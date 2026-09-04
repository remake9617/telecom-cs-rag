import dayjs from 'dayjs';

// 后端时间格式为 yyyy-MM-dd HH:mm:ss（东八区），统一用 dayjs 解析展示。

/** 完整日期时间：YYYY-MM-DD HH:mm:ss */
export function formatDateTime(value?: string | null): string {
  if (!value) return '-';
  const d = dayjs(value);
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm:ss') : value;
}

/** 仅日期：YYYY-MM-DD */
export function formatDate(value?: string | null): string {
  if (!value) return '-';
  const d = dayjs(value);
  return d.isValid() ? d.format('YYYY-MM-DD') : value;
}

/** 会话列表等场景的紧凑时间：今天显示 HH:mm，否则 MM-DD */
export function formatConversationTime(value?: string | null): string {
  if (!value) return '-';
  const d = dayjs(value);
  if (!d.isValid()) return value;
  return d.isSame(dayjs(), 'day') ? d.format('HH:mm') : d.format('MM-DD');
}

/** 比率：0.87 -> 87%（保留一位小数） */
export function formatPercent(rate?: number | null): string {
  if (rate === null || rate === undefined || Number.isNaN(rate)) return '-';
  return `${(rate * 100).toFixed(1)}%`;
}
