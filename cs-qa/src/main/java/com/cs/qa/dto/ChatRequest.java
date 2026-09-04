package com.cs.qa.dto;

import lombok.Data;

/**
 * 流式问答请求（对应 contract/rest-api.md 的 POST /api/qa/chat/stream 入参）。
 */
@Data
public class ChatRequest {

    /** 会话 ID；为空则新建会话 */
    private Long conversationId;

    /** 用户问题 */
    private String question;
}
