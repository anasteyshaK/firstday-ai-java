
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import {
  Bell,
  BookOpen,
  ChevronRight,
  ExternalLink,
  LayoutDashboard,
  House,
  Settings,
  Search,
  ChevronDown,
  Activity,
  PanelLeftClose,
  ShieldCheck,
  Sparkle,
  X,
} from 'lucide-react';

import { type AISource } from './components/AIMessage';
import DocumentModal from './components/DocumentModal';
import OnboardingHero from './components/OnboardingHero';
import KnowledgeHub from './pages/KnowledgeHub';
import MyOnboarding, {
  type OnboardingPlan,
} from './pages/MyOnboarding';
import AccessCenter from './pages/AccessCenter';
import LoginPage from './pages/LoginPage';
import AdminPanel from './pages/AdminPanel';
import ProjectSwitcher, { type Project } from './components/ProjectSwitcher';
import ProjectOnboarding from './pages/ProjectOnboarding';
import Dashboard from './pages/Dashboard';
import AssistantPage from './pages/AssistantPage';
import './reference-shell.css';
import {
  apiFetch,
  getMe,
  logout,
  type CurrentUser,
} from './services/auth';

type Page =
  | 'dashboard'
  | 'assistant'
  | 'onboarding'
  | 'knowledge'
  | 'access'
  | 'admin';

type ChatMessage = {
  role: 'user' | 'assistant';
  content: string;
  sources?: AISource[];
};

type ConversationSummary = {
  id: string;
  title: string;
  created_at: string;
  updated_at: string;
};

type ConversationDetail = {
  id: string;
  title: string;
  messages: ChatMessage[];
};

type StreamEvent = {
  type: 'status' | 'delta' | 'done' | 'error';
  text?: string;
  message?: string;
  conversation_id?: string;
  sources?: AISource[];
};

const welcomeMessage: ChatMessage = {
  role: 'assistant',
  content:
    'Hi! I’m FirstDay AI. I can help you navigate your NovaTech onboarding. Ask me about your first day, development tools, VPN, or company policies.',
};

const menu: {
  id: Page;
  label: string;
  icon: typeof LayoutDashboard;
}[] = [
  {
    id: 'dashboard',
    label: 'Dashboard',
    icon: House,
  },
  {
    id: 'assistant',
    label: 'AI Assistant',
    icon: Sparkle,
  },
  {
    id: 'onboarding',
    label: 'My Onboarding',
    icon: BookOpen,
  },
  {
    id: 'knowledge',
    label: 'Knowledge Hub',
    icon: BookOpen,
  },
  {
    id: 'access',
    label: 'Access Center',
    icon: ShieldCheck,
  },
  {
    id: 'admin',
    label: 'Admin Panel',
    icon: Settings,
  },
];

function Logo() {
  return <div className="fd-nova-brand">
    <img src="/assets/dashboard/novatech-mark.png" alt="" />
    <span><strong>NovaTech</strong><small>FirstDay AI</small></span>
  </div>;
}

/**
 * Normalizes sources returned by the backend.
 * The stream and saved conversation use the same source format.
 */
function normalizeSources(value: unknown): AISource[] {
  if (!Array.isArray(value)) {
    return [];
  }

  const uniquePaths = new Set<string>();
  const normalized: AISource[] = [];

  for (const item of value) {
    if (
      !item ||
      typeof item !== 'object'
    ) {
      continue;
    }

    const source = item as Record<string, unknown>;

    if (
      typeof source.file_path !== 'string' ||
      !source.file_path.trim()
    ) {
      continue;
    }

    const path = source.file_path.trim();

    if (uniquePaths.has(path)) {
      continue;
    }

    uniquePaths.add(path);

    normalized.push({
      ...source,
      reference:
        typeof source.reference === 'number'
          ? source.reference
          : normalized.length + 1,
      title:
        typeof source.title === 'string' &&
        source.title.trim()
          ? source.title
          : path.split('/').pop() ?? path,
      file_path: path,
    } as AISource);
  }

  return normalized;
}

/**
 * Related Resources.
 *
 * Rendered separately from AIMessage so that sources remain
 * visible regardless of AIMessage's internal rendering logic.
 */
