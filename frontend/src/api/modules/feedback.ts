import { http } from '../http';

// 反馈接口 /api/feedback（cs-system）。MVP 仅落库，不做回流（见 D10/D15）。

export type FeedbackType = 'LIKE' | 'DISLIKE';

export interface FeedbackParams {
  messageId: number;
  type: FeedbackType;
  comment?: string;
}

export const feedbackApi = {
  /** 点赞 / 点踩 */
  submit: (params: FeedbackParams) => http.post<null>('/api/feedback', params),
};
