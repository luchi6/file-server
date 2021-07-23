package com.luch.pinda.file.storage;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.*;
import com.itheima.pinda.base.R;
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
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.itheima.pinda.utils.DateUtils.DEFAULT_MONTH_FORMAT_SLASH;

/**
 * @author luch
 * @date 2021/7/20 0:12
 */

@Configuration
@Slf4j
@EnableConfigurationProperties(FileServerProperties.class)
@ConditionalOnProperty(name = "pinda.file.type",havingValue = "ALI")
public class AliOssAutoConfigure {

    @Service
    public class AliServiceImpl extends AbstractFileStrategy {

        private OSS buildClient() {
            properties = fileProperties.getAli();
            return new OSSClientBuilder()
                    .build(properties.getEndpoint(),
                            properties.getAccessKeyId(),
                            properties.getAccessKeySecret());
        }

        @Override
        protected String getUriPrefix() {
            if (StringUtils.isNotEmpty(properties.getUriPrefix())) {
                return properties.getUriPrefix();
            } else {
                String prefix = properties.
                        getEndpoint().
                        contains("https://") ? "https://" : "http://";
                return prefix + properties.getBucketName() + "." +
                        properties.getEndpoint().replaceFirst(prefix, "");
            }
        }

        @Override
        public void uploadFile(File file, MultipartFile multipartFile) throws Exception {
            OSS client = buildClient();

            //存储空间
            String bucketName = properties.getBucketName();
            // 不存在则创建
            if (!client.doesBucketExist(bucketName)) {
                client.createBucket(bucketName);
            }
            //生成文件名
            String fileName = UUID.randomUUID().toString() +
                    StrPool.DOT +
                    file.getExt();

            //日期文件夹，例如：2020\04
            String relativePath = LocalDate.now()
                    .format(DateTimeFormatter.ofPattern(DEFAULT_MONTH_FORMAT_SLASH));

            // web服务器存放的相对路径
            String relativeFileName = relativePath + StrPool.SLASH + fileName;
            relativeFileName = StrUtil.replace(relativeFileName, "\\\\",
                    StrPool.SLASH);
            relativeFileName = StrUtil.replace(relativeFileName, "\\",
                    StrPool.SLASH);

            //对象元数据
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentDisposition("attachment;fileName=" +
                    file.getSubmittedFileName());
            metadata.setContentType(file.getContextType());

            //上传请求对象
            PutObjectRequest request =
                    new PutObjectRequest(bucketName, relativeFileName,
                            multipartFile.getInputStream(),
                            metadata);
            //上传文件到阿里云OSS空间
            PutObjectResult result = client.putObject(request);

            log.info("result={}", JSONObject.toJSONString(result));

            String url = getUriPrefix() + StrPool.SLASH + relativeFileName;
            url = StrUtil.replace(url, "\\\\", StrPool.SLASH);
            url = StrUtil.replace(url, "\\", StrPool.SLASH);
            // 写入文件表
            file.setUrl(url);
            file.setFilename(fileName);
            file.setRelativePath(relativePath);

            file.setGroup(result.getETag());
            file.setPath(result.getRequestId());

            //关闭阿里云OSS客户端
            client.shutdown();
        }

        @Override
        protected void delete(FileDeleteDO file) {
            OSS client = buildClient();
            //获得OSS空间名称
            String bucketName = properties.getBucketName();
            // 删除文件
            client.deleteObject(bucketName, file.getRelativePath() +
                    StrPool.SLASH + file.getFileName());
            //关闭阿里云OSS客户端
            client.shutdown();
        }
    }

    /**
     * 阿里云OSS分片文件处理策略类
     */
    @Service
    public class AliChunkServiceImpl extends AbstractFileChunkStrategy {
        private OSS buildClient() {
            properties = fileProperties.getAli();
            return new OSSClientBuilder().build(properties.getEndpoint(), properties.getAccessKeyId(),
                    properties.getAccessKeySecret());
        }

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
            OSS client = buildClient();
            String bucketName = properties.getBucketName();

            //日期文件夹
            String relativePath = LocalDate.now().format(DateTimeFormatter.ofPattern(DEFAULT_MONTH_FORMAT_SLASH));
            // web服务器存放的相对路径
            String relativeFileName = relativePath + StrPool.SLASH + fileName;

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentDisposition("attachment;fileName=" + info.getSubmittedFileName());
            metadata.setContentType(info.getContextType());
            //步骤1：初始化一个分片上传事件。
            InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(bucketName, relativeFileName, metadata);
            InitiateMultipartUploadResult result = client.initiateMultipartUpload(request);
            // 返回uploadId，它是分片上传事件的唯一标识，您可以根据这个ID来发起相关的操作，如取消分片上传、查询分片上传等。
            String uploadId = result.getUploadId();

            // partETags是PartETag的集合。PartETag由分片的ETag和分片号组成。
            List<PartETag> partETags = new ArrayList<PartETag>();
            for (int i = 0; i < files.size(); i++) {
                java.io.File file = files.get(i);
                FileInputStream in = FileUtils.openInputStream(file);

                UploadPartRequest uploadPartRequest = new UploadPartRequest();
                uploadPartRequest.setBucketName(bucketName);
                uploadPartRequest.setKey(relativeFileName);
                uploadPartRequest.setUploadId(uploadId);
                uploadPartRequest.setInputStream(in);
                // 设置分片大小。除了最后一个分片没有大小限制，其他的分片最小为100KB。
                uploadPartRequest.setPartSize(file.length());
                // 设置分片号。每一个上传的分片都有一个分片号，取值范围是1~10000，如果超出这个范围，OSS将返回InvalidArgument的错误码。
                uploadPartRequest.setPartNumber(i + 1);

                // 每个分片不需要按顺序上传，甚至可以在不同客户端上传，OSS会按照分片号排序组成完整的文件。
                UploadPartResult uploadPartResult = client.uploadPart(uploadPartRequest);

                // 每次上传分片之后，OSS的返回结果会包含一个PartETag。PartETag将被保存到partETags中。
                partETags.add(uploadPartResult.getPartETag());
            }

            /* 步骤3：完成分片上传。 */
            // 排序。partETags必须按分片号升序排列。
            partETags.sort(Comparator.comparingInt(PartETag::getPartNumber));

            // 在执行该操作时，需要提供所有有效的partETags。OSS收到提交的partETags后，会逐一验证每个分片的有效性。当所有的数据分片验证通过后，OSS将把这些分片组合成一个完整的文件。
            CompleteMultipartUploadRequest completeMultipartUploadRequest =
                    new CompleteMultipartUploadRequest(bucketName, relativeFileName, uploadId, partETags);

            CompleteMultipartUploadResult uploadResult = client.completeMultipartUpload(completeMultipartUploadRequest);

            String url = new StringBuilder(properties.getUriPrefix())
                    .append(relativePath)
                    .append(StrPool.SLASH)
                    .append(fileName)
                    .toString();
            File filePo = File.builder()
                    .relativePath(relativePath)
                    .group(uploadResult.getETag())
                    .path(uploadResult.getRequestId())
                    .url(StringUtils.replace(url, "\\", StrPool.SLASH))
                    .build();

            // 关闭OSSClient。
            client.shutdown();
            return R.success(filePo);
        }
    }
}