function RelatedResources({
  sources,
  onOpenSource,
}: {
  sources: AISource[];
  onOpenSource: (source: AISource) => void;
}) {
  const items = normalizeSources(sources);

  if (items.length === 0) {
    return null;
  }

  return (
    <section className="fd-assistant-resources" aria-label="Related resources">
      <div className="fd-assistant-resources-title"><BookOpen size={16} />Related resources <span>({items.length})</span></div>
      <div className="fd-assistant-resource-grid">{items.map((source, index) => (
        <button key={source.file_path} type="button" onClick={() => onOpenSource(source)} title={`Open ${source.title}`}>
          <span className="fd-assistant-resource-icon"><BookOpen size={19} /></span>
          <span className="fd-assistant-resource-text"><strong>[{source.reference ?? index + 1}] {source.title}</strong><small>{source.file_path}</small></span>
          <ExternalLink size={15} />
        </button>
      ))}</div>
    </section>
  );
}

export default function App() {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    let active = true;

    getMe()
      .then((found) => {
        if (active) {
          setUser(found);
        }
      })
      .catch(() => {
        if (active) {
          setUser(null);
        }
      })
      .finally(() => {
        if (active) {
          setChecking(false);
        }
      });

    const expired = () => setUser(null);

    window.addEventListener(
      'firstday:session-expired',
      expired
    );

    return () => {
      active = false;

      window.removeEventListener(
        'firstday:session-expired',
        expired
      );
    };
  }, []);

  if (checking) {
    return (
      <div
        style={{
          padding: 40,
          color: '#fff',
          background: '#10132b',
          minHeight: '100vh',
        }}
      >
        Loading FirstDay AI…
      </div>
    );
  }

  if (!user) {
    return <LoginPage onLogin={setUser} />;
  }

  return (
    <Workspace
      key={user.id}
      user={user}
      onLogout={() => {
        void logout().finally(() => setUser(null));
      }}
    />
  );
}

function Workspace({
  user,
  onLogout,
}: {
  user: CurrentUser;
  onLogout: () => void;
}) {
  const [profile, setProfile] = useState(user);
  const [projects, setProjects] = useState<Project[]>([]);
  const [selection, setSelection] = useState<string | null | undefined>(undefined);
  const [ready, setReady] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [accessError, setAccessError] = useState('');
  const version = useRef(0);
  const mode = useRef(user.onboarding_mode);
  const refreshAccess = useCallback(async () => {
    const request = ++version.current;
    setRefreshing(true);
    try {
      const [response, latest] = await Promise.all([apiFetch('/api/v1/projects'), getMe()]);
      if (!response.ok || !latest) throw new Error('Не удалось проверить доступы. Повторите обновление.');
      const available = await response.json() as Project[];
      if (request !== version.current) return;
      const changedMode = mode.current !== latest.onboarding_mode;
      mode.current = latest.onboarding_mode;
      setProfile(latest); setProjects(available);
      setSelection(previous => {
        if (previous === undefined || changedMode) return latest.onboarding_mode === 'PROJECT' ? available[0]?.id ?? null : null;
        return previous && !available.some(item => item.id === previous) ? null : previous;
      });
      setAccessError(''); setReady(true);
    } catch (reason) {
      if (request !== version.current) return;
      setProjects([]); setSelection(undefined); setReady(false);
      setAccessError(reason instanceof Error ? reason.message : 'Доступы недоступны.');
    } finally { if (request === version.current) setRefreshing(false); }
  }, []);
  useEffect(() => {
    const refresh = () => { void refreshAccess(); };
    refresh();
    window.addEventListener('focus', refresh);
    window.addEventListener('firstday:projects-updated', refresh);
    const timer = window.setInterval(refresh, 30000);
    return () => {
      version.current += 1;
      window.clearInterval(timer);
      window.removeEventListener('focus', refresh);
      window.removeEventListener('firstday:projects-updated', refresh);
    };
  }, [refreshAccess]);
  if (!ready) return <main className="fd-auth-page"><section className="fd-auth-card" role="status">
    <h1>FirstDay AI</h1><p>{accessError || 'Проверяем назначенные вам проекты…'}</p>
    {accessError && <button className="fd-auth-submit" onClick={() => void refreshAccess()} disabled={refreshing}>Повторить</button>}
    <button className="fd-auth-switch" onClick={onLogout}>Выйти</button>
  </section></main>;
  const visibleProjects = profile.onboarding_mode === 'PROJECT' ? projects : [];
  const project = visibleProjects.find(item => item.id === selection) ?? null;
  return <WorkspaceContent key={`${project?.id ?? 'company'}-${profile.onboarding_mode}`} user={profile} onLogout={onLogout}
    project={project} projects={visibleProjects} onProjectChange={next => setSelection(next?.id ?? null)}
    onRefreshAccess={() => void refreshAccess()} refreshingAccess={refreshing} accessError={accessError} />;
}

