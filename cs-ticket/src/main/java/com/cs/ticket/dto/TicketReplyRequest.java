package com.cs.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 后台回复工单请求（rest-api 第 4 节 PUT /api/ticket/{id}/reply）。
 */
@Data
public class TicketReplyRequest {

    @NotBlank(message = "回复内容不能为空")
    private String reply;
}
