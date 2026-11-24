import { useEffect, useRef, type FormEvent, type ReactNode } from 'react';
import { CalendarDays, ChevronDown, Github, Plus, Send, ShieldCheck, Sparkle, Trash2, TrendingUp } from 'lucide-react';
import AIMessage, { type AISource } from '../components/AIMessage';
import type { Project } from '../components/ProjectSwitcher';
import './AssistantPage.css';

type Props = {
  project: Project | null;
  internship?: boolean;
  messages: { role: 'user' | 'assistant'; content: string; sources?: AISource[] }[];
  conversations: { id: string; title: string }[];
  conversationId: string | null;
  question: string;
  onQuestionChange: (value: string) => void;
  onSubmit: (event: FormEvent) => void;
  onAsk: (question: string) => void;
  onNewConversation: () => void;
  onOpenConversation: (id: string) => void;
  onDeleteConversation: () => void;
  loading: boolean;
  historyLoading: boolean;
  historyError: string;
  chatError: string;
  onOpenSource: (source: AISource) => void;
  renderResources: (sources: AISource[]) => ReactNode;
};

export default function AssistantPage(props: Props) {
  const log = useRef<HTMLDivElement>(null);
  const followLatest = useRef(true);
  const busy = props.loading || props.historyLoading;
  const hasConversation = props.messages.some(message => message.role === 'user');
  const suggestions = [
    { icon: CalendarDays, text: 'Какие у меня остались задачи?' },
    { icon: TrendingUp, text: 'Какой мой прогресс адаптации?' },
    { icon: Github, text: props.project ? 'Как устроен проект и какие у него компоненты?' : 'Какой статус моей заявки на GitHub?' },
    { icon: ShieldCheck, text: props.project ? 'Что мешает моему подключению?' : 'Как настроить VPN?' },
  ];

  useEffect(() => {
    if (log.current && followLatest.current) log.current.scrollTop = log.current.scrollHeight;
  }, [props.messages, props.loading]);

  function newChat() {
    followLatest.current = true;
    props.onNewConversation();
  }

  return (
    <section className="fd-assistant" aria-labelledby="fd-assistant-title">
      <header className="fd-assistant-hero">
        <img className="fd-assistant-art" src="/assets/assistant/assistant-accent.png" alt="" aria-hidden="true" />
        <p className="fd-assistant-eyebrow">AI ASSISTANT</p>
        <h1 id="fd-assistant-title">Ask FirstDay AI <Sparkle aria-hidden="true" /></h1>
        <p className="fd-assistant-description">{props.project ? `Your onboarding assistant for ${props.project.name}.` : props.internship ? 'Your personal internship assistant at NovaTech.' : 'Your assistant for company resources at NovaTech.'}</p>
        <div className="fd-assistant-toolbar">
          <button className="fd-assistant-new" type="button" disabled={busy} onClick={newChat}><Plus size={21} />New chat</button>
          <div className="fd-assistant-history">
            <select aria-label="Conversation history" value={props.conversationId ?? ''} disabled={busy} onChange={event => {
              followLatest.current = true;
              if (event.target.value) props.onOpenConversation(event.target.value);
              else newChat();
            }}>
              <option value="">New conversation</option>
              {props.conversations.map(item => <option value={item.id} key={item.id}>{item.title}</option>)}
            </select>
            <ChevronDown size={21} aria-hidden="true" />
          </div>
          {props.conversationId && <button className="fd-assistant-delete" type="button" aria-label="Delete current conversation" title="Delete conversation" disabled={busy} onClick={props.onDeleteConversation}><Trash2 size={19} /></button>}
        </div>
        {props.historyLoading && <p className="fd-assistant-history-status" role="status">Loading history…</p>}
        {props.historyError && <p className="fd-assistant-error" role="alert">{props.historyError}</p>}
      </header>

      <div className="fd-assistant-panel">
        <div className="fd-assistant-log" ref={log} role="log" tabIndex={0} aria-label="Conversation" aria-live="polite" aria-relevant="additions text" onScroll={() => {
          if (log.current) followLatest.current = log.current.scrollHeight - log.current.scrollTop - log.current.clientHeight < 96;
        }}>
          {props.messages.map((message, index) => <div key={index} className={`fd-assistant-message ${message.role === 'user' ? 'fd-assistant-user' : 'fd-assistant-answer'}`}>
            {message.role === 'assistant' ? <>
              <AIMessage content={message.content} sources={[]} pending={props.loading && index === props.messages.length - 1} onOpenSource={props.onOpenSource} />
              {props.renderResources(message.sources ?? [])}
            </> : <article className="fd-assistant-user-bubble"><strong>You</strong><p>{message.content}</p></article>}
          </div>)}
          {props.chatError && <p className="fd-assistant-error" role="alert">{props.chatError}</p>}
        </div>
        <div className="fd-assistant-bottom">
          <details className="fd-assistant-prompts" open={!hasConversation}>
            <summary>Try asking <ChevronDown size={14} aria-hidden="true" /></summary>
            <div className="fd-assistant-suggestions"><div>
            {suggestions.map(item => <button type="button" key={item.text} disabled={busy} onClick={() => { followLatest.current = true; props.onAsk(item.text); }}><item.icon size={22} strokeWidth={1.8} /><span>{item.text}</span></button>)}
            </div></div>
          </details>
          <form className="fd-assistant-composer" onSubmit={event => { followLatest.current = true; props.onSubmit(event); }}>
            <input value={props.question} onChange={event => props.onQuestionChange(event.target.value)} placeholder={props.project ? `Ask anything about ${props.project.name}...` : props.internship ? 'Ask anything about your internship at NovaTech...' : 'Ask anything about NovaTech...'} aria-label="Chat question" />
            <button type="submit" disabled={busy || !props.question.trim()} aria-label="Send"><Send size={25} strokeWidth={1.8} /></button>
          </form>
        </div>
      </div>
    </section>
  );
}
