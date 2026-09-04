import { http } from '../http';
import type { PageVO } from '../types';
import type { DocStatusVO, DocumentVO, KnowledgeBaseVO } from '@/types';

// 知识库接口 /api/kb（cs-knowledge / cs-ingestion，管理员）。

export interface CreateKbParams {
  name: string;
  description: string;
}

export interface DocListParams {
  kbId?: number;
  current?: number;
  size?: number;
}

export interface UrlIngestParams {
  url: string;
  kbId: number;
}

export const kbApi = {
  /** 知识库列表 */
  bases: () => http.get<KnowledgeBaseVO[]>('/api/kb/bases'),
  /** 新建知识库 */
  createBase: (params: CreateKbParams) => http.post<KnowledgeBaseVO>('/api/kb/bases', params),
  /** 文档分页列表 */
  documents: (params: DocListParams) =>
    http.get<PageVO<DocumentVO>>('/api/kb/documents', { params }),
  /** 上传文档（multipart，触发入库流水线） */
  upload: (file: File, kbId: number) => {
    const form = new FormData();
    form.append('file', file);
    form.append('kbId', String(kbId));
    return http.post<DocumentVO>('/api/kb/documents/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  /** URL 抓取入库 */
  ingestUrl: (params: UrlIngestParams) => http.post<DocumentVO>('/api/kb/documents/url', params),
  /** 删除文档（含 ES chunk 清理） */
  deleteDocument: (id: number) => http.del<null>(`/api/kb/documents/${id}`),
  /** 重建索引 */
  reindex: (id: number) => http.post<null>(`/api/kb/documents/${id}/reindex`),
  /** 入库进度轮询 */
  status: (id: number) => http.get<DocStatusVO>(`/api/kb/documents/${id}/status`),
};
