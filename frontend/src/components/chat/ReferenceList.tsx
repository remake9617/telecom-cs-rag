import { Collapse, Space, Tag, Typography } from 'antd';
import { FileTextOutlined } from '@ant-design/icons';
import type { Reference } from '@/types';

const { Text, Paragraph } = Typography;

// 引用来源（RAG 溯源）：每条来源默认折叠，展示 docTitle + 相关度；点击展开看 chunk 原文预览。
// 对应契约 reference 事件的 references:[{docTitle,chunkText,score}]。

interface Props {
  references?: Reference[];
}

export default function ReferenceList({ references }: Props) {
  if (!references || references.length === 0) return null;

  return (
    <div style={{ marginTop: 10 }}>
      <Text type="secondary" style={{ fontSize: 12 }}>
        引用来源（{references.length}）
      </Text>
      <Collapse
        size="small"
        ghost
        style={{ marginTop: 4 }}
        items={references.map((ref, i) => ({
          key: String(i),
          label: (
            <Space size={6} wrap>
              <FileTextOutlined style={{ color: '#1677ff' }} />
              <Text type="secondary">[{i + 1}]</Text>
              <Text strong>{ref.docTitle}</Text>
              <Tag color="blue" style={{ marginInlineEnd: 0 }}>
                相关度 {(ref.score * 100).toFixed(0)}%
              </Tag>
            </Space>
          ),
          children: (
            <Paragraph
              type="secondary"
              style={{ marginBottom: 0, whiteSpace: 'pre-wrap', fontSize: 13 }}
            >
              {ref.chunkText}
            </Paragraph>
          ),
        }))}
      />
    </div>
  );
}
