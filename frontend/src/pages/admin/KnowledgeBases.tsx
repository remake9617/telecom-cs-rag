import { useState } from 'react';
import { Button, Card, Col, Form, Input, Modal, Row, Space, Tag, Typography } from 'antd';
import { DatabaseOutlined, FileTextOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import StateBlock from '@/components/common/StateBlock';
import { useCreateKnowledgeBase, useKnowledgeBases } from '@/hooks/useKb';

const { Text, Paragraph } = Typography;

// 知识库管理（管理员）：卡片式展示 + 新建；进入某库的文档管理。
export default function KnowledgeBases() {
  const { data, isLoading, error, refetch } = useKnowledgeBases();
  const createKb = useCreateKnowledgeBase();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<{ name: string; description?: string }>();

  const submit = async (): Promise<void> => {
    const values = await form.validateFields();
    createKb.mutate(
      { name: values.name, description: values.description ?? '' },
      {
        onSuccess: () => {
          setOpen(false);
          form.resetFields();
        },
      },
    );
  };

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <Text strong style={{ fontSize: 16 }}>
          知识库管理
        </Text>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>
          新建知识库
        </Button>
      </Space>

      <StateBlock
        loading={isLoading}
        error={error}
        onRetry={refetch}
        empty={!data || data.length === 0}
        emptyText="还没有知识库，点击右上角新建"
      >
        <Row gutter={[16, 16]}>
          {data?.map((kb) => (
            <Col xs={24} sm={12} lg={8} key={kb.id}>
              <Card
                hoverable
                actions={[
                  <a key="docs" onClick={() => navigate(`/admin/kb/${kb.id}/documents`)}>
                    <FileTextOutlined /> 文档管理
                  </a>,
                ]}
              >
                <Card.Meta
                  avatar={<DatabaseOutlined style={{ fontSize: 24, color: '#1677ff' }} />}
                  title={
                    <Space>
                      <span>{kb.name}</span>
                      <Tag color="green">{kb.status}</Tag>
                    </Space>
                  }
                  description={
                    <>
                      <Paragraph
                        type="secondary"
                        ellipsis={{ rows: 2 }}
                        style={{ minHeight: 44, marginBottom: 6 }}
                      >
                        {kb.description || '暂无描述'}
                      </Paragraph>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        向量模型：{kb.embeddingModel}
                      </Text>
                    </>
                  }
                />
              </Card>
            </Col>
          ))}
        </Row>
      </StateBlock>

      <Modal
        open={open}
        title="新建知识库"
        onOk={submit}
        onCancel={() => setOpen(false)}
        confirmLoading={createKb.isPending}
        okText="创建"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入知识库名称' }]}>
            <Input placeholder="如：套餐资费知识库" maxLength={50} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="简要描述该知识库的语料范围" maxLength={200} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
