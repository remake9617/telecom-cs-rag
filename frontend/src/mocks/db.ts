import type {
  ConversationVO,
  DocumentVO,
  KnowledgeBaseVO,
  MessageVO,
  TicketVO,
  UserVO,
} from '@/types';
import {
  seedConversations,
  seedDocuments,
  seedKbs,
  seedMessages,
  seedTickets,
  seedUsers,
} from './data/seed';

// 可变内存库：让 Mock 具备"活"的行为——发消息生成会话、上传入库、创建/回复工单等即时反映到列表与历史。
// 仅存在于浏览器内存，刷新重置为种子（currentUser 例外，用 sessionStorage 保活以便刷新后守卫正确）。
// 约定：handler 内一律「重新赋值数组」而非原地 mutate，避免污染 seed。

const SS_KEY = 'cs_mock_current_user';

function loadCurrentUser(): UserVO | null {
  try {
    const raw = sessionStorage.getItem(SS_KEY);
    return raw ? (JSON.parse(raw) as UserVO) : null;
  } catch {
    return null;
  }
}

export const db = {
  currentUser: loadCurrentUser(),
  users: [...seedUsers] as UserVO[],
  conversations: [...seedConversations] as ConversationVO[],
  messages: { ...seedMessages } as Record<number, MessageVO[]>,
  kbs: [...seedKbs] as KnowledgeBaseVO[],
  documents: [...seedDocuments] as DocumentVO[],
  tickets: [...seedTickets] as TicketVO[],
  seq: 1000,
};

export function nextId(): number {
  db.seq += 1;
  return db.seq;
}

/** 设置当前登录用户（Mock 身份），并持久化到 sessionStorage 以便刷新后 /me 恢复 */
export function setCurrentUser(user: UserVO | null): void {
  db.currentUser = user;
  try {
    if (user) sessionStorage.setItem(SS_KEY, JSON.stringify(user));
    else sessionStorage.removeItem(SS_KEY);
  } catch {
    /* 隐私模式等场景忽略 */
  }
}
