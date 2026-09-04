import { http } from 'msw';
import { fail, ok } from './_helpers';

// 反馈 Mock：点赞/点踩仅落库（MVP 不做回流，见 D15），成功返回 null。
export const feedbackHandlers = [
  http.post('*/api/feedback', async ({ request }) => {
    const body = (await request.json()) as { messageId?: number; type?: string };
    if (!body.messageId) return fail(1002, '缺少 messageId');
    return ok(null);
  }),
];
