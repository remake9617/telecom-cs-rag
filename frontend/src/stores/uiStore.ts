import { create } from 'zustand';

// UI 全局态：主题模式（亮/暗）与侧栏折叠。属纯客户端态，不入 TanStack Query。

export type ThemeMode = 'light' | 'dark';

interface UiState {
  themeMode: ThemeMode;
  siderCollapsed: boolean;
  toggleTheme: () => void;
  setSiderCollapsed: (collapsed: boolean) => void;
}

export const useUiStore = create<UiState>((set) => ({
  themeMode: 'light',
  siderCollapsed: false,
  toggleTheme: () => set((s) => ({ themeMode: s.themeMode === 'light' ? 'dark' : 'light' })),
  setSiderCollapsed: (collapsed) => set({ siderCollapsed: collapsed }),
}));
