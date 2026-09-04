package com.cs.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.knowledge.entity.KbChunkMeta;
import org.apache.ibatis.annotations.Mapper;

/**
 * chunk 元数据 Mapper。
 */
@Mapper
public interface KbChunkMetaMapper extends BaseMapper<KbChunkMeta> {
}
