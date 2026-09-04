import { http } from 'msw';
import { fail, ok } from './_helpers';
import { db, nextId, setCurrentUser } from '../db';
import type { UserVO } from '@/types';

// 认证 Mock。约定：admin -> 管理员；agent* -> 坐席；其余 -> 访客；密码 'wrong' 触发业务错误，便于演示错误态。

const FAKE_TOKEN = 'mock.jwt.token';

export const authHandlers = [
  http.post('*/api/auth/register', async ({ request }) => {
    const { username, password } = (await request.json()) as { username: string; password: string };
    if (!username || !password) return fail(1001, '用户名和密码不能为空');
    if (db.users.some((u) => u.username === username)) return fail(5002, '用户名已存在');
    const user: UserVO = { id: nextId(), username, nickname: username, role: 'USER' };
    db.users = [...db.users, user];
    setCurrentUser(user);
    return ok({ token: FAKE_TOKEN, user });
  }),

  http.post('*/api/auth/login', async ({ request }) => {
    const { username, password } = (await request.json()) as { username: string; password: string };
    if (!username || !password) return fail(1001, '用户名和密码不能为空');
    if (password === 'wrong') return fail(5001, '用户名或密码错误');
    const role: UserVO['role'] =
      username === 'admin' ? 'ADMIN' : username.startsWith('agent') ? 'AGENT' : 'USER';
    const nickname = username === 'admin' ? '系统管理员' : username;
    const user: UserVO = { id: 1, username, nickname, role };
    setCurrentUser(user);
    return ok({ token: FAKE_TOKEN, user });
  }),

  http.get('*/api/auth/me', () => {
    // 刷新后 db.currentUser 由 sessionStorage 恢复；兜底给管理员，避免演示时刷新被锁在登录页
    const user: UserVO = db.currentUser ?? {
      id: 1,
      username: 'admin',
      nickname: '系统管理员',
      role: 'ADMIN',
    };
    return ok(user);
  }),
];
