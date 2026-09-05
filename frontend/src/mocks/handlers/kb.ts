import { http } from 'msw';
import { fail, nowStr, ok, page } from './_helpers';
import { db, nextId } from '../db';
import type { DocumentVO, KnowledgeBaseVO } from '@/types';

// 知识库 Mock：知识库增查、文档分页、上传/URL 入库（模拟异步入库进度）、删除、重建索引、状态轮询。

/** 模拟异步入库：一段时间后把 PROCESSING 置为 DONE 并给出 chunkCount */
function scheduleIngest(docId: number, ms = 3000): void {
  setTimeout(() => {
    db.documents = db.documents.map((d) =>
      d.id === docId
        ? { ...d, status: 'DONE', chunkCount: 10 + Math.floor(Math.random() * 40) }
        : d,
    );
  }, ms);
}

export const kbHandlers = [
  http.get('*/api/kb/bases', () => ok(db.kbs)),

  http.post('*/api/kb/bases', async ({ request }) => {
    const body = (await request.json()) as { name?: string; description?: string };
    if (!body.name) return fail(1001, '知识库名称不能为空');
    const kb: KnowledgeBaseVO = {
      id: nextId(),
      name: body.name,
      description: body.description ?? '',
      embeddingModel: 'bge-m3',
      status: 'ACTIVE',
    };
    db.kbs = [kb, ...db.kbs];
    return ok(kb);
  }),

  http.get('*/api/kb/documents', ({ request }) => {
    const url = new URL(request.url);
    const kbId = url.searchParams.get('kbId');
    const current = Number(url.searchParams.get('current') ?? 1);
    const size = Number(url.searchParams.get('size') ?? 10);
    const filtered = kbId ? db.documents.filter((d) => d.kbId === Number(kbId)) : db.documents;
    const start = (current - 1) * size;
    return page(filtered.slice(start, start + size), current, size, filtered.length);
  }),

  http.post('*/api/kb/documents/upload', async ({ request }) => {
    const form = await request.formData();
    const file = form.get('file') as File | null;
    const kbId = Number(form.get('kbId'));
    const title = file?.name ?? '未命名文档';
    const doc: DocumentVO = {
      id: nextId(),
      kbId,
      title,
      sourceType: 'FILE',
      fileType: title.includes('.') ? title.split('.').pop()! : 'bin',
      chunkCount: 0,
      status: 'PROCESSING',
      createdAt: nowStr(),
    };
    db.documents = [doc, ...db.documents];
    scheduleIngest(doc.id);
    return ok(doc);
  }),

  http.post('*/api/kb/documents/url', async ({ request }) => {
    const body = (await request.json()) as { url?: string; kbId?: number };
    if (!body.url) return fail(2002, 'URL 不能为空');
    const doc: DocumentVO = {
      id: nextId(),
      kbId: Number(body.kbId),
      title: body.url,
      sourceType: 'URL',
      fileType: 'url',
      chunkCount: 0,
      status: 'PROCESSING',
      createdAt: nowStr(),
    };
    db.documents = [doc, ...db.documents];
    scheduleIngest(doc.id, 4000);
    return ok(doc);
  }),

  http.delete('*/api/kb/documents/:id', ({ params }) => {
    const id = Number(params.id);
    db.documents = db.documents.filter((d) => d.id !== id);
    return ok(null);
  }),

  http.post('*/api/kb/documents/:id/reindex', ({ params }) => {
    const id = Number(params.id);
    db.documents = db.documents.map((d) =>
      d.id === id ? { ...d, status: 'PROCESSING', chunkCount: 0 } : d,
    );
    scheduleIngest(id, 2500);
    return ok(null);
  }),

  http.get('*/api/kb/documents/:id/status', ({ params }) => {
    const id = Number(params.id);
    const doc = db.documents.find((d) => d.id === id);
    return ok({ status: doc?.status ?? 'DONE', chunkCount: doc?.chunkCount ?? 0 });
  }),
];
