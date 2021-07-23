package com.luch.pinda.file.strategy.impl;

import com.itheima.pinda.exception.BizException;
import com.itheima.pinda.exception.code.ExceptionCode;
import com.luch.pinda.file.domain.FileDeleteDO;
import com.luch.pinda.file.entity.File;
import com.luch.pinda.file.enumeration.IconType;
import com.luch.pinda.file.properties.FileServerProperties;
import com.luch.pinda.file.strategy.FileStrategy;
import com.luch.pinda.file.utils.FileDataTypeUtil;
import com.itheima.pinda.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author luch
 * @date 2021/7/13 22:06
 */

@Slf4j
public abstract class AbstractFileStrategy implements FileStrategy {
    private static final String FILE_SPLIT = ".";
    @Autowired
    protected FileServerProperties fileProperties;

    protected FileServerProperties.Properties properties;

    /**
     * 获取下载地址前缀
     */
    protected String getUriPrefix() {
        if (StringUtils.isNotEmpty(properties.getUriPrefix())) {
            return properties.getUriPrefix();
        } else {
            return properties.getEndpoint();
        }
    }
    /**
     * 文件上传
     * @param multipartFile
     * @return
     */
    @Override
    public File upload(MultipartFile multipartFile) {
        try {
            String originalFilename = multipartFile.getOriginalFilename();
            if (!originalFilename.contains(FILE_SPLIT)) {
                throw BizException.wrap(ExceptionCode.BASE_VALID_PARAM.build("上传的文件名称缺少后缀"));
            }

            String contentType = multipartFile.getContentType();
            File file = File.builder()
                    .isDelete(false)
                    .size(multipartFile.getSize())
                    .contextType(contentType)
                    .dataType(FileDataTypeUtil.getDataType(contentType))
                    .submittedFileName(originalFilename)
                    .ext(FilenameUtils.getExtension(originalFilename))
                    .build();

            //设置图标
            file.setIcon(IconType.getIcon(file.getExt()).getIcon());

            //设置文件创建时间
            LocalDateTime now = LocalDateTime.now();
            file.setCreateMonth(DateUtils.formatAsYearMonthEn(now));
            file.setCreateWeek(DateUtils.formatAsYearWeekEn(now));
            file.setCreateDay(DateUtils.formatAsDateEn(now));

            uploadFile(file, multipartFile);

            return file;
        } catch (Exception e) {
            log.error("e = {}",e);
            throw BizException.wrap(ExceptionCode.BASE_VALID_PARAM.build("文件上传失败"));
        }
    }

    public abstract void uploadFile(File file, MultipartFile multipartFile) throws Exception;

    /**
     * 文件删除
     * @param list
     * @return
     */
    @Override
    public boolean delete(List<FileDeleteDO> list) {
        if (list == null || list.isEmpty()) {
            return true;
        }

        boolean flag = false;
        for (FileDeleteDO fileDeleteDO : list) {
            try {
                delete(fileDeleteDO);
                flag = true;
            } catch (Exception e) {
                log.error("e = {}",e);
            }
        }
        return flag;
    }

    /**
     * 文件删除抽象方法，由子类实现
     * @param fileDeleteDO
     */
    protected abstract void delete(FileDeleteDO fileDeleteDO);
}
