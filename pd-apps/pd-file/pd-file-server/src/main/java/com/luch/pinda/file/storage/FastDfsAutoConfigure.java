package com.luch.pinda.file.storage;

import com.github.tobato.fastdfs.domain.fdfs.StorePath;
import com.github.tobato.fastdfs.service.AppendFileStorageClient;
import com.github.tobato.fastdfs.service.FastFileStorageClient;
import com.itheima.pinda.base.R;
import com.luch.pinda.file.domain.FileDeleteDO;
import com.luch.pinda.file.dto.chunk.FileChunksMergeDTO;
import com.luch.pinda.file.entity.File;
import com.luch.pinda.file.properties.FileServerProperties;
import com.luch.pinda.file.strategy.impl.AbstractFileChunkStrategy;
import com.luch.pinda.file.strategy.impl.AbstractFileStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * @author luch
 * @date 2021/7/14 21:42
 */

@Configuration
@Slf4j
@EnableConfigurationProperties(FileServerProperties.class)
@ConditionalOnProperty(name = "pinda.file.type",havingValue = "FAST_DFS")
public class FastDfsAutoConfigure {

    @Service
    public class FastDfsServiceImpl extends AbstractFileStrategy {

        @Autowired
        private FastFileStorageClient storageClient;

        @Override
        public void uploadFile(File file, MultipartFile multipartFile) throws Exception{
            StorePath storePath = storageClient.uploadFile(multipartFile.getInputStream(),
                    multipartFile.getSize(),
                    file.getExt(), null);
            file.setUrl(fileProperties.getUriPrefix() + storePath.getFullPath());
            file.setGroup(storePath.getGroup());
            file.setPath(storePath.getPath());
        }

        @Override
        protected void delete(FileDeleteDO fileDeleteDO) {
            storageClient.deleteFile(fileDeleteDO.getGroup(), fileDeleteDO.getFileName());

        }
    }

    /**
     * FastDfs分片文件处理策略类
     */
    @Service
    public class FastDfsChunkServiceImpl extends AbstractFileChunkStrategy {
        @Autowired
        protected AppendFileStorageClient storageClient;

        /**
         * 分片合并
         * @param files    分片文件
         * @param fileName 唯一名 含后缀
         * @param info     分片信息
         * @return
         * @throws IOException
         */
        @Override
        protected R<File> merge(List<java.io.File> files, String fileName, FileChunksMergeDTO info) throws IOException {
            StorePath storePath = null;

            for (int i = 0; i < files.size(); i++) {
                java.io.File file = files.get(i);

                FileInputStream in = FileUtils.openInputStream(file);
                if (i == 0) {
                    storePath = storageClient.uploadAppenderFile(null, in,
                            file.length(), info.getExt());
                } else {
                    storageClient.appendFile(storePath.getGroup(), storePath.getPath(),
                            in, file.length());
                }
            }
            if (storePath == null) {
                return R.fail("上传失败");
            }

            String url = new StringBuilder(fileProperties.getUriPrefix())
                    .append(storePath.getFullPath())
                    .toString();
            File filePo = File.builder()
                    .url(url)
                    .group(storePath.getGroup())
                    .path(storePath.getPath())
                    .build();
            return R.success(filePo);
        }
    }
}
