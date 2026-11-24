import { useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { BookOpen, X } from 'lucide-react';
import type { AISource } from './AIMessage';
import './DocumentModal.css';
import { apiFetch } from '../services/auth';

interface DocumentResponse {
  title: string;
  file_path: string;
  category: string;
  content: string;
}

export default function DocumentModal({
  source,
  onClose,
  projectId,
}: {
  source: AISource | null;
  onClose: () => void;
  projectId?: string;
}) {
  const [document, setDocument] = useState<DocumentResponse | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!source) return;
    const controller = new AbortController();
    setDocument(null);
    setError('');
    setLoading(true);

    const params = new URLSearchParams({ path: source.file_path });
    if (projectId) params.set('project_id', projectId);
    apiFetch(`/api/v1/documents/content?${params.toString()}`, {
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) {
          if (response.status === 404) throw new Error('Документ не найден или недоступен.');
          throw new Error('Не удалось загрузить документ.');
        }
        return (await response.json()) as DocumentResponse;
      })
      .then(setDocument)
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) {
          setError(reason instanceof Error ? reason.message : 'Неизвестная ошибка.');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [source, projectId]);

  useEffect(() => {
    if (!source) return;
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [source, onClose]);

  if (!source) return null;

  return (
    <div className="fd-document-backdrop" onMouseDown={(e) => {
      if (e.target === e.currentTarget) onClose();
    }}>
      <section className="fd-document-dialog" role="dialog" aria-modal="true" aria-label={`Документ ${source.title}`}>
        <header className="fd-document-header">
          <div className="fd-document-header-title">
            <span className="fd-document-emblem"><BookOpen size={20} /></span>
            <div>
              <span className="fd-document-eyebrow">NOVATECH KNOWLEDGE BASE</span>
              <h2>{document?.title ?? source.title}</h2>
              <p>{document?.file_path ?? source.file_path}</p>
            </div>
          </div>
          <button type="button" className="fd-document-close" onClick={onClose} aria-label="Закрыть документ"><X size={20} /></button>
        </header>
        <div className="fd-document-body">
          {loading && <p className="fd-document-state">Загружаем документ...</p>}
          {error && <p className="fd-document-error" role="alert">{error}</p>}
          {document && (
            <div className="fd-document-markdown">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{document.content}</ReactMarkdown>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
