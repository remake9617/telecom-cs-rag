import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { qaApi } from '@/api';
import { queryKeys } from './queryKeys';

// 会话相关（服务端态）：列表、历史消息、删除。

export function useConversations() {
  return useQuery({ queryKey: queryKeys.conversations, queryFn: qaApi.conversations });
}

/** 某会话历史消息；conversationId 为 null（新会话）时不请求 */
export function useConversationMessages(conversationId: number | null) {
  return useQuery({
    queryKey: queryKeys.messages(conversationId ?? -1),
    queryFn: () => qaApi.messages(conversationId as number),
    enabled: conversationId != null,
  });
}

export function useDeleteConversation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => qaApi.deleteConversation(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.conversations }),
  });
}
