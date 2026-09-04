package com.cs.system.service;

/**
 * 反馈服务（点赞点踩，MVP 仅落库，D10/D15）。
 */
public interface FeedbackService {

    /**
     * 提交反馈（upsert 语义）：同一用户对同一消息重复提交时覆盖改票
     * （LIKE→DISLIKE 或更新评论），符合前端「点赞/点踩按钮可切换」的交互习惯。
     *
     * @param messageId 消息 ID（必须存在）
     * @param userId    反馈人
     * @param type      LIKE / DISLIKE
     * @param comment   可选评论
     */
    void submit(Long messageId, Long userId, String type, String comment);
}
