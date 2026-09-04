import { useCallback, useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { chatStream } from '@/api';
import { useChatStore } from '@/stores/chatStore';
import { notify } from '@/utils/notify';
import { queryKeys } from './queryKeys';

// 流式问答编排：把 SSE 事件桥接到 chatStore，实现打字机 + 引用 + 完成/失败/转人工提示。
// 为什么单独抽 hook：AbortController 生命周期、事件回调、缓存失效都属副作用编排，
// 放组件里会让 ChatPage 臃肿且难测；这里对外只暴露 send / stop / streaming。

export function useChatStream() {
  const abortRef = useRef<AbortController | null>(null);
  const queryClient = useQueryClient();
  const streaming = useChatStore((s) => s.streaming);

  // 组件卸载时中断未完成的流，避免继续写已卸载组件的状态
  useEffect(() => () => abortRef.current?.abort(), []);

  const send = useCallback(
    async (question: string) => {
      const store = useChatStore.getState();
      if (store.streaming) return; // 进行中不重复发送
      const trimmed = question.trim();
      if (!trimmed) return;

      // 乐观展示：先落用户消息，再开一条空的流式助手气泡
      store.addUserMessage(trimmed);
      const tempId = store.beginAssistantMessage();

      const controller = new AbortController();
      abortRef.current = controller;

      try {
        await chatStream(
          { conversationId: store.conversationId ?? undefined, question: trimmed },
          {
            onDelta: (delta) => useChatStore.getState().appendDelta(tempId, delta),
            onReference: (refs) => useChatStore.getState().setReferences(tempId, refs),
            onTicketHint: () => useChatStore.getState().markTicketHint(tempId),
            onDone: (info) => {
              useChatStore.getState().finishAssistant(tempId, info);
              // 新会话可能已生成：失效会话列表，让侧栏出现/更新该会话
              queryClient.invalidateQueries({ queryKey: queryKeys.conversations });
            },
            onError: (e) => {
              useChatStore.getState().failAssistant(tempId, e.message || '回答失败');
              notify.error(e.message || '回答失败');
            },
          },
          controller.signal,
        );
      } catch (err) {
        if ((err as Error)?.name === 'AbortError') {
          // 用户主动「停止生成」：保留已生成内容，标记完成，不算错误
          useChatStore.getState().stopAssistant(tempId);
        } else {
          useChatStore.getState().failAssistant(tempId, (err as Error)?.message || '网络异常');
          notify.error('流式连接异常，请重试');
        }
      } finally {
        abortRef.current = null;
      }
    },
    [queryClient],
  );

  const stop = useCallback(() => {
    abortRef.current?.abort();
  }, []);

  return { send, stop, streaming };
}
