import { http } from 'msw';
import { page } from './_helpers';
import { db } from '../db';

// 用户管理 Mock（端点为提案，未在 rest-api.md 冻结，详见 api/modules/system.ts）。
export const systemHandlers = [
  http.get('*/api/system/users', ({ request }) => {
    const url = new URL(request.url);
    const current = Number(url.searchParams.get('current') ?? 1);
    const size = Number(url.searchParams.get('size') ?? 10);
    const keyword = url.searchParams.get('keyword')?.trim();
    const filtered = keyword
      ? db.users.filter((u) => u.username.includes(keyword) || u.nickname.includes(keyword))
      : db.users;
    const start = (current - 1) * size;
    return page(filtered.slice(start, start + size), current, size, filtered.length);
  }),
];
