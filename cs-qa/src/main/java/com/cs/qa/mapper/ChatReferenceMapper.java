package com.cs.qa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.qa.entity.ChatReference;
import org.apache.ibatis.annotations.Mapper;

/**
 * 回答引用来源 Mapper。
 */
@Mapper
public interface ChatReferenceMapper extends BaseMapper<ChatReference> {
}
