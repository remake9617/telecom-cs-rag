import { http } from 'msw';
import { fail, nowStr, ok, page } from './_helpers';
import { db, nextId } from '../db';
import type { TicketVO } from '@/types';

// 工单 Mock：创建、我的工单、管理员分页列表、后台回复。

export const ticketHandlers = [
  http.post('*/api/ticket', async ({ request }) => {
    const body = (await request.json()) as { question?: string; conversationId?: number };
    if (!body.question) return fail(4001, '工单内容不能为空');
    const ticket: TicketVO = {
      id: nextId(),
      question: body.question,
      aiReason: '用户手动发起',
      status: 'OPEN',
      createdAt: nowStr(),
    };
    db.tickets = [ticket, ...db.tickets];
    return ok(ticket);
  }),

  http.get('*/api/ticket/mine', () => ok(db.tickets)),

  http.get('*/api/ticket', ({ request }) => {
    const url = new URL(request.url);
    const status = url.searchParams.get('status');
    const current = Number(url.searchParams.get('current') ?? 1);
    const size = Number(url.searchParams.get('size') ?? 10);
    const filtered = status ? db.tickets.filter((t) => t.status === status) : db.tickets;
    const start = (current - 1) * size;
    return page(filtered.slice(start, start + size), current, size, filtered.length);
  }),

  http.put('*/api/ticket/:id/reply', async ({ params, request }) => {
    const id = Number(params.id);
    const { reply } = (await request.json()) as { reply?: string };
    if (!reply) return fail(4002, '回复内容不能为空');
    const target = db.tickets.find((t) => t.id === id);
    if (!target) return fail(4004, '工单不存在');
    const updated: TicketVO = {
      ...target,
      reply,
      status: 'REPLIED',
      repliedAt: nowStr(),
      handlerId: db.currentUser?.id ?? 1,
    };
    db.tickets = db.tickets.map((t) => (t.id === id ? updated : t));
    return ok(updated);
  }),
];
