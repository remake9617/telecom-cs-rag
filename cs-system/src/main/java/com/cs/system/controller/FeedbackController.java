package com.cs.system.controller;

import com.cs.framework.common.R;
import com.cs.framework.security.SecurityUtils;
import com.cs.system.dto.FeedbackRequest;
import com.cs.system.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 反馈接口（对应 contract/rest-api.md 第 5 节 /api/feedback，点赞点踩仅落库）。
 */
@Slf4j
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    /** 点赞点踩：同用户同消息重复提交 = 覆盖改票 */
    @PostMapping
    public R<Void> submit(@Valid @RequestBody FeedbackRequest request) {
        feedbackService.submit(request.getMessageId(), SecurityUtils.requireUserId(),
                request.getType(), request.getComment());
        return R.ok();
    }
}
