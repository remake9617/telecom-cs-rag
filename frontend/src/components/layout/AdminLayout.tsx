import { Layout } from 'antd';
import { Outlet } from 'react-router-dom';
import AppSider from './AppSider';
import HeaderBar from './HeaderBar';
import { useUiStore } from '@/stores/uiStore';

const { Header, Content, Sider } = Layout;

// 管理后台布局：左侧可折叠 Sider（断点自动收起）+ 顶部 Header + 可滚动内容区。

export default function AdminLayout() {
  const collapsed = useUiStore((s) => s.siderCollapsed);
  const setCollapsed = useUiStore((s) => s.setSiderCollapsed);

  return (
    <Layout style={{ height: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        trigger={null}
        breakpoint="lg"
        onBreakpoint={(broken) => setCollapsed(broken)}
        collapsedWidth={64}
        width={220}
        theme="light"
        style={{ borderInlineEnd: '1px solid rgba(5,5,5,0.06)' }}
      >
        <AppSider />
      </Sider>
      <Layout>
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
          <HeaderBar
            title="管理后台"
            collapsed={collapsed}
            onToggleCollapse={() => setCollapsed(!collapsed)}
          />
        </Header>
        <Content style={{ height: 'calc(100vh - 56px)', overflow: 'auto', padding: 16 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
