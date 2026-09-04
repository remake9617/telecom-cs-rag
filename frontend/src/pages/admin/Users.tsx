import { useState } from 'react';
import { Input, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useUsers } from '@/hooks/useSystem';
import type { Role, UserVO } from '@/types';

const { Text } = Typography;

const ROLE_META: Record<Role, { color: string; label: string }> = {
  ADMIN: { color: 'red', label: '管理员' },
  AGENT: { color: 'blue', label: '客服坐席' },
  VISITOR: { color: 'default', label: '访客' },
};

// 用户管理（仅 ADMIN）：列表 + 关键词搜索 + 分页。
// 契约见 contract/rest-api.md 第 7 节：GET /api/system/users?current=&size=&keyword= -> R<PageVO<UserVO>>。
export default function Users() {
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [keyword, setKeyword] = useState('');
  const { data, isLoading } = useUsers({
    current: page.current,
    size: page.size,
    keyword: keyword || undefined,
  });

  const columns: ColumnsType<UserVO> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '用户名', dataIndex: 'username' },
    { title: '昵称', dataIndex: 'nickname' },
    {
      title: '角色',
      dataIndex: 'role',
      width: 140,
      render: (v: Role) => {
        const m = ROLE_META[v] ?? { color: 'default', label: v };
        return <Tag color={m.color}>{m.label}</Tag>;
      },
    },
  ];

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }} wrap>
        <Text strong style={{ fontSize: 16 }}>
          用户管理
        </Text>
        <Input.Search
          placeholder="搜索用户名 / 昵称"
          allowClear
          style={{ width: 240 }}
          onSearch={(v) => {
            setKeyword(v.trim());
            setPage((p) => ({ ...p, current: 1 }));
          }}
        />
      </Space>

      <Table<UserVO>
        rowKey="id"
        columns={columns}
        dataSource={data?.records}
        loading={isLoading}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total: data?.total ?? 0,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />
    </div>
  );
}
