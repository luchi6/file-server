package com.luch.pinda.file.strategy;

import com.luch.pinda.file.domain.FileDeleteDO;
import com.luch.pinda.file.entity.File;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author luch
 * @date 2021/7/13 20:27
 */


public interface FileStrategy {

    /**
     * 文件上传
     * @param multipartFile
     * @return
     */
    File upload(MultipartFile multipartFile);

    /**
     * 文件删除
     * @param list
     * @return
     */
    boolean delete(List<FileDeleteDO> list);



}
