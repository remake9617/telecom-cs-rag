package com.cs.qa.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cs.framework.common.R;
import com.cs.framework.security.SecurityUtils;
import com.cs.qa.dto.ChatRequest;
import com.cs.qa.entity.ChatConversation;
import com.cs.qa.entity.ChatMessage;
import com.cs.qa.mapper.ChatConversationMapper;
import com.cs.qa.mapper.ChatMessageMapper;
import com.cs.qa.service.QaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 问答接口（对应 contract/rest-api.md 第 2 节 /api/qa）。
 *
 * <p>userId 说明：MVP 暂用默认用户（QaService.DEFAULT_USER_ID），M3 接入 Spring Security + JWT 后
 * 从认证上下文取真实 userId。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;

    /** 流式问答（SSE）：POST + text/event-stream，前端用 fetch+ReadableStream 消费 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        return qaService.chatStream(SecurityUtils.requireUserId(), request.getConversationId(), request.getQuestion());
    }

    /** 我的会话列表（MVP 默认用户） */
    @GetMapping("/conversations")
    public R<List<ChatConversation>> conversations() {
        Long userId = SecurityUtils.requireUserId();
        return R.ok(conversationMapper.selectList(
                new QueryWrapper<ChatConversation>().eq("user_id", userId).orderByDesc("last_active_at")));
    }

    /** 会话历史消息 */
    @GetMapping("/conversations/{id}/messages")
    public R<List<ChatMessage>> messages(@PathVariable Long id) {
        return R.ok(messageMapper.selectList(
                new QueryWrapper<ChatMessage>().eq("conversation_id", id).orderByAsc("id")));
    }

    /** 删除会话 */
    @DeleteMapping("/conversations/{id}")
    public R<Void> delete(@PathVariable Long id) {
        conversationMapper.deleteById(id);
        return R.ok();
    }
}
