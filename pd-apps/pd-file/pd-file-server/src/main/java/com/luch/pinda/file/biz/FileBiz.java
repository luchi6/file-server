package com.luch.pinda.file.biz;

import cn.hutool.core.util.StrUtil;
import com.luch.pinda.file.domain.FileDO;
import com.luch.pinda.file.enumeration.DataType;
import com.luch.pinda.file.utils.ZipUtils;
import com.itheima.pinda.utils.NumberHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件和附件的一些公共方法
 */
@Component
@Slf4j
public class FileBiz {

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private HttpServletResponse response;
    /**
     * 构建新文件名称
     *
     * @param filename 文件名
     * @param order
     * @return
     */
    private static String buildNewFileName(String filename, Integer order) {
        return StrUtil.strBuilder(filename).
                insert(filename.lastIndexOf("."), "(" + order + ")").toString();
    }

    /**
     * 下载文件
     *
     * @param list
     * @throws Exception
     */
    public void down(List<FileDO> list) throws Exception {
        int fileSize = list.stream()
                .filter(file -> file != null &&
                        !DataType.DIR.eq(file.getDataType()) &&   //不是目录
                        StringUtils.isNotEmpty(file.getUrl()))
                .mapToInt(
                        file -> NumberHelper.intValueOf0(file.getSize()))
                .sum();

        //获取第一个文件的文件名
        String fileName = list.get(0).getSubmittedFileName();
        //下载多余一个文件，zip文件名获取
        if (list.size() > 1) {
            fileName = StringUtils.substring(fileName, 0,
                    StringUtils.lastIndexOf(fileName, ".")) +
                    "等.zip";
        }

        //key文件名，value文件地址
        Map<String, String> map = new LinkedHashMap<>(list.size());
        //key文件名，value文件名重复次数
        Map<String, Integer> duplicateFile = new HashMap<>(list.size());
        //循环处理相同的文件名
        list.stream()
                .filter(file -> file != null &&
                        !DataType.DIR.eq(file.getDataType()) &&   //不是目录
                        StringUtils.isNotEmpty(file.getUrl()))
                .forEach((file) -> {
                    String submittedFileName = file.getSubmittedFileName();
                    if (map.containsKey(submittedFileName)) {
                        if (duplicateFile.containsKey(submittedFileName)) {
                            duplicateFile.put(submittedFileName, duplicateFile.get(submittedFileName) + 1);
                        } else {
                            duplicateFile.put(submittedFileName, 1);
                        }
                        submittedFileName = buildNewFileName(submittedFileName, duplicateFile.get(submittedFileName));
                    }
                    map.put(submittedFileName, file.getUrl());
                });


        ZipUtils.zipFilesByInputStream(map, (long) fileSize, fileName, request, response);
    }
}
