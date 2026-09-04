import { Menu } from 'antd';
import type { MenuProps } from 'antd';
import {
  CustomerServiceOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';
import { BRAND_PRIMARY } from '@/styles/theme';

// 管理后台左侧菜单：以路由 key 驱动导航，选中项按最长前缀匹配（兼容 /admin/kb/:kbId/documents）。

const NAV = [
  { key: '/admin/dashboard', icon: <DashboardOutlined />, label: '统计看板' },
  { key: '/admin/kb', icon: <DatabaseOutlined />, label: '知识库' },
  { key: '/admin/documents', icon: <FileTextOutlined />, label: '文档管理' },
  { key: '/admin/tickets', icon: <CustomerServiceOutlined />, label: '工单管理' },
  { key: '/admin/users', icon: <TeamOutlined />, label: '用户管理' },
];

export default function AppSider() {
  const navigate = useNavigate();
  const location = useLocation();

  const selectedKey =
    NAV.map((n) => n.key)
      .filter((k) => location.pathname.startsWith(k))
      .sort((a, b) => b.length - a.length)[0] || '/admin/dashboard';

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div
        style={{
          height: 56,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontWeight: 600,
          fontSize: 15,
          color: BRAND_PRIMARY,
          whiteSpace: 'nowrap',
          overflow: 'hidden',
        }}
      >
        CS 管理后台
      </div>
      <Menu
        mode="inline"
        selectedKeys={[selectedKey]}
        items={NAV as MenuProps['items']}
        onClick={({ key }) => navigate(key)}
        style={{ flex: 1, borderInlineEnd: 'none' }}
      />
    </div>
  );
}
