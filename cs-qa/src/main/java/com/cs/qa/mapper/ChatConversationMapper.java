package com.cs.qa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.qa.entity.ChatConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话 Mapper。
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
}
