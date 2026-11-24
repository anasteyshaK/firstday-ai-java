import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { BookOpen, ExternalLink, Sparkle } from 'lucide-react';
import './AIMessage.css';

export interface AISource {
  reference: number;
  title: string;
  file_path: string;
  similarity: number | null;
}

interface AIMessageProps {
  content: string;
  sources?: AISource[];
  onOpenSource: (source: AISource) => void;
  pending?: boolean;
}

function withCitationLinks(content: string, sources: AISource[]): string {
  const known = new Set(sources.map((s) => s.reference));
  // Convert standalone citations [1] to internal links. Never interpolate paths.
  return content.replace(/\[(\d+)\]/g, (whole, num: string) => {
    const ref = Number(num);
    return known.has(ref) ? `[[${ref}]](#source-${ref})` : whole;
  });
}

export default function AIMessage({
  content,
  sources = [],
  onOpenSource,
  pending = false,
}: AIMessageProps) {
  const cited = new Set(
    [...content.matchAll(/\[(\d+)\]/g)].map((match) => Number(match[1]))
  );
  const citedSources = sources.filter((source) => cited.has(source.reference));
  const linkedContent = withCitationLinks(content, sources);

  return (
    <div className="firstday-ai-message">
      <div className="firstday-ai-avatar" aria-hidden="true">
        <Sparkle size={20} strokeWidth={1.8} />
      </div>
      <div className="firstday-ai-content">
        <div className="firstday-ai-name">
          FirstDay AI
          <span className="firstday-ai-label">AI ASSISTANT</span>
        </div>
        <div className="firstday-ai-markdown">
          <ReactMarkdown
            remarkPlugins={[remarkGfm]}
            components={{
              a: ({ href, children }) => {
                if (href?.startsWith('#source-')) {
                  const ref = Number(href.slice('#source-'.length));
                  const source = sources.find((s) => s.reference === ref);
                  if (source) {
                    return (
                      <button
                        type="button"
                        className="firstday-citation"
                        onClick={() => onOpenSource(source)}
                        title={`Открыть документ: ${source.title}`}
                      >
                        {children}
                      </button>
                    );
                  }
                }
                return (
                  <a href={href} target="_blank" rel="noopener noreferrer">
                    {children}
                  </a>
                );
              },
            }}
          >
            {linkedContent}
          </ReactMarkdown>
        </div>
        {pending && <div className="fd-assistant-thinking" role="status"><span /><span /><span />{content ? 'Ответ формируется…' : 'Thinking…'}</div>}
        {citedSources.length > 0 && (
          <div className="firstday-sources">
            <div className="firstday-sources-title">
              <BookOpen size={15} /> Related resources
            </div>
            <div className="firstday-sources-grid">
              {citedSources.map((source) => (
                <button
                  type="button"
                  className="firstday-source-card"
                  key={source.reference}
                  onClick={() => onOpenSource(source)}
                >
                  <span className="firstday-source-icon"><BookOpen size={18} /></span>
                  <span className="firstday-source-info">
                    <span className="firstday-source-name">[{source.reference}] {source.title}</span>
                    <span className="firstday-source-path">{source.file_path}</span>
                  </span>
                  <ExternalLink size={15} aria-hidden="true" />
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
