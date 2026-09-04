package com.cs.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cs.framework.common.ErrorCode;
import com.cs.framework.exception.BizException;
import com.cs.system.entity.Feedback;
import com.cs.system.mapper.FeedbackMapper;
import com.cs.system.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 反馈服务实现。
 *
 * <p>type 合法值白名单校验在前，避免脏数据进入统计口径（resolveRate 依赖 LIKE/DISLIKE）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private static final Set<String> VALID_TYPES = Set.of("LIKE", "DISLIKE");

    private final FeedbackMapper feedbackMapper;

    @Override
    @Transactional
    public void submit(Long messageId, Long userId, String type, String comment) {
        if (type == null || !VALID_TYPES.contains(type)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "反馈类型必须是 LIKE 或 DISLIKE");
        }
        if (feedbackMapper.countMessageById(messageId) == 0) {
            throw new BizException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        Feedback existing = feedbackMapper.selectOne(
                new LambdaQueryWrapper<Feedback>()
                        .eq(Feedback::getMessageId, messageId)
                        .eq(Feedback::getUserId, userId)
                        .last("LIMIT 1"));
        if (existing != null) {
            existing.setType(type);
            existing.setComment(comment);
            feedbackMapper.updateById(existing);
            log.info("反馈改票: messageId={}, userId={}, type={}", messageId, userId, type);
        } else {
            Feedback feedback = new Feedback();
            feedback.setMessageId(messageId);
            feedback.setUserId(userId);
            feedback.setType(type);
            feedback.setComment(comment);
            feedbackMapper.insert(feedback);
            log.info("反馈落库: messageId={}, userId={}, type={}", messageId, userId, type);
        }
    }
}
