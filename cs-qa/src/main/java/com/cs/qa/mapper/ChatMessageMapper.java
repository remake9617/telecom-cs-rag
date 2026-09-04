package com.cs.qa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.qa.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息 Mapper。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
