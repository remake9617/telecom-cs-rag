import { create } from 'zustand';
import type { MessageRole, MessageVO, Reference, StreamDoneInfo } from '@/types';

// 对话端「活跃会话」的单一数据源：消息列表 + 流式打字机缓冲。
// 为什么用 Zustand 而非 TanStack Query：SSE 高频 delta 属瞬时客户端态，
// 放进 Query 缓存会与失效/重取语义冲突；会话列表与历史消息仍由 Query 负责，
// 切换会话时用 hydrate() 把历史灌入本 store，发送/流式过程中在此就地更新。

export type StreamStatus = 'idle' | 'streaming' | 'done' | 'error';

/** 视图层消息：兼容服务端 MessageVO 与本地临时消息（流式中的助手气泡） */
export interface ChatMessage {
  /** 服务端消息 id；本地临时消息用 `temp-` 前缀字符串 */
  id: number | string;
  role: MessageRole;
  content: string;
  references?: Reference[];
  createdAt?: string;
  /** 该条助手消息是否正在流式生成 */
  streaming?: boolean;
  /** 该条助手消息状态 */
  status?: StreamStatus;
  /** 失败信息（status=error 时展示） */
  errorMsg?: string;
  /** 是否收到 ticket_hint（展示「转人工」引导） */
  ticketHint?: boolean;
  /** done 事件返回的 token 消耗 */
  tokenCost?: number;
}

let tempSeq = 0;
const nextTempId = (): string => `temp-${Date.now()}-${tempSeq++}`;

interface ChatState {
  /** 当前会话 id；新建会话时为 null，首个 done 事件回填 */
  conversationId: number | null;
  messages: ChatMessage[];
  /** 全局流式标志：禁用输入、显示「停止生成」 */
  streaming: boolean;

  setConversationId: (id: number | null) => void;
  /** 切换会话时用服务端历史灌入 */
  hydrate: (conversationId: number | null, history: MessageVO[]) => void;
  /** 新建会话：清空消息与 id */
  newConversation: () => void;
  /** 追加用户消息（乐观展示），返回临时 id */
  addUserMessage: (content: string) => string;
  /** 追加一条空的流式助手消息，返回临时 id */
  beginAssistantMessage: () => string;
  /** 向指定临时助手消息追加正文分片 */
  appendDelta: (tempId: string, delta: string) => void;
  /** 设置引用来源（reference 事件） */
  setReferences: (tempId: string, refs: Reference[]) => void;
  /** 标记可转人工（ticket_hint 事件） */
  markTicketHint: (tempId: string) => void;
  /** 完成流式（done 事件）：落 messageId、结束 streaming */
  finishAssistant: (tempId: string, info: StreamDoneInfo) => void;
  /** 流式失败（error 事件或网络异常） */
  failAssistant: (tempId: string, message: string) => void;
  /** 用户主动停止：保留已生成内容，结束 streaming（不落 messageId） */
  stopAssistant: (tempId: string) => void;
  /** 清空全部（退出登录等） */
  reset: () => void;
}

/** 就地更新指定 id 的消息，返回新数组（仅替换命中项，配合 React.memo 降低重渲染范围） */
function patchMessage(
  list: ChatMessage[],
  id: string,
  patch: Partial<ChatMessage> | ((m: ChatMessage) => Partial<ChatMessage>),
): ChatMessage[] {
  return list.map((m) => {
    if (m.id !== id) return m;
    const p = typeof patch === 'function' ? patch(m) : patch;
    return { ...m, ...p };
  });
}

export const useChatStore = create<ChatState>((set) => ({
  conversationId: null,
  messages: [],
  streaming: false,

  setConversationId: (id) => set({ conversationId: id }),

  hydrate: (conversationId, history) =>
    set({
      conversationId,
      streaming: false,
      messages: history.map<ChatMessage>((m) => ({
        id: m.id,
        role: m.role,
        content: m.content,
        references: m.references,
        createdAt: m.createdAt,
        status: 'done',
      })),
    }),

  newConversation: () => set({ conversationId: null, messages: [], streaming: false }),

  addUserMessage: (content) => {
    const id = nextTempId();
    set((s) => ({
      messages: [...s.messages, { id, role: 'user', content, createdAt: undefined }],
    }));
    return id;
  },

  beginAssistantMessage: () => {
    const id = nextTempId();
    set((s) => ({
      streaming: true,
      messages: [
        ...s.messages,
        { id, role: 'assistant', content: '', streaming: true, status: 'streaming' },
      ],
    }));
    return id;
  },

  appendDelta: (tempId, delta) =>
    set((s) => ({
      messages: patchMessage(s.messages, tempId, (m) => ({ content: m.content + delta })),
    })),

  setReferences: (tempId, refs) =>
    set((s) => ({ messages: patchMessage(s.messages, tempId, { references: refs }) })),

  markTicketHint: (tempId) =>
    set((s) => ({ messages: patchMessage(s.messages, tempId, { ticketHint: true }) })),

  finishAssistant: (tempId, info) =>
    set((s) => ({
      streaming: false,
      // 首个问答确定会话 id（新建会话场景）
      conversationId: s.conversationId ?? info.conversationId,
      messages: patchMessage(s.messages, tempId, {
        id: info.messageId ?? tempId,
        streaming: false,
        status: 'done',
        tokenCost: info.tokenCost,
      }),
    })),

  failAssistant: (tempId, message) =>
    set((s) => ({
      streaming: false,
      messages: patchMessage(s.messages, tempId, {
        streaming: false,
        status: 'error',
        errorMsg: message,
      }),
    })),

  stopAssistant: (tempId) =>
    set((s) => ({
      streaming: false,
      messages: patchMessage(s.messages, tempId, { streaming: false, status: 'done' }),
    })),

  reset: () => set({ conversationId: null, messages: [], streaming: false }),
}));
