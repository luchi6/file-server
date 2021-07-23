package com.luch.pinda.file.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.pinda.base.BaseController;
import com.itheima.pinda.base.R;
import com.luch.pinda.file.dto.AttachmentDTO;
import com.luch.pinda.file.dto.AttachmentRemoveDTO;
import com.luch.pinda.file.dto.AttachmentResultDTO;
import com.luch.pinda.file.dto.FilePageReqDTO;
import com.luch.pinda.file.entity.Attachment;
import com.luch.pinda.file.service.AttachmentService;
import com.itheima.pinda.utils.BizAssert;
import io.swagger.annotations.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.itheima.pinda.exception.code.ExceptionCode.BASE_VALID_PARAM;

/**
 * 文件服务-文件处理控制器
 *
 * @author luch
 * @date 2021/7/20 20:58
 */

@RestController
@Slf4j
@RequestMapping("/attachment")
@Api(value = "附件", tags = "附件")
public class AttachmentController extends BaseController {
    @Autowired
    private AttachmentService attachmentService;

    /**
     * 上传附件
     */
    @ApiOperation(value = "附件上传", notes = "附件上传")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "isSingle", value = "是否单文件", dataType = "boolean", paramType = "query"),
            @ApiImplicitParam(name = "id", value = "文件id", dataType = "long", paramType = "query"),
            @ApiImplicitParam(name = "bizId", value = "业务id", dataType = "long", paramType = "query"),
            @ApiImplicitParam(name = "bizType", value = "业务类型", dataType = "long", paramType = "query"),
            @ApiImplicitParam(name = "file", value = "附件", dataType = "MultipartFile", allowMultiple = true, required = true),
    })
    @PostMapping(value = "/upload")
    public R<AttachmentDTO> upload(
            @RequestParam(value = "file") MultipartFile file,
            @RequestParam(value = "isSingle", required = false, defaultValue = "false") Boolean isSingle,
            @RequestParam(value = "id", required = false) Long id,
            @RequestParam(value = "bizId", required = false) String bizId,
            @RequestParam(value = "bizType") String bizType) {
        // 忽略路径字段,只处理文件类型
        if (file.isEmpty()) {
            return fail(BASE_VALID_PARAM.build("请求中必须至少包含一个有效文件"));
        }
        AttachmentDTO attachment = attachmentService.upload(file, id, bizType, bizId, isSingle);
        return success(attachment);
    }

    /**
     * 根据id删除文件
     * @param ids
     * @return
     */
    @ApiOperation(value = "删除文件", notes = "删除文件")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "ids[]", value = "文件ids", dataType = "array", paramType = "query"),
    })
    @DeleteMapping
    public R<Boolean> remove(@RequestParam(value = "ids[]") Long[] ids) {
        attachmentService.remove(ids);
        return success(true);
    }

    /**
     * 根据业务类型或业务id删除文件
     * @param dto
     * @return
     */
    @ApiOperation(value = "根据业务类型或业务id删除文件", notes = "根据业务类型或业务id删除文件")
    @DeleteMapping(value = "/biz")
    public R<Boolean> removeByBizIdAndBizType(@RequestBody AttachmentRemoveDTO dto) {
        attachmentService.removeByBizIdAndBizType(dto.getBizId(), dto.getBizType());
        return success(true);
    }

    /**
     * 下载一个文件或多个文件打包下载
     *
     * @param ids      文件id
     * @throws Exception
     */
    @ApiOperation(value = "根据文件id打包下载", notes = "根据附件id下载多个打包的附件")
    @GetMapping(value = "/download", produces = "application/octet-stream")
    public void download(
            @ApiParam(name = "ids[]", value = "文件id 数组")
            @RequestParam(value = "ids[]") Long[] ids) throws Exception {
        BizAssert.isTrue(ArrayUtils.isNotEmpty(ids),
                BASE_VALID_PARAM.build("附件id不能为空"));
        //根据文件id下载文件
        attachmentService.download(ids);
    }

    /**
     * 根据业务类型或者业务id其中之一，或者2个同时打包下载文件
     *
     * @param bizIds   业务id
     * @param bizTypes 业务类型
     *
     */
    @ApiImplicitParams({
            @ApiImplicitParam(name = "bizIds[]", value = "业务id数组", dataType = "array", paramType = "query"),
            @ApiImplicitParam(name = "bizTypes[]", value = "业务类型数组", dataType = "array", paramType = "query"),
    })
    @ApiOperation(value = "根据业务类型/业务id打包下载", notes = "根据业务id下载一个文件或多个文件打包下载")
    @GetMapping(value = "/download/biz", produces = "application/octet-stream")
    public void downloadByBiz(
            @RequestParam(value = "bizIds[]", required = false) String[] bizIds,
            @RequestParam(value = "bizTypes[]", required = false) String[] bizTypes) throws Exception {
        BizAssert.isTrue(!(ArrayUtils.isEmpty(bizTypes) && ArrayUtils.isEmpty(bizIds)), BASE_VALID_PARAM.build("附件业务id和业务类型不能同时为空"));
        attachmentService.downloadByBiz(bizTypes, bizIds);
    }

    /**
     * 分页查询附件
     *
     */
    @ApiOperation(value = "分页查询附件", notes = "分页查询附件")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "current", value = "当前页", dataType = "long", paramType = "query", defaultValue = "1"),
            @ApiImplicitParam(name = "size", value = "每页显示几条", dataType = "long", paramType = "query", defaultValue = "10"),
    })
    @GetMapping(value = "/page")
    public R<IPage<Attachment>> page(FilePageReqDTO data) {
        Page<Attachment> page = getPage();
        attachmentService.page(page, data);
        return success(page);
    }

    /**
     * 根据业务类型/业务id查询附件
     * @param bizTypes
     * @param bizIds
     * @return
     */
    @ApiOperation(value = "查询附件", notes = "查询附件")
    @ApiResponses(
            @ApiResponse(code = 60103, message = "文件id为空")
    )
    @GetMapping
    public R<List<AttachmentResultDTO>> findAttachment(@RequestParam(value = "bizTypes", required = false) String[] bizTypes,
                                                       @RequestParam(value = "bizIds", required = false) String[] bizIds) {
        //不能同时为空
        BizAssert.isTrue(!(ArrayUtils.isEmpty(bizTypes) && ArrayUtils.isEmpty(bizIds)), BASE_VALID_PARAM.build("业务类型不能为空"));
        return success(attachmentService.find(bizTypes, bizIds));
    }
}
