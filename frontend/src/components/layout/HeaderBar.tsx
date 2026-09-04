import { Avatar, Button, Dropdown, Space, Switch, Typography } from 'antd';
import type { MenuProps } from 'antd';
import {
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MoonFilled,
  RobotOutlined,
  SettingOutlined,
  SunOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { useUiStore } from '@/stores/uiStore';
import { useChatStore } from '@/stores/chatStore';

const { Text } = Typography;

// 顶部栏：折叠触发（后台用）+ 标题 + 主题切换 + 用户下拉（角色入口 / 退出登录）。
// 用户端与管理后台共用，通过 props 差异化。

interface HeaderBarProps {
  title?: string;
  collapsed?: boolean;
  onToggleCollapse?: () => void;
}

export default function HeaderBar({ title, collapsed, onToggleCollapse }: HeaderBarProps) {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const themeMode = useUiStore((s) => s.themeMode);
  const toggleTheme = useUiStore((s) => s.toggleTheme);
  const resetChat = useChatStore((s) => s.reset);

  const handleLogout = (): void => {
    logout();
    resetChat();
    navigate('/login', { replace: true });
  };

  const items: MenuProps['items'] = [
    { key: 'chat', icon: <RobotOutlined />, label: '用户对话端' },
  ];
  if (user?.role === 'ADMIN') {
    items.push({ key: 'admin', icon: <SettingOutlined />, label: '管理后台' });
  }
  items.push({ type: 'divider' }, { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', danger: true });

  const onMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'chat') navigate('/chat');
    else if (key === 'admin') navigate('/admin/dashboard');
    else if (key === 'logout') handleLogout();
  };

  return (
    <Space style={{ width: '100%', justifyContent: 'space-between' }}>
      <Space>
        {onToggleCollapse && (
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={onToggleCollapse}
          />
        )}
        {title && (
          <Text strong style={{ fontSize: 16 }}>
            {title}
          </Text>
        )}
      </Space>
      <Space size="middle">
        <Switch
          checkedChildren={<MoonFilled />}
          unCheckedChildren={<SunOutlined />}
          checked={themeMode === 'dark'}
          onChange={toggleTheme}
        />
        <Dropdown menu={{ items, onClick: onMenuClick }} placement="bottomRight">
          <Space style={{ cursor: 'pointer' }}>
            <Avatar size="small" icon={<UserOutlined />} />
            <Text>{user?.nickname || user?.username || '未登录'}</Text>
          </Space>
        </Dropdown>
      </Space>
    </Space>
  );
}
