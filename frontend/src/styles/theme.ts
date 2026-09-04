import { theme as antdTheme, type ThemeConfig } from 'antd';
import type { ThemeMode } from '@/stores/uiStore';

// AntD 主题配置：电信蓝主色 + 圆角，亮/暗通过 algorithm 切换（由 uiStore.themeMode 驱动）。
// 中文 locale 在 ConfigProvider 处统一配置（见 App.tsx）。

/** 品牌主色（电信蓝）；如需换品牌色改这里即可，禁止散落到各组件硬编码 */
export const BRAND_PRIMARY = '#1677ff';

export function buildTheme(mode: ThemeMode): ThemeConfig {
  const dark = mode === 'dark';
  return {
    algorithm: dark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: {
      colorPrimary: BRAND_PRIMARY,
      borderRadius: 8,
      fontSize: 14,
    },
    components: {
      Layout: {
        headerBg: dark ? '#141414' : '#ffffff',
        siderBg: dark ? '#141414' : '#ffffff',
        bodyBg: dark ? '#000000' : '#f5f7fa',
      },
    },
  };
}
