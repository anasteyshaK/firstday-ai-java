import { useEffect, useRef, useState, type FormEvent } from 'react';
import {
  BookOpen, Code2, FileText, FolderOpen, RefreshCw,
  Search, ShieldCheck, Users, ChevronRight, Layers3, ClipboardCheck,
  Server, Box, GraduationCap, Headphones, Zap, Settings2, Workflow, X,
} from 'lucide-react';
import type { AISource } from '../components/AIMessage';
import './KnowledgeHub.css';
import { apiFetch } from '../services/auth';
import type { Project } from '../components/ProjectSwitcher';

interface DocumentSummary {
  title: string;
  file_path: string;
  category: string;
  department: string | null;
  updated_at: string;
}

interface CategorySummary {
  name: string;
  count: number;
}

interface DocumentsResponse {
  items: DocumentSummary[];
  total: number;
  categories: CategorySummary[];
}

interface KnowledgeHubProps {
  onOpenSource: (source: AISource) => void;
  project?: Project | null;
}

const categoryIcons: Record<string, typeof BookOpen> = {
  '00_company': BookOpen, '01_hr': Code2, '02_it_support': Users,
  '03_security': ShieldCheck, '04_engineering': Layers3, '05_qa': ClipboardCheck,
  '06_devops': Server, '07_product': Box, '08_learning': GraduationCap,
  '09_support': Headphones, '10_operations': Settings2, '11_incidents': Zap,
  '12_process': Workflow,
};

const categoryName = (category: string) => {
  const names: Record<string, string> = {
    '00_company': 'Company',
    '01_hr': 'Getting Started & HR',
    '02_it_support': 'IT Support',
    '03_security': 'Security',
    '04_engineering': 'Engineering',
    '05_qa': 'Quality Assurance',
    '06_devops': 'DevOps',
    '07_product': 'Product',
    '08_learning': 'Learning Paths',
    '09_support': 'Support',
    '10_operations': 'Operations',
    '11_incidents': 'Incident Runbooks',
    '12_process': 'Processes',
  };
  return names[category] ?? category.replace(/^\d+_/, '').replace(/_/g, ' ');
};

