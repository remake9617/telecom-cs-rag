import { http } from 'msw';
import { page } from './_helpers';
import { db } from '../db';

// 用户管理 Mock（契约见 rest-api.md 第 7 节，与 api/modules/system.ts 一致）。
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
