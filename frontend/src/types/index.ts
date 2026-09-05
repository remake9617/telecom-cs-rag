// 领域 VO 类型：严格对齐 contract/rest-api.md「VO 结构速查」，供 API 层与 Mock 共用。

/** 角色枚举：与后端 SysUser/UserVO 对齐——VISITOR(访客) / AGENT(客服坐席) / ADMIN(管理员)（D15 三角色）。 */
export type Role = 'ADMIN' | 'AGENT' | 'VISITOR';

export interface UserVO {
  id: number;
  username: string;
  nickname: string;
  role: Role;
}

/** 注册 / 登录返回：JWT + 用户信息 */
export interface AuthResult {
  token: string;
  user: UserVO;
}

/** 引用来源：SSE reference 事件与 MessageVO.references 共用结构 */
export interface Reference {
  docTitle: string;
  chunkText: string;
  score: number;
}

export type MessageRole = 'user' | 'assistant';

export interface MessageVO {
  id: number;
  role: MessageRole;
  content: string;
  references?: Reference[];
  createdAt: string;
  /** token 消耗（后端已随 MessageVO 补发；DashScope 未回传 usage 时为 null/缺失，故可选） */
  tokenCost?: number;
}

export interface ConversationVO {
  id: number;
  title: string;
  lastActiveAt: string;
}

/**
 * 文档 VO。status/sourceType/fileType 后端已冻结为大写枚举，这里用 string 承载，
 * UI 侧用映射表兜底展示，避免后端取值差异导致前端崩溃。已冻结枚举：
 * - status：PENDING | PROCESSING | DONE | FAILED
 * - sourceType：UPLOAD | URL
 * - fileType：MD | TXT | PDF | WORD | EXCEL | HTML
 */
export interface DocumentVO {
  id: number;
  kbId: number;
  title: string;
  sourceType: string; // UPLOAD / URL
  fileType: string; // MD / TXT / PDF / WORD / EXCEL / HTML
  chunkCount: number;
  status: string; // 入库状态：PENDING / PROCESSING / DONE / FAILED
  createdAt: string;
}

export interface KnowledgeBaseVO {
  id: number;
  name: string;
  description: string;
  embeddingModel: string;
  status: string;
}

/** 文档入库进度轮询返回 */
export interface DocStatusVO {
  status: string;
  chunkCount: number;
}

export type TicketStatus = 'OPEN' | 'REPLIED' | 'CLOSED';

export interface TicketVO {
  id: number;
  question: string;
  aiReason?: string;
  status: TicketStatus;
  reply?: string;
  handlerId?: number;
  createdAt: string;
  repliedAt?: string;
}

// ---------- 统计看板 ----------

/** 概览看板：字段严格对齐后端 OverviewVO（avgTokenCost 归阶段二，前端暂未消费，保留可选） */
export interface StatsOverview {
  askCount: number;
  // D24：无样本时后端返回 null（不硬造 0%），故为 number | null，UI 用 formatPercent 兜底显示「-」
  resolveRate: number | null;
  ticketRate: number | null;
  openTicketCount?: number;
  userCount?: number;
  docCount?: number;
  kbCount?: number;
  avgTokenCost?: number;
}

export interface HotQuestion {
  question: string;
  count: number;
}

export interface TrendPoint {
  date: string;
  askCount: number;
  resolveCount: number;
}

// ---------- SSE 事件负载 ----------

/** event: done 负载（后端已补发 tokenCost，取不到时为 null；保留可选以防御异常路径漏发，缺失时前端不展示 token 标签） */
export interface StreamDoneInfo {
  messageId: number;
  conversationId: number;
  tokenCost?: number;
}

/** event: error 负载 */
export interface StreamErrorInfo {
  code: number;
  message: string;
}

/**
 * event: ticket_hint 负载（见 rest-api.md SSE 段）：
 * TICKET 意图 / 无召回兜底时，后端已自动建单，此事件是「已建单通知」而非「请你去建单」。
 */
export interface StreamTicketHint {
  conversationId?: number;
  ticketId: number;
  autoCreated?: boolean;
}
