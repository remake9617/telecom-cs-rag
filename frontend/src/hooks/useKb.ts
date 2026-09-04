import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { kbApi, type CreateKbParams, type DocListParams, type UrlIngestParams } from '@/api';
import { notify } from '@/utils/notify';
import { queryKeys } from './queryKeys';

// 知识库 / 文档（管理员）。文档列表相关统一用 ['kb','documents'] 前缀失效。

const DOCS_PREFIX = ['kb', 'documents'] as const;

export function useKnowledgeBases() {
  return useQuery({ queryKey: queryKeys.kbBases, queryFn: kbApi.bases });
}

export function useCreateKnowledgeBase() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (params: CreateKbParams) => kbApi.createBase(params),
    onSuccess: () => {
      notify.success('知识库已创建');
      qc.invalidateQueries({ queryKey: queryKeys.kbBases });
    },
  });
}

export function useDocuments(params: DocListParams) {
  return useQuery({
    queryKey: queryKeys.documents(params),
    queryFn: () => kbApi.documents(params),
    // 列表中存在「进行中」文档（未到 DONE/FAILED）时自动轮询，实现入库进度实时反馈
    refetchInterval: (query) => {
      const records = query.state.data?.records ?? [];
      return records.some((d) => !['DONE', 'FAILED'].includes(d.status)) ? 3000 : false;
    },
  });
}

export function useUploadDocument() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { file: File; kbId: number }) => kbApi.upload(vars.file, vars.kbId),
    onSuccess: () => {
      notify.success('文档已提交入库');
      qc.invalidateQueries({ queryKey: DOCS_PREFIX });
    },
  });
}

export function useIngestUrl() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (params: UrlIngestParams) => kbApi.ingestUrl(params),
    onSuccess: () => {
      notify.success('URL 已提交抓取入库');
      qc.invalidateQueries({ queryKey: DOCS_PREFIX });
    },
  });
}

export function useDeleteDocument() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => kbApi.deleteDocument(id),
    onSuccess: () => {
      notify.success('文档已删除');
      qc.invalidateQueries({ queryKey: DOCS_PREFIX });
    },
  });
}

export function useReindexDocument() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => kbApi.reindex(id),
    onSuccess: () => {
      notify.success('已触发重建索引');
      qc.invalidateQueries({ queryKey: DOCS_PREFIX });
    },
  });
}

/**
 * 入库进度轮询：仅对进行中的文档启用；到达终态（DONE/FAILED）自动停止轮询。
 * refetchInterval 回调依据最新 data.status 动态决定是否继续。
 */
export function useDocStatus(id: number | null, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.docStatus(id ?? -1),
    queryFn: () => kbApi.status(id as number),
    enabled: enabled && id != null,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status && !['DONE', 'FAILED'].includes(status) ? 1500 : false;
    },
  });
}
