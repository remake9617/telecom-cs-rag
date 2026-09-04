import { useEffect, useState } from 'react';
import { Button, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography, Upload } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { UploadProps } from 'antd';
import { DeleteOutlined, LinkOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons';
import { useParams } from 'react-router-dom';
import {
  useDeleteDocument,
  useDocuments,
  useIngestUrl,
  useKnowledgeBases,
  useReindexDocument,
  useUploadDocument,
} from '@/hooks/useKb';
import { formatDateTime } from '@/utils/format';
import type { DocumentVO } from '@/types';

const { Text } = Typography;

// 入库状态展示映射：后端未冻结枚举，这里对常见取值兜底（未知值原样展示）
const STATUS_META: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'default', label: '排队中' },
  PROCESSING: { color: 'processing', label: '入库中' },
  PARSING: { color: 'processing', label: '解析中' },
  CHUNKING: { color: 'processing', label: '分块中' },
  EMBEDDING: { color: 'processing', label: '向量化中' },
  INDEXING: { color: 'processing', label: '索引中' },
  DONE: { color: 'success', label: '已完成' },
  FAILED: { color: 'error', label: '失败' },
};

// 文档管理（管理员）：支持从 /admin/documents（带库选择器）或 /admin/kb/:kbId/documents（固定库）进入。
export default function Documents() {
  const { kbId: kbIdParam } = useParams();
  const kbsQuery = useKnowledgeBases();
  const [selectedKb, setSelectedKb] = useState<number | undefined>();
  const kbId = kbIdParam ? Number(kbIdParam) : selectedKb;

  const [page, setPage] = useState({ current: 1, size: 10 });
  const docsQuery = useDocuments({ kbId, current: page.current, size: page.size });
  const uploadDoc = useUploadDocument();
  const ingestUrl = useIngestUrl();
  const deleteDoc = useDeleteDocument();
  const reindex = useReindexDocument();

  const [urlOpen, setUrlOpen] = useState(false);
  const [url, setUrl] = useState('');

  // 无固定库时，默认选中第一个知识库
  useEffect(() => {
    if (!kbIdParam && selectedKb === undefined && kbsQuery.data?.length) {
      setSelectedKb(kbsQuery.data[0].id);
    }
  }, [kbIdParam, selectedKb, kbsQuery.data]);

  const uploadProps: UploadProps = {
    accept: '.md,.txt,.pdf,.docx,.doc,.xlsx,.xls',
    showUploadList: false,
    disabled: kbId == null,
    customRequest: ({ file, onSuccess }) => {
      if (kbId == null) return;
      // 触发入库流水线；成功提示与列表刷新由 useUploadDocument 的 onSuccess 统一处理
      uploadDoc.mutate({ file: file as File, kbId }, { onSuccess: (res) => onSuccess?.(res) });
    },
  };

  const submitUrl = (): void => {
    if (!url.trim() || kbId == null) return;
    ingestUrl.mutate(
      { url: url.trim(), kbId },
      {
        onSuccess: () => {
          setUrlOpen(false);
          setUrl('');
        },
      },
    );
  };

  const columns: ColumnsType<DocumentVO> = [
    { title: '标题', dataIndex: 'title', ellipsis: true },
    {
      title: '来源',
      dataIndex: 'sourceType',
      width: 90,
      render: (v: string) => <Tag>{v === 'URL' ? '网页' : '文件'}</Tag>,
    },
    { title: '类型', dataIndex: 'fileType', width: 80 },
    { title: '分块数', dataIndex: 'chunkCount', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (v: string) => {
        const meta = STATUS_META[v] ?? { color: 'default', label: v };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, render: (v: string) => formatDateTime(v) },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_: unknown, record) => (
        <Space size={0}>
          <Button
            type="link"
            size="small"
            icon={<ReloadOutlined />}
            loading={reindex.isPending && reindex.variables === record.id}
            onClick={() => reindex.mutate(record.id)}
          >
            重建
          </Button>
          <Popconfirm
            title="删除该文档及其向量分块？"
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => deleteDoc.mutate(record.id)}
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }} wrap>
        <Space wrap>
          <Text strong style={{ fontSize: 16 }}>
            文档管理
          </Text>
          {!kbIdParam && (
            <Select
              style={{ width: 220 }}
              placeholder="选择知识库"
              value={selectedKb}
              loading={kbsQuery.isLoading}
              onChange={(v) => {
                setSelectedKb(v);
                setPage((p) => ({ ...p, current: 1 }));
              }}
              options={(kbsQuery.data ?? []).map((kb) => ({ value: kb.id, label: kb.name }))}
            />
          )}
        </Space>
        <Space>
          <Upload {...uploadProps}>
            <Button icon={<UploadOutlined />} disabled={kbId == null} loading={uploadDoc.isPending}>
              上传文档
            </Button>
          </Upload>
          <Button icon={<LinkOutlined />} disabled={kbId == null} onClick={() => setUrlOpen(true)}>
            URL 入库
          </Button>
        </Space>
      </Space>

      <Table<DocumentVO>
        rowKey="id"
        columns={columns}
        dataSource={docsQuery.data?.records}
        loading={docsQuery.isLoading}
        scroll={{ x: 900 }}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total: docsQuery.data?.total ?? 0,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />

      <Modal
        open={urlOpen}
        title="URL 抓取入库"
        onOk={submitUrl}
        onCancel={() => setUrlOpen(false)}
        confirmLoading={ingestUrl.isPending}
        okText="提交"
        cancelText="取消"
        okButtonProps={{ disabled: !url.trim() }}
      >
        <Input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://example.com/help/..." />
        <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
          提交后系统会抓取网页正文、分块并向量化入库，进度可在列表状态列查看。
        </Text>
      </Modal>
    </div>
  );
}
