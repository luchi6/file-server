package com.luch.pinda.file.domain;


import com.luch.pinda.file.entity.File;
import lombok.Data;

/**
 * 文件查询 DO
 *
 */
@Data
public class FileQueryDO extends File {
    private File parent;

}
