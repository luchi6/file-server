package com.luch.pinda.file.strategy;

import com.itheima.pinda.base.R;
import com.luch.pinda.file.dto.chunk.FileChunksMergeDTO;
import com.luch.pinda.file.entity.File;
/**
 * 文件分片处理策略接口
 */
public interface FileChunkStrategy {
    /**
     * 分片合并
     *
     * @param merge
     * @return
     */
    R<File> chunksMerge(FileChunksMergeDTO merge);
}
