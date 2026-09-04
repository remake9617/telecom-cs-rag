// 集中管理 TanStack Query 的 queryKey，避免字符串散落导致的失效（invalidate）错配。
// 约定：以模块前缀 + 参数组成数组，参数变化即天然形成不同缓存条目。

export const queryKeys = {
  me: ['me'] as const,

  conversations: ['qa', 'conversations'] as const,
  messages: (conversationId: number) => ['qa', 'messages', conversationId] as const,

  kbBases: ['kb', 'bases'] as const,
  documents: (params: { kbId?: number; current?: number; size?: number }) =>
    ['kb', 'documents', params] as const,
  docStatus: (id: number) => ['kb', 'docStatus', id] as const,

  myTickets: ['ticket', 'mine'] as const,
  tickets: (params: { status?: string; current?: number; size?: number }) =>
    ['ticket', 'list', params] as const,

  statsOverview: ['stats', 'overview'] as const,
  hotQuestions: (limit: number) => ['stats', 'hot', limit] as const,
  trend: (days: number) => ['stats', 'trend', days] as const,

  users: (params: { current?: number; size?: number; keyword?: string }) =>
    ['system', 'users', params] as const,
};
