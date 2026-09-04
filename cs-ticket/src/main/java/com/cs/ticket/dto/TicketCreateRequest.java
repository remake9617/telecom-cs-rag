package com.cs.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建工单请求（rest-api 第 4 节 POST /api/ticket）。
 */
@Data
public class TicketCreateRequest {

    /** 用户问题（人工转办的原因载体） */
    @NotBlank(message = "问题内容不能为空")
    @Size(max = 1024, message = "问题内容最长 1024 字符")
    private String question;

    /** 来源会话 ID（可空：非对话场景手动建单） */
    private Long conversationId;
}
