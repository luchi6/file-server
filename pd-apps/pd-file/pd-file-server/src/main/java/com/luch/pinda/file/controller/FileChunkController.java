package com.luch.pinda.file.controller;

import com.itheima.pinda.base.BaseController;
import com.itheima.pinda.base.R;
import com.itheima.pinda.dozer.DozerUtils;
import com.luch.pinda.file.dto.chunk.FileChunksMergeDTO;
import com.luch.pinda.file.dto.chunk.FileUploadDTO;
import com.luch.pinda.file.entity.File;
import com.luch.pinda.file.manager.WebUploader;
import com.luch.pinda.file.properties.FileServerProperties;
import com.luch.pinda.file.service.FileService;
import com.luch.pinda.file.strategy.FileChunkStrategy;
import com.luch.pinda.file.strategy.FileStrategy;
import com.luch.pinda.file.utils.FileDataTypeUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
/**
 * 分片上传
 */
@RestController
@Slf4j
@RequestMapping("/chunk")
@CrossOrigin
@Api(value = "分片上传", tags = "分片上传，需要webuploder.js插件进行配合使用")
public class FileChunkController extends BaseController {
    @Autowired
    private FileServerProperties fileProperties;
    @Autowired
    private FileService fileService;
    @Autowired
    private FileStrategy fileStrategy;
    @Autowired
    private WebUploader webUploader;
    @Autowired
    private DozerUtils dozerUtils;
    /**
     * 分片上传
     * @param fileUploadDTO
     * @param multipartFile
     * @return
     */
    @ApiOperation(value = "分片上传", notes = "分片上传")
    @PostMapping(value = "/upload")
    public R<FileChunksMergeDTO> uploadFile(FileUploadDTO fileUploadDTO,
                                            @RequestParam(value = "file", required = false) MultipartFile multipartFile) throws Exception {

        if (multipartFile == null || multipartFile.isEmpty()) {
            log.error("分片上传分片为空");
            return fail("分片上传分片为空");
        }


        if (fileUploadDTO.getChunks() == null || fileUploadDTO.getChunks() <= 0) {
            //没有分片，按照普通文件上传处理
            File file = fileStrategy.upload(multipartFile);
            file.setFileMd5(fileUploadDTO.getMd5());

            fileService.save(file);

            return success(null);
        } else {
            //  存放分片文件的服务器绝对路径 ，例如 D:\\uploadfiles\\2020\\04
            String uploadFolder = FileDataTypeUtil.getUploadPathPrefix(fileProperties.getStoragePath());

            //为上传的文件准备好对应的位置
            java.io.File targetFile = webUploader.getReadySpace(fileUploadDTO, uploadFolder);

            if (targetFile == null) {
                return fail("分片上传失败");
            }
            //保存上传文件
            multipartFile.transferTo(targetFile);

            //封装信息给前端，用于分片合并
            FileChunksMergeDTO mergeDTO = new FileChunksMergeDTO();
            mergeDTO.setSubmittedFileName(multipartFile.getOriginalFilename());
            dozerUtils.map(fileUploadDTO,mergeDTO);

            return success(mergeDTO);
        }
    }

    @Autowired
    private FileChunkStrategy fileChunkStrategy;//分片文件处理策略

    /**
     * 分片合并
     * @param info
     * @return
     */
    @ApiOperation(value = "分片合并", notes = "所有分片上传成功后，调用该接口对分片进行合并")
    @PostMapping(value = "/merge")
    public R<File> saveChunksMerge(FileChunksMergeDTO info) {
        log.info("info={}", info);

        return fileChunkStrategy.chunksMerge(info);
    }
}
