package com.cs.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 点赞点踩请求（rest-api 第 5 节 POST /api/feedback）。
 */
@Data
public class FeedbackRequest {

    @NotNull(message = "messageId 不能为空")
    private Long messageId;

    /** LIKE / DISLIKE */
    @NotBlank(message = "反馈类型不能为空")
    private String type;

    @Size(max = 512, message = "评论最长 512 字符")
    private String comment;
}
