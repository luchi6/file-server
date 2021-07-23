package com.luch.pinda.file.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.luch.pinda.file.dto.AttachmentDTO;
import com.luch.pinda.file.dto.AttachmentResultDTO;
import com.luch.pinda.file.dto.FilePageReqDTO;
import com.luch.pinda.file.entity.Attachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 附件业务接口
 * @author luch
 * @date 2021/7/20 21:05
 */


public interface AttachmentService extends IService<Attachment> {
    /**
     * 上传附件
     *
     * @param file 文件
     * @param id 附件id
     * @param bizType 业务类型
     * @param bizId 业务id
     * @param isSingle 是否单个文件
     * @return
     */
    AttachmentDTO upload(MultipartFile file, Long id, String bizType, String bizId, Boolean isSingle);

    /**
     * 删除附件
     *
     * @param ids
     */
    void remove(Long[] ids);

    /**
     * 根据业务id/业务类型删除附件
     *
     * @param bizId
     * @param bizType
     */
    void removeByBizIdAndBizType(String bizId, String bizType);

    /**
     * 根据文件id下载附件
     *
     * @param ids
     * @throws Exception
     */
    void download(Long[] ids) throws Exception;

    /**
     * 根据业务id和业务类型下载附件
     *
     * @param bizTypes
     * @param bizIds
     * @throws Exception
     */
    void downloadByBiz(String[] bizTypes, String[] bizIds) throws Exception;

    /**
     * 查询附件分页数据
     *
     * @param page
     * @param data
     * @return
     */
    IPage<Attachment> page(Page<Attachment> page, FilePageReqDTO data);

    /**
     * 根据业务类型和业务id查询附件
     *
     * @param bizTypes
     * @param bizIds
     * @return
     */
    List<AttachmentResultDTO> find(String[] bizTypes, String[] bizIds);
}
