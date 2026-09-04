package com.cs.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.knowledge.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档元数据 Mapper。
 */
@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {
}
