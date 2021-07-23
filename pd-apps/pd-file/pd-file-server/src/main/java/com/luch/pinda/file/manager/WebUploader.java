package com.luch.pinda.file.manager;

import com.luch.pinda.file.dto.chunk.FileUploadDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.IOException;
/**
 * 分片上传工具类
 */
@Service
@Slf4j
public class WebUploader {
    /**
     * 为上传的文件创建对应的保存位置,若上传的是分片，则会创建对应的文件夹结构和tmp文件
     *
     * @param fileUploadDTO 上传文件的相关信息
     * @param path 文件保存根路径
     * @return
     */
    public java.io.File getReadySpace(FileUploadDTO fileUploadDTO, String path) {
        //创建上传文件所需的文件夹
        if (!this.createFileFolder(path, false)) {
            return null;
        }

        //将上传的分片保存在此目录中
        String fileFolder = fileUploadDTO.getName();

        if (fileFolder == null) {
            return null;
        }

        //文件上传路径更新为指定文件信息签名后的临时文件夹，用于后期合并
        path += "/" + fileFolder;

        if (!this.createFileFolder(path, true)) {
            return null;
        }

        //分片上传，指定当前分片文件的文件名
        String newFileName = String.valueOf(fileUploadDTO.getChunk());
        return new java.io.File(path, newFileName);
    }

    /**
     * 创建存放分片上传的文件的文件夹
     *
     * @param file   文件夹路径
     * @param hasTmp 是否有临时文件
     * @return
     */
    private boolean createFileFolder(String file, boolean hasTmp) {
        //创建存放分片文件的临时文件夹
        java.io.File tmpFile = new java.io.File(file);
        if (!tmpFile.exists()) {
            try {
                tmpFile.mkdirs();
            } catch (SecurityException ex) {
                log.error("无法创建文件夹", ex);
                return false;
            }
        }

        if (hasTmp) {
            //创建临时文件，用来记录上传分片文件的修改时间，用于清理长期未完成的垃圾分片
            tmpFile = new java.io.File(file + ".tmp");
            if (tmpFile.exists()) {
                return tmpFile.setLastModified(System.currentTimeMillis());
            } else {
                try {
                    tmpFile.createNewFile();
                } catch (IOException ex) {
                    log.error("无法创建tmp文件", ex);
                    return false;
                }
            }
        }
        return true;
    }
}
