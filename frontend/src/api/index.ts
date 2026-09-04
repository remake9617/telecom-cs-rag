// API 层统一出口：调用处只从 '@/api' 引入，屏蔽内部实现细节。

export * from './types';
export { http } from './http';
export { client } from './client';
export { chatStream } from './sse/chatStream';
export type { StreamHandlers, ChatStreamPayload } from './sse/chatStream';

export { authApi } from './modules/auth';
export type { CredentialParams } from './modules/auth';
export { qaApi } from './modules/qa';
export { kbApi } from './modules/kb';
export type { CreateKbParams, DocListParams, UrlIngestParams } from './modules/kb';
export { ticketApi } from './modules/ticket';
export type { CreateTicketParams, TicketListParams } from './modules/ticket';
export { feedbackApi } from './modules/feedback';
export type { FeedbackParams, FeedbackType } from './modules/feedback';
export { statsApi } from './modules/stats';
export { systemApi } from './modules/system';
export type { UserListParams } from './modules/system';
