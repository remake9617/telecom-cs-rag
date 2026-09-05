import { lazy } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import AppLayout from '@/components/layout/AppLayout';
import AdminLayout from '@/components/layout/AdminLayout';
import { RequireAuth, RequireRole } from './guards';
import { SuspensePage } from './SuspensePage';

// 路由表：按角色分区（用户端 / 管理后台），页面全部懒加载以做代码分割。

const Login = lazy(() => import('@/pages/auth/Login'));
const Register = lazy(() => import('@/pages/auth/Register'));
const ChatPage = lazy(() => import('@/pages/chat/ChatPage'));
const MyTickets = lazy(() => import('@/pages/ticket/MyTickets'));
const Dashboard = lazy(() => import('@/pages/admin/Dashboard'));
const KnowledgeBases = lazy(() => import('@/pages/admin/KnowledgeBases'));
const Documents = lazy(() => import('@/pages/admin/Documents'));
const TicketAdmin = lazy(() => import('@/pages/admin/TicketAdmin'));
const Users = lazy(() => import('@/pages/admin/Users'));
const NotFound = lazy(() => import('@/pages/NotFound'));

export const router = createBrowserRouter([
  { path: '/login', element: <SuspensePage><Login /></SuspensePage> },
  { path: '/register', element: <SuspensePage><Register /></SuspensePage> },

  // 用户对话端：需登录
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/chat" replace /> },
      { path: 'chat', element: <SuspensePage><ChatPage /></SuspensePage> },
      { path: 'chat/:conversationId', element: <SuspensePage><ChatPage /></SuspensePage> },
      { path: 'tickets', element: <SuspensePage><MyTickets /></SuspensePage> },
    ],
  },

  // 管理后台：需 ADMIN 角色
  {
    path: '/admin',
    element: (
      <RequireRole role="ADMIN">
        <AdminLayout />
      </RequireRole>
    ),
    children: [
      { index: true, element: <Navigate to="/admin/dashboard" replace /> },
      { path: 'dashboard', element: <SuspensePage><Dashboard /></SuspensePage> },
      { path: 'kb', element: <SuspensePage><KnowledgeBases /></SuspensePage> },
      { path: 'documents', element: <SuspensePage><Documents /></SuspensePage> },
      { path: 'kb/:kbId/documents', element: <SuspensePage><Documents /></SuspensePage> },
      { path: 'tickets', element: <SuspensePage><TicketAdmin /></SuspensePage> },
      { path: 'users', element: <SuspensePage><Users /></SuspensePage> },
    ],
  },

  { path: '*', element: <SuspensePage><NotFound /></SuspensePage> },
]);
