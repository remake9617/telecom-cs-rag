import { http } from '../http';
import type { PageVO } from '../types';
import type { TicketStatus, TicketVO } from '@/types';

// 工单接口 /api/ticket（cs-ticket）。

export interface CreateTicketParams {
  question: string;
  conversationId?: number;
}

export interface TicketListParams {
  status?: TicketStatus;
  current?: number;
  size?: number;
}

export const ticketApi = {
  /** 创建工单（AI 判定或用户手动） */
  create: (params: CreateTicketParams) => http.post<TicketVO>('/api/ticket', params),
  /** 我的工单（访客） */
  mine: () => http.get<TicketVO[]>('/api/ticket/mine'),
  /** 工单分页列表（管理员） */
  list: (params: TicketListParams) => http.get<PageVO<TicketVO>>('/api/ticket', { params }),
  /** 后台回复（管理员） */
  reply: (id: number, reply: string) => http.put<TicketVO>(`/api/ticket/${id}/reply`, { reply }),
};