function WorkspaceContent({ user, onLogout, project, projects, onProjectChange, onRefreshAccess, refreshingAccess, accessError }: {
  user: CurrentUser; onLogout: () => void; project: Project | null;
  projects: Project[]; onRefreshAccess: () => void; refreshingAccess: boolean; accessError: string;
  onProjectChange: (project: Project | null) => void;
}) {
  const isInternship = !project && user.onboarding_mode === 'COMPANY';
  const planLabel = isInternship ? 'My Internship' : 'Project Onboarding';
  const currentWelcome: ChatMessage = project ? {
    role: 'assistant',
    content: `I’ll help you get started with ${project.name}: explore the documentation, prepare your environment, and find your next step. Ask me about the project or your onboarding plan.`,
  } : isInternship ? { ...welcomeMessage, content: 'Hi! I’m FirstDay AI. I can help you follow your NovaTech internship plan. Ask me about your learning tasks, progress, tools, or company resources.' } : welcomeMessage;
  const [page, setPage] = useState<Page>('dashboard');

  const [onboardingPlan, setOnboardingPlan] =
    useState<OnboardingPlan | null>(null);

  const [sidebar, setSidebar] = useState(false);

  const [openSource, setOpenSource] =
    useState<AISource | null>(null);

  const [planLoading, setPlanLoading] = useState(true);
  const [planError, setPlanError] = useState('');
  const [taskSaving, setTaskSaving] =
    useState<string | null>(null);

  const [chat, setChat] = useState<ChatMessage[]>([
    currentWelcome,
  ]);

  const [question, setQuestion] = useState('');
  const [loading, setLoading] = useState(false);
  const [chatError, setChatError] = useState('');

  const [conversationId, setConversationId] =
    useState<string | null>(null);

  const [conversations, setConversations] =
    useState<ConversationSummary[]>([]);

  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState('');

  async function loadConversations() {
    try {
      const response = await apiFetch(
        `/api/v1/chat/conversations${project ? `?project_id=${project.id}` : ''}`
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const data =
        (await response.json()) as ConversationSummary[];

      setConversations(data);
      setHistoryError('');
    } catch (error) {
      setHistoryError(
        error instanceof Error
          ? error.message
          : 'History unavailable'
      );
    }
  }

  const historyKey = 'firstday:conversation:' + user.id + ':' + user.onboarding_mode + ':' + (project?.id ?? 'company');

  function rememberConversation(id: string | null) {
    try {
      if (id) sessionStorage.setItem(historyKey, id);
      else sessionStorage.removeItem(historyKey);
    } catch {
      // Server history remains available if browser storage is disabled.
    }
  }

  useEffect(() => {
    void loadConversations();
    try {
      const remembered = sessionStorage.getItem(historyKey);
      if (remembered) void openConversation(remembered);
    } catch {
      // The conversation list still works without browser storage.
    }
  }, [historyKey]);

  useEffect(() => {
    if (conversationId) rememberConversation(conversationId);
  }, [conversationId, historyKey]);

  function newConversation() {
    if (loading || historyLoading) {
      return;
    }

    rememberConversation(null);
    setConversationId(null);
    setChat([currentWelcome]);
    setChatError('');
    setQuestion('');
  }

  async function openConversation(id: string) {
    if (loading || historyLoading) {
      return;
    }

    setHistoryLoading(true);
    setChatError('');
    setHistoryError('');

    try {
      const response = await apiFetch(
        `/api/v1/chat/conversations/${id}`
      );

      if (!response.ok) {
        if (response.status === 403 || response.status === 404) rememberConversation(null);
        throw new Error(`HTTP ${response.status}`);
      }

      const data =
        (await response.json()) as ConversationDetail;

      const restoredMessages: ChatMessage[] =
        data.messages.map((message) => ({
          role: message.role,
          content: message.content,
          sources: normalizeSources(message.sources),
        }));

      setConversationId(id);

      setChat(
        restoredMessages.length > 0
          ? restoredMessages
          : [currentWelcome]
      );
    } catch (error) {
      setHistoryError(
        error instanceof Error
          ? error.message
          : 'Could not load conversation'
      );
    } finally {
      setHistoryLoading(false);
    }
  }

  async function deleteConversation(id: string) {
    if (
      loading ||
      historyLoading ||
      !window.confirm(
        'Delete this conversation and its messages?'
      )
    ) {
      return;
    }

    setHistoryLoading(true);
    setHistoryError('');

    try {
      const response = await apiFetch(
        `/api/v1/chat/conversations/${id}`,
        { method: 'DELETE' }
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      if (conversationId === id) {
        rememberConversation(null);
        setConversationId(null);
        setChat([currentWelcome]);
      }

      await loadConversations();
    } catch (error) {
      setHistoryError(
        error instanceof Error
          ? error.message
          : 'Could not delete conversation'
      );
    } finally {
      setHistoryLoading(false);
    }
  }

  useEffect(() => {
    let active = true;

    if (project || user.onboarding_mode === 'PROJECT') {
      setPlanLoading(false);
      return;
    }

    setOnboardingPlan(null);
    setPlanLoading(true);
    setPlanError('');

    async function loadPlan() {
      try {
        const response = await apiFetch('/api/v1/onboarding');

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }

        const plan =
          (await response.json()) as OnboardingPlan;

        if (active) {
          setOnboardingPlan(plan);
        }
      } catch (error) {
        if (active) {
          setPlanError(
            error instanceof Error
              ? error.message
              : 'Plan unavailable'
          );
        }
      } finally {
        if (active) {
          setPlanLoading(false);
        }
      }
    }

    void loadPlan();

    return () => {
      active = false;
    };
  }, [user.id, project?.id, user.onboarding_mode]);

  async function toggleDashboardTask(
    taskId: string,
    status: 'TODO' | 'IN_PROGRESS' | 'DONE'
  ) {
    if (taskSaving || !onboardingPlan) {
      return;
    }

    setTaskSaving(taskId);
    setPlanError('');

    try {
      const response = await apiFetch(
        `/api/v1/onboarding/tasks/${taskId}`,
        {
          method: 'PATCH',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            status: status === 'DONE' ? 'TODO' : 'DONE',
          }),
        }
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      setOnboardingPlan(
        (await response.json()) as OnboardingPlan
      );
    } catch (error) {
      setPlanError(
        error instanceof Error
          ? error.message
          : 'Could not update task'
      );
    } finally {
      setTaskSaving(null);
    }
  }

  /**
   * Streaming chat.
   *
   * delta -> append text to the assistant message
   * done  -> attach sources to the SAME assistant message
   */
  async function sendMessage(
    e?: FormEvent,
    suggested?: string
  ) {
    e?.preventDefault();

    const value = (suggested ?? question).trim();

    if (!value || loading || historyLoading) {
      return;
    }

    setPage('assistant');
    setQuestion('');
    setChatError('');

    setChat((current) => [
      ...current,
      {
        role: 'user',
        content: value,
      },
      {
        role: 'assistant',
        content: '',
        sources: [],
      },
    ]);

    setLoading(true);

    let complete = false;

    try {
      const response = await apiFetch(
        '/api/v1/chat/stream',
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            question: value,
            conversation_id: conversationId,
            project_id: project?.id ?? null,
          }),
        }
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      if (!response.body) {
        throw new Error(
          'Streaming is not supported by this browser'
        );
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();

      let buffer = '';

      function processLine(line: string) {
        if (!line.trim()) {
          return;
        }

        const event = JSON.parse(line) as StreamEvent;

        if (event.type === 'status') {
          return;
        }

        if (event.type === 'delta') {
          const newText = event.text ?? '';

          setChat((current) =>
            current.map((message, index) => {
              if (
                index !== current.length - 1 ||
                message.role !== 'assistant'
              ) {
                return message;
              }

              return {
                ...message,
                content: message.content + newText,
              };
            })
          );

          return;
        }

        if (event.type === 'done') {
          complete = true;

          if (event.conversation_id) {
            setConversationId(event.conversation_id);
          }

          const receivedSources = normalizeSources(
            event.sources
          );

          setChat((current) =>
            current.map((message, index) => {
              if (
                index !== current.length - 1 ||
                message.role !== 'assistant'
              ) {
                return message;
              }

              return {
                ...message,
                sources: receivedSources,
              };
            })
          );

          void loadConversations();

          return;
        }

        if (event.type === 'error') {
          throw new Error(
            event.message ?? 'Chat request failed'
          );
        }
      }

      try {
        while (true) {
          const { value: chunk, done } =
            await reader.read();

          if (done) {
            break;
          }

          buffer += decoder.decode(chunk, {
            stream: true,
          });

          const lines = buffer.split('\n');

          buffer = lines.pop() ?? '';

          for (const line of lines) {
            processLine(line);
          }
        }

        buffer += decoder.decode();

        if (buffer.trim()) {
          processLine(buffer);
        }

        if (!complete) {
          throw new Error(
            'Connection closed before answer finished'
          );
        }
      } finally {
        reader.releaseLock();
      }
    } catch (error) {
      setChatError(
        error instanceof Error
          ? `Не удалось получить ответ: ${error.message}`
          : 'Не удалось подключиться к API.'
      );

      setChat((current) => {
        const last = current[current.length - 1];

        if (
          last?.role === 'assistant' &&
          !last.content
        ) {
          return current.slice(0, -1);
        }

        return current;
      });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className={`app fd-reference-shell ${page === 'dashboard' ? 'is-dashboard' : page === 'assistant' ? 'is-assistant' : page === 'onboarding' ? 'is-onboarding' : page === 'knowledge' ? 'is-knowledge' : page === 'access' ? 'is-access' : ''}`}>
      <div className="ambient ambient-a" />
      <div className="ambient ambient-b" />

      <div className="app-frame">
        <a className="fd-skip-link" href="#workspace-content">Skip to content</a>
        <aside className={`sidebar ${sidebar ? 'sidebar-open' : ''}`} aria-label="Main navigation">
          <div className="sidebar-top"><Logo />
            <button className="mobile-close icon-button" onClick={() => setSidebar(false)} aria-label="Close navigation"><X size={21} /></button>
          </div>
          <nav className="nav">{menu.filter(item => item.id !== 'admin' || user.role === 'ADMIN').map(item => (
            <button type="button" key={item.id} className={`nav-item ${page === item.id ? 'active' : ''}`}
              aria-current={page === item.id ? 'page' : undefined} onClick={() => { setPage(item.id); setSidebar(false); }}>
              <item.icon size={23} strokeWidth={1.65} /><span>{item.id === 'onboarding' ? isInternship ? 'My Internship' : 'My Onboarding' : item.label}</span>
              {page === item.id && <i className="fd-nav-active-dot" aria-hidden="true" />}
            </button>
          ))}</nav>
          <div className="fd-sidebar-quote"><p>“Better<br />questions.<br />Faster growth.”</p><span aria-hidden="true" /></div>
          <div className="fd-sidebar-status"><i aria-hidden="true" />NovaTech <span>Online</span><Activity size={48} strokeWidth={0.9} aria-hidden="true" /></div>
        </aside>
        {sidebar && <button className="fd-sidebar-scrim" aria-label="Close navigation" onClick={() => setSidebar(false)} />}
        <div className="main-shell">
          <header className="topbar">
            <div className="fd-topbar-left">
              <button onClick={() => setSidebar(true)} className="mobile-menu icon-button" aria-label="Open navigation"><PanelLeftClose size={21} /></button>
              <div className="topbar-breadcrumb"><span>NovaTech</span><ChevronRight size={13} />
                <ProjectSwitcher selected={project} projects={projects} user={user} onChange={onProjectChange}
                  onRefresh={onRefreshAccess} refreshing={refreshingAccess} error={accessError} disabled={loading || historyLoading} compact />
                {(page === 'assistant' || page === 'onboarding' || page === 'knowledge') && <><ChevronRight size={16} /><strong className="fd-current-breadcrumb fd-assistant-breadcrumb">{page === 'assistant' ? 'AI Assistant' : page === 'onboarding' ? planLabel : 'Knowledge Hub'}</strong></>}
              </div>
            </div>
            <div className="topbar-right">
              <button className="icon-button fd-header-search" aria-label="Search knowledge" onClick={() => setPage('knowledge')}><Search size={23} strokeWidth={1.7} /></button>
              <details className="fd-header-popover"><summary aria-label="Notifications"><Bell size={23} strokeWidth={1.7} /><i /></summary>
                <div><strong>Notifications</strong><p>Центр уведомлений появится здесь.</p><small>Coming soon</small></div>
              </details>
              <details className="fd-profile-menu fd-header-popover"><summary aria-label="Account menu">
                <span className="avatar">{user.full_name.charAt(0).toUpperCase()}</span><span className="fd-profile-name">{user.full_name.split(/\s+/)[0]}</span><ChevronDown size={15} />
              </summary><div><strong>{user.full_name}</strong><p>{user.employment_type === 'INTERN' ? 'Стажёр' : 'Сотрудник'} · {user.job_title}</p>
                <button type="button" onClick={onRefreshAccess} disabled={refreshingAccess}>Обновить доступы</button>
                <button type="button" onClick={onLogout}>Sign out</button>
              </div></details>
            </div>
          </header>
          <main className="main-content" id="workspace-content">
            {page === 'dashboard' && <Dashboard user={user} project={project} companyPlan={onboardingPlan}
              availableProject={projects[0] ?? null} onOpenProject={onProjectChange}
              companyLoading={planLoading} companyError={planError} companySaving={taskSaving}
              onToggleCompany={(id, status) => { void toggleDashboardTask(id, status); }}
              onNavigate={setPage} onAsk={value => { void sendMessage(undefined, value); }} onRefreshAccess={onRefreshAccess} />}
            {page === 'onboarding' && !project && user.onboarding_mode === 'PROJECT' && (
              <div className="fd-onboarding-page fd-project-waiting-page">
              <OnboardingHero variant="project" description="Your plan will appear when an administrator assigns a project. Company resources and Access Center are already available." />
              <section className="fd-project-page fd-onboarding-waiting" style={{ padding: 28 }}>
                <h2>{projects.length ? 'Choose your assigned project' : 'Waiting for a project assignment'}</h2>
                <p>{projects.length ? 'Select your project in the header to open its plan.' : 'An administrator will add you to the team and assign your project role.'}</p>
                <button className="fd-project-text-button" onClick={() => setPage('access')}>Open Access Center</button>
              </section>
              </div>
            )}

            {page === 'assistant' && <AssistantPage project={project} internship={isInternship} messages={chat}
              conversations={conversations} conversationId={conversationId} question={question}
              onQuestionChange={setQuestion} onSubmit={event => { void sendMessage(event); }}
              onAsk={value => { void sendMessage(undefined, value); }} onNewConversation={newConversation}
              onOpenConversation={id => { void openConversation(id); }}
              onDeleteConversation={() => { if (conversationId) void deleteConversation(conversationId); }}
              loading={loading} historyLoading={historyLoading} historyError={historyError} chatError={chatError}
              onOpenSource={setOpenSource}
              renderResources={sources => <RelatedResources sources={sources} onOpenSource={setOpenSource} />} />}

            {page === 'onboarding' && project && (
              <ProjectOnboarding project={project} onOpenSource={setOpenSource}
                onAsk={value => { void sendMessage(undefined, value); }} />
            )}
            {page === 'onboarding' && !project && user.onboarding_mode === 'COMPANY' && (
              <MyOnboarding
                onPlanChange={setOnboardingPlan}
              />
            )}

            {page === 'knowledge' && (
              <KnowledgeHub
                onOpenSource={setOpenSource}
                project={project}
              />
            )}

            {page === 'access' && (
              <AccessCenter />
            )}

            {page === 'admin' &&
              user.role === 'ADMIN' && (
                <AdminPanel />
              )}
          </main>
        </div>
      </div>

      <DocumentModal
        source={openSource}
        projectId={project?.id}
        onClose={() => setOpenSource(null)}
      />
    </div>
  );
}
