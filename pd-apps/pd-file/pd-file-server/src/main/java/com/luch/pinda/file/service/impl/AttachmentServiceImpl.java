package com.luch.pinda.file.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.pinda.base.id.IdGenerate;
import com.itheima.pinda.database.mybatis.conditions.Wraps;
import com.itheima.pinda.database.mybatis.conditions.query.LbqWrapper;
import com.itheima.pinda.dozer.DozerUtils;
import com.itheima.pinda.exception.BizException;
import com.luch.pinda.file.biz.FileBiz;
import com.luch.pinda.file.dao.AttachmentMapper;
import com.luch.pinda.file.domain.FileDO;
import com.luch.pinda.file.domain.FileDeleteDO;
import com.luch.pinda.file.dto.AttachmentDTO;
import com.luch.pinda.file.dto.AttachmentResultDTO;
import com.luch.pinda.file.dto.FilePageReqDTO;
import com.luch.pinda.file.entity.Attachment;
import com.luch.pinda.file.entity.File;
import com.luch.pinda.file.properties.FileServerProperties;
import com.luch.pinda.file.service.AttachmentService;
import com.luch.pinda.file.strategy.FileStrategy;
import com.itheima.pinda.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author luch
 * @date 2021/7/20 21:06
 */
@Slf4j
@Service
public class AttachmentServiceImpl extends ServiceImpl<AttachmentMapper, Attachment>  implements AttachmentService {
    @Autowired
    private IdGenerate<Long> idGenerate;
    @Autowired
    private DozerUtils dozer;
    @Autowired
    private FileStrategy fileStrategy;
    @Autowired
    private FileServerProperties fileProperties;

    @Override
    public AttachmentDTO upload(MultipartFile multipartFile, Long id, String bizType, String bizId, Boolean isSingle) {
        //?????????????????????????????????????????????id
        if (StringUtils.isNotEmpty(bizType) && StringUtils.isEmpty(bizId)) {
            bizId = String.valueOf(idGenerate.generate());
        }

        //????????????
        File file = fileStrategy.upload(multipartFile);


        //????????????
        Attachment attachment = dozer.map(file, Attachment.class);

        attachment.setBizId(bizId);
        attachment.setBizType(bizType);
        setDate(attachment);

        if (isSingle) {
            super.remove(Wraps.<Attachment>lbQ().eq(Attachment::getBizId, bizId).eq(Attachment::getBizType, bizType));
        }

        //?????????????????????????????????
        if (id != null && id > 0) {
            //????????????????????????id????????????????????????
            attachment.setId(id);
            super.updateById(attachment);
        } else {
            attachment.setId(idGenerate.generate());
            super.save(attachment);
        }

        AttachmentDTO dto = dozer.map(attachment, AttachmentDTO.class);
        return dto;
    }

    private void setDate(Attachment file) {
        LocalDateTime now = LocalDateTime.now();
        file.setCreateMonth(DateUtils.formatAsYearMonthEn(now));
        file.setCreateWeek(DateUtils.formatAsYearWeekEn(now));
        file.setCreateDay(DateUtils.formatAsDateEn(now));

    }

    /**
     *??????id????????????
     * @param ids
     */
    @Override
    public void remove(Long[] ids) {
        if (ArrayUtils.isEmpty(ids)) {
            return;
        }
        //???????????????
        List<Attachment> list = super.list(Wrappers.<Attachment>lambdaQuery().
                in(Attachment::getId, ids));
        if (list.isEmpty()) {
            return;
        }
        //???????????????????????????
        super.removeByIds(Arrays.asList(ids));

        //??????????????????
        List<FileDeleteDO> fileDeleteDOList =
                list.stream().map((fi) -> FileDeleteDO.builder()
                        .relativePath(fi.getRelativePath()) //?????????????????????????????????
                        .fileName(fi.getFilename()) //???????????????
                        .group(fi.getGroup()) //fastDFS???????????? ??????FastDFS
                        .path(fi.getPath()) //fastdfs ?????????
                        .build())
                        .collect(Collectors.toList());
        //????????????
        fileStrategy.delete(fileDeleteDOList);
    }

    /**
     * ????????????id???????????????????????????
     *
     * @param bizId
     * @param bizType
     */
    @Override
    public void removeByBizIdAndBizType(String bizId, String bizType) {
        //????????????????????????id???????????????
        List<Attachment> list = super.list(
                Wraps.<Attachment>lbQ()
                        .eq(Attachment::getBizId, bizId)
                        .eq(Attachment::getBizType, bizType));
        if (list.isEmpty()) {
            return;
        }

        //??????id????????????
        remove(list.stream().mapToLong(
                Attachment::getId).boxed().toArray(Long[]::new));
    }

    @Autowired
    private FileBiz fileBiz;

    /**
     * ????????????id????????????
     * @param request
     * @param response
     * @param ids
     * @throws Exception
     */
    @Override
    public void download(Long[] ids) throws Exception {
        //????????????id???????????????
        List<Attachment> list =
                (List<Attachment>) super.listByIds(Arrays.asList(ids));
        if (list.isEmpty()) {
            throw BizException.wrap("???????????????????????????");
        }
        down(list);
    }

    /**
     * ????????????
     * @param request
     * @param response
     * @param list
     * @throws Exception
     */
    private void down(List<Attachment> list) throws Exception {
        List<FileDO> fileDOList =
                list.stream().map((file) ->FileDO.builder()
                        .url(file.getUrl())
                        .submittedFileName(file.getSubmittedFileName())
                        .size(file.getSize())
                        .dataType(file.getDataType())
                        .build())
                        .collect(Collectors.toList());
        fileBiz.down(fileDOList);
    }

    /**
     * ????????????id???????????????????????????
     *
     * @param request
     * @param response
     * @param bizTypes
     * @param bizIds
     * @throws Exception
     */
    @Override
    public void downloadByBiz(String[] bizTypes, String[] bizIds) throws Exception {
        List<Attachment> list = super.list(
                Wraps.<Attachment>lbQ()
                        .in(Attachment::getBizType, bizTypes)
                        .in(Attachment::getBizId, bizIds));

        down(list);
    }

    /**
     * ????????????????????????
     *
     * @param page
     * @param data
     * @return
     */
    public IPage<Attachment> page(Page<Attachment> page, FilePageReqDTO data) {
        Attachment attachment = dozer.map(data, Attachment.class);

        // ${ew.customSqlSegment} ?????????????????????eq like ??? ?????????lbQ!
        LbqWrapper<Attachment> wrapper = Wraps.<Attachment>lbQ()
                .like(Attachment::getSubmittedFileName, attachment.getSubmittedFileName())
                .like(Attachment::getBizType, attachment.getBizType())
                .like(Attachment::getBizId, attachment.getBizId())
                .eq(Attachment::getDataType, attachment.getDataType())
                .orderByDesc(Attachment::getId);
        return baseMapper.page(page, wrapper);
    }

    /**
     * ???????????????????????????id????????????
     *
     * @param bizTypes
     * @param bizIds
     * @return
     */
    public List<AttachmentResultDTO> find(String[] bizTypes, String[] bizIds) {
        return baseMapper.find(bizTypes, bizIds);
    }
}
