package com.cs.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.knowledge.entity.KbKnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库 Mapper（MyBatis-Plus BaseMapper 提供单表 CRUD）。
 */
@Mapper
public interface KbKnowledgeBaseMapper extends BaseMapper<KbKnowledgeBase> {
}
