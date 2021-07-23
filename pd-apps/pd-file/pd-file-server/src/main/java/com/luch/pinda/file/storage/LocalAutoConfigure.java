package com.luch.pinda.file.storage;

import cn.hutool.core.util.StrUtil;
import com.itheima.pinda.base.R;
import com.itheima.pinda.utils.DateUtils;
import com.itheima.pinda.utils.StrPool;
import com.luch.pinda.file.domain.FileDeleteDO;
import com.luch.pinda.file.dto.chunk.FileChunksMergeDTO;
import com.luch.pinda.file.entity.File;
import com.luch.pinda.file.properties.FileServerProperties;
import com.luch.pinda.file.strategy.impl.AbstractFileChunkStrategy;
import com.luch.pinda.file.strategy.impl.AbstractFileStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * @author luch
 * @date 2021/7/14 21:42
 */

@Configuration
@Slf4j
@EnableConfigurationProperties(FileServerProperties.class)
@ConditionalOnProperty(name = "pinda.file.type",havingValue = "LOCAL")
public class LocalAutoConfigure {

    @Service
    public class LocalServiceImpl extends AbstractFileStrategy {
        public void buildClient() {
            properties = fileProperties.getLocal();
        }

        @Override
        public void uploadFile(File file, MultipartFile multipartFile) throws Exception{
            buildClient();
            String endpoint = properties.getEndpoint();
            String bucketName = properties.getBucketName();
            String uriPrefix = properties.getUriPrefix();

            String fileName = UUID.randomUUID().toString() + StrPool.DOT + file.getExt();

            //日期目录（相对路径）
            String relativePath = Paths.get(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"))).toString();

            //文件上传的绝对路径目录
            String absolutePath = Paths.get(endpoint, bucketName, relativePath).toString();

            //目标输出文件
            java.io.File outFile = new java.io.File(Paths.get(absolutePath, fileName).toString());

            //向目标文件写入数据
            FileUtils.writeByteArrayToFile(outFile,multipartFile.getBytes());

            String url = new StringBuilder(getUriPrefix())
                    .append(StrPool.SLASH)
                    .append(properties.getBucketName())
                    .append(StrPool.SLASH)
                    .append(relativePath)
                    .append(StrPool.SLASH)
                    .append(fileName)
                    .toString();
            //替换掉windows环境的\路径
            url = StrUtil.replace(url, "\\\\", StrPool.SLASH);
            url = StrUtil.replace(url, "\\", StrPool.SLASH);


            file.setUrl(url);
            file.setFilename(fileName);
            file.setRelativePath(relativePath);
        }

        @Override
        protected void delete(FileDeleteDO fileDeleteDO) {
            String filePath = Paths.get(properties.getEndpoint(),
                    properties.getBucketName(),
                    fileDeleteDO.getRelativePath(),
                    fileDeleteDO.getFileName())
                    .toString();

            java.io.File file = new java.io.File(filePath);
            FileUtils.deleteQuietly(file);

        }
    }

    /**
     * 本地分片文件处理策略类
     */
    @Service
    public class LocalChunkServiceImpl extends AbstractFileChunkStrategy {
        /**
         *分片合并
         * @param files    分片文件
         * @param fileName 唯一名 含后缀
         * @param info     分片信息
         * @return
         * @throws IOException
         */
        @Override
        protected R<File> merge(List<java.io.File> files, String fileName, FileChunksMergeDTO info) throws IOException {
            properties = fileProperties.getLocal();

            //日期目录
            String relativePath = Paths.get(LocalDate.now().format(DateTimeFormatter.ofPattern(DateUtils.DEFAULT_MONTH_FORMAT_SLASH))).toString();

            //合并后文件的存储路径 例如：D:\\uploadFiles\\oss-file-service\\2020\\05
            String path = Paths.get(properties.getEndpoint(), properties.getBucketName(), relativePath).toString();

            //上传文件存放目录，如果不存在则创建
            java.io.File uploadFolder = new java.io.File(path);
            if(!uploadFolder.exists()){
                uploadFolder.mkdirs();
            }

            //创建合并后的文件
            java.io.File outputFile = new java.io.File(Paths.get(path, fileName).toString());
            if (!outputFile.exists()) {
                boolean newFile = outputFile.createNewFile();
                if (!newFile) {
                    return R.fail("创建文件失败");
                }
                try (FileChannel outChannel = new FileOutputStream(outputFile).getChannel()) {
                    //同步nio 方式对分片进行合并, 有效的避免文件过大导致内存溢出
                    for (java.io.File file : files) {
                        try (FileChannel inChannel = new FileInputStream(file).getChannel()) {
                            inChannel.transferTo(0, inChannel.size(), outChannel);
                        } catch (FileNotFoundException ex) {
                            log.error("文件转换失败", ex);
                            return R.fail("文件转换失败");
                        }
                        //删除分片
                        if (!file.delete()) {
                            log.error("分片[" + info.getName() + "=>" + file.getName() + "]删除失败");
                        }
                    }
                } catch (FileNotFoundException e) {
                    log.error("文件输出失败", e);
                    return R.fail("文件输出失败");
                }

            } else {
                log.warn("文件[{}], fileName={}已经存在", info.getName(), fileName);
            }

            String url = new StringBuilder(properties.getUriPrefix()).
                    append(properties.getBucketName()).append(StrPool.SLASH).
                    append(relativePath).append(StrPool.SLASH).
                    append(fileName).toString();
            File filePo = File.builder()
                    .relativePath(relativePath)
                    .url(StringUtils.replace(url, "\\", StrPool.SLASH))
                    .build();
            return R.success(filePo);
        }
    }
}
