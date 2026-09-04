import { http } from '../http';
import type { ConversationVO, MessageVO } from '@/types';

// 问答接口 /api/qa（cs-qa）。
// 注意：流式问答 POST /api/qa/chat/stream 属 SSE，走 api/sse/chatStream.ts，不在此封装。

export const qaApi = {
  /** 我的会话列表 */
  conversations: () => http.get<ConversationVO[]>('/api/qa/conversations'),
  /** 某会话历史消息 */
  messages: (conversationId: number) =>
    http.get<MessageVO[]>(`/api/qa/conversations/${conversationId}/messages`),
  /** 删除会话 */
  deleteConversation: (conversationId: number) =>
    http.del<null>(`/api/qa/conversations/${conversationId}`),
};
