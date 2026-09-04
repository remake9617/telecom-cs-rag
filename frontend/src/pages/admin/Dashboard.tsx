import { useMemo } from 'react';
import { Card, Col, Row, Statistic, Typography } from 'antd';
import {
  CheckCircleOutlined,
  CommentOutlined,
  DatabaseOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import { Bar, Line } from '@ant-design/plots';
import StateBlock from '@/components/common/StateBlock';
import { useHotQuestions, useStatsOverview, useTrend } from '@/hooks/useStats';
import { formatPercent } from '@/utils/format';

const { Text } = Typography;

// 统计看板：概览指标卡 + 近 7 日咨询/解决趋势（折线）+ 热点问题 Top10（条形）。
export default function Dashboard() {
  const overview = useStatsOverview();
  const hot = useHotQuestions(10);
  const trend = useTrend(7);

  // 趋势转长表：便于用 colorField 画「咨询量 / 解决量」双序列折线
  const trendData = useMemo(
    () =>
      (trend.data ?? []).flatMap((p) => [
        { date: p.date, type: '咨询量', value: p.askCount },
        { date: p.date, type: '解决量', value: p.resolveCount },
      ]),
    [trend.data],
  );

  const o = overview.data;

  return (
    <div>
      <StateBlock loading={overview.isLoading} error={overview.error} onRetry={overview.refetch}>
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title="累计咨询量"
                value={o?.askCount ?? 0}
                prefix={<CommentOutlined style={{ color: '#1677ff' }} />}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title="解决率"
                value={formatPercent(o?.resolveRate)}
                prefix={<CheckCircleOutlined style={{ color: '#52c41a' }} />}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title="转人工率"
                value={formatPercent(o?.ticketRate)}
                prefix={<SwapOutlined style={{ color: '#faad14' }} />}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title="知识库 / 文档"
                value={`${o?.kbCount ?? 0} / ${o?.docCount ?? 0}`}
                prefix={<DatabaseOutlined style={{ color: '#722ed1' }} />}
              />
            </Card>
          </Col>
        </Row>
      </StateBlock>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={14}>
          <Card title="近 7 日咨询趋势">
            <StateBlock loading={trend.isLoading} error={trend.error} empty={trendData.length === 0}>
              <Line data={trendData} xField="date" yField="value" colorField="type" height={300} />
            </StateBlock>
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title="热点问题 Top 10">
            <StateBlock
              loading={hot.isLoading}
              error={hot.error}
              empty={!hot.data || hot.data.length === 0}
            >
              <Bar data={hot.data ?? []} xField="question" yField="count" height={300} />
            </StateBlock>
          </Card>
        </Col>
      </Row>

      <Text type="secondary" style={{ display: 'block', marginTop: 12, fontSize: 12 }}>
        说明：指标口径来自 /api/stats（cs-stats）。当前为 Mock 数据，联调后自动切换真实统计。
      </Text>
    </div>
  );
}