export default function KnowledgeHub({ onOpenSource, project }: KnowledgeHubProps) {
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');
  const [data, setData] = useState<DocumentsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [showDocuments, setShowDocuments] = useState(false);
  const results = useRef<HTMLElement>(null);
  const categoryHome = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedQuery(query.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    const controller = new AbortController();
    const params = new URLSearchParams();
    if (debouncedQuery) params.set('q', debouncedQuery);
    if (selectedCategory) params.set('category', selectedCategory);

    setLoading(true);
    setError('');
    const endpoint = project ? `/api/v1/projects/${project.id}/documents` : '/api/v1/documents';
    setData(null);
    apiFetch(`${endpoint}${params.size ? `?${params.toString()}` : ''}`, {
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) throw new Error(`Ошибка загрузки документов: HTTP ${response.status}`);
        return (await response.json()) as DocumentsResponse;
      })
      .then(setData)
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) {
          setError(reason instanceof Error ? reason.message : 'Не удалось загрузить документы');
        }
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });

    return () => controller.abort();
  }, [debouncedQuery, selectedCategory, refresh, project?.id]);

  const categories = data?.categories ?? [];
  const documentCount = categories.reduce((sum, category) => sum + category.count, 0);
  const availableDocumentCount = loading || error ? '—' : documentCount;
  const resultsVisible = showDocuments || !!selectedCategory || !!debouncedQuery;

  function revealDocuments() {
    setShowDocuments(true);
    window.requestAnimationFrame(() => {
      results.current?.focus({ preventScroll: true });
      results.current?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' });
    });
  }

  function searchDocuments(event: FormEvent) {
    event.preventDefault();
    setDebouncedQuery(query.trim());
    revealDocuments();
  }

  function backToCategories() {
    setShowDocuments(false);
    setSelectedCategory('');
    setQuery('');
    setDebouncedQuery('');
    window.requestAnimationFrame(() => categoryHome.current?.focus());
  }

  function openDocument(document: DocumentSummary) {
    onOpenSource({
      reference: 1,
      title: document.title,
      file_path: document.file_path,
      similarity: null,
    });
  }

  return (
    <section className={`kh-root ${categories.length > 11 ? 'kh-many-categories' : ''}`}>
      <header className="kh-hero">
        <img className="kh-hero-art" src="/assets/knowledge/documents-wave.png" alt="" aria-hidden="true" />
        <span className="eyebrow">KNOWLEDGE HUB</span>
        <h1>{project ? `${project.name} · документация` : 'NovaTech Resource Library'}</h1>
        <p>{project ? 'Материалы выбранного проекта для подключения и ежедневной работы.' : 'Find company guides, policies and onboarding resources.'}</p>
      </header>

      <div className="kh-overview">
        <div>
          <h2>{project ? `Your ${project.name} knowledge base` : 'Everything you need to get started'}</h2>
          <p>{project ? 'Browse the documents available to your project team.' : 'Browse approved internal documents and open the original source in one click.'}</p>
        </div>
        <div className="kh-counter"><strong>{availableDocumentCount}</strong><span>available {availableDocumentCount === 1 ? 'document' : 'documents'}</span></div>
      </div>

      <form className="kh-search" onSubmit={searchDocuments} role="search">
        <Search size={22} aria-hidden="true" />
        <input
          aria-label="Search knowledge base"
          placeholder="Search guides, titles, or the contents of documents..."
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          maxLength={150}
        />
        {query && <button type="button" className="kh-clear" onClick={() => { setQuery(''); setDebouncedQuery(''); }} aria-label="Clear search"><X size={17} /></button>}
        <button type="submit" className="kh-search-submit" aria-label="Search documents"><Search size={23} strokeWidth={1.8} /></button>
      </form>

      {error && !resultsVisible && <div className="kh-notice" role="alert"><span>Не удалось загрузить документы. Попробуйте ещё раз.</span><button type="button" onClick={() => setRefresh(n => n + 1)}>Повторить</button></div>}

      <div className="kh-heading-row"><h2>Explore categories</h2><span>{project ? 'Project resources' : 'Company resources'}</span></div>
      <div className="kh-category-grid">
        <button
          ref={categoryHome}
          className={`kh-category ${!selectedCategory ? 'kh-category-active' : ''}`}
          onClick={() => { setSelectedCategory(''); revealDocuments(); }}
          aria-pressed={!selectedCategory}
        >
          <span className="kh-category-icon"><FolderOpen size={28} strokeWidth={1.65} /></span>
          <span className="kh-category-copy"><strong>All resources</strong><span className="kh-category-count">{availableDocumentCount} {availableDocumentCount === 1 ? 'document' : 'documents'}</span></span>
          <ChevronRight size={20} aria-hidden="true" />
        </button>
        {categories.map(category => {
          const Icon = categoryIcons[category.name] ?? FolderOpen;
          return (
            <button
              key={category.name}
              className={`kh-category ${selectedCategory === category.name ? 'kh-category-active' : ''}`}
              onClick={() => { setSelectedCategory(category.name); revealDocuments(); }}
              aria-pressed={selectedCategory === category.name}
            >
              <span className="kh-category-icon"><Icon size={28} strokeWidth={1.7} /></span>
              <span className="kh-category-copy"><strong>{categoryName(category.name)}</strong><span className="kh-category-count">{category.count} {category.count === 1 ? 'document' : 'documents'}</span></span>
              <ChevronRight size={20} aria-hidden="true" />
            </button>
          );
        })}
      </div>

      {resultsVisible && <section className="kh-results" ref={results} tabIndex={-1} aria-label="Document results">
      <div className="kh-heading-row kh-doc-heading">
        <h2>{selectedCategory ? categoryName(selectedCategory) : 'Documents'}</h2>
        <div className="kh-doc-actions">
          <button type="button" className="kh-back" onClick={backToCategories}>Back to categories</button>
          <span>{loading ? 'Searching...' : `${data?.total ?? 0} ${data?.total === 1 ? 'result' : 'results'}`}</span>
          <button aria-label="Reload documents" title="Reload documents" onClick={() => setRefresh(n => n + 1)}>
            <RefreshCw size={17} />
          </button>
        </div>
      </div>

      {error && <div className="kh-notice" role="alert"><span>Не удалось загрузить документы. Попробуйте ещё раз.</span><button type="button" onClick={() => setRefresh(n => n + 1)}>Повторить</button></div>}
      {!error && (
        <div className="kh-doc-list">
          {loading && <div className="kh-empty" role="status">Загружаем документы…</div>}
          {!loading && data?.items.length === 0 && (
            <div className="kh-empty">Нет документов по выбранным условиям. Попробуй другой запрос или категорию.</div>
          )}
          {!loading && data?.items.map((document) => (
            <button
              className="kh-doc-row"
              key={document.file_path}
              onClick={() => openDocument(document)}
              title="Open document"
            >
              <span className="kh-doc-icon"><FileText size={19} /></span>
              <span className="kh-doc-text"><strong>{document.title}</strong><small>{document.file_path}</small></span>
              <span className="kh-doc-department">{document.department || categoryName(document.category)}</span>
              <span className="kh-doc-open">Open <ChevronRight size={16} /></span>
            </button>
          ))}
        </div>
      )}
      <p className="kh-footnote">{project ? 'Доступ к материалам проверяется по участию в выбранном проекте.' : 'Корпоративные материалы доступны после входа в аккаунт.'}</p>
      </section>}
    </section>
  );
}
