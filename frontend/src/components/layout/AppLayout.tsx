import { Layout } from 'antd';
import { Outlet, useLocation } from 'react-router-dom';
import HeaderBar from './HeaderBar';

const { Header, Content } = Layout;

// 用户对话端布局：顶部 Header + 内容区。
// ChatPage 自带「会话侧栏 + 对话主区」，故此处不再放全局 Sider（对标主流 AI 产品布局）。

export default function AppLayout() {
  const location = useLocation();
  const title = location.pathname.startsWith('/tickets') ? '我的工单' : '智能客服';

  return (
    <Layout style={{ height: '100vh' }}>
      <Header
        style={{
          height: 56,
          lineHeight: '56px',
          display: 'flex',
          alignItems: 'center',
          paddingInline: 16,
          borderBottom: '1px solid rgba(5,5,5,0.06)',
        }}
      >
        <HeaderBar title={`电信智能客服 · ${title}`} />
      </Header>
      <Content style={{ height: 'calc(100vh - 56px)', overflow: 'hidden' }}>
        <Outlet />
      </Content>
    </Layout>
  );
}
