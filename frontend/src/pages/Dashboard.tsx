import { useEffect, useState, type FormEvent } from 'react';
import {
  ArrowRight, ArrowUp, BookOpen, Check, ChevronRight, ClipboardList, CodeXml,
  CornerDownLeft, FileText, Headphones, Layers3, Orbit, Sparkle, Target, Users,
} from 'lucide-react';
import type { CurrentUser } from '../services/auth';
import { apiFetch } from '../services/auth';
import type { Project } from '../components/ProjectSwitcher';
import type { OnboardingPlan } from './MyOnboarding';
import useDashboardMotion from '../hooks/useDashboardMotion';
import './Dashboard.css';

type Destination = 'assistant' | 'onboarding' | 'knowledge' | 'access';
interface ProjectTask {
  id: string; title: string; stage: string; status: 'TODO' | 'IN_PROGRESS' | 'BLOCKED' | 'DONE';
}
interface ProjectPlan {
  tasks: ProjectTask[]; completed_tasks: number; total_tasks: number; progress_percent: number; project_role: string;
}
interface Props {
  user: CurrentUser; project: Project | null; companyPlan: OnboardingPlan | null;
  availableProject: Project | null; onOpenProject: (project: Project) => void;
  companyLoading: boolean; companyError: string; companySaving: string | null;
  onToggleCompany: (id: string, status: 'TODO' | 'IN_PROGRESS' | 'DONE') => void;
  onNavigate: (destination: Destination) => void; onAsk: (question: string) => void;
  onRefreshAccess: () => void;
}
const art = '/assets/dashboard/';
const projectRoles: Record<string, string> = { ENGINEERING: 'Engineering', QA: 'QA', DEVOPS: 'DevOps / SRE', GENERAL: 'General track' };

export default function Dashboard({ user, project, companyPlan, companyLoading, companyError, companySaving,
  onToggleCompany, onNavigate, onAsk, onRefreshAccess, availableProject, onOpenProject }: Props) {
  const [question, setQuestion] = useState('');
  const [projectPlan, setProjectPlan] = useState<ProjectPlan | null>(null);
  const [projectLoading, setProjectLoading] = useState(Boolean(project));
  const [projectError, setProjectError] = useState('');
  const [projectSaving, setProjectSaving] = useState<string | null>(null);
  const [notice, setNotice] = useState('');
  const motionRoot = useDashboardMotion();
  useEffect(() => {
    if (!project) return;
    const controller = new AbortController();
    setProjectLoading(true); setProjectPlan(null); setProjectError('');
    apiFetch(`/api/v1/projects/${project.id}/onboarding`, { signal: controller.signal })
      .then(async response => {
        if (!response.ok) throw new Error('Не удалось загрузить маршрут проекта. Обновите доступы и повторите.');
        return await response.json() as ProjectPlan;
      }).then(plan => { if (!controller.signal.aborted) setProjectPlan(plan); })
      .catch(reason => { if (!controller.signal.aborted) setProjectError(reason instanceof Error ? reason.message : 'Маршрут недоступен.'); })
      .finally(() => { if (!controller.signal.aborted) setProjectLoading(false); });
    return () => controller.abort();
  }, [project?.id]);

  const isCompany = !project && user.onboarding_mode === 'COMPANY';
  const waiting = !project && !isCompany;
  const plan = project ? projectPlan : isCompany ? companyPlan : null;
  const loading = project ? projectLoading : isCompany && companyLoading;
  const error = project ? projectError : isCompany ? companyError : '';
  const saving = project ? projectSaving : companySaving;
  const tasks = project ? projectPlan?.tasks ?? [] : isCompany ? companyPlan?.tasks ?? [] : [];
  const remaining = tasks.filter(task => task.status !== 'DONE');
  const visibleTasks = [...remaining, ...tasks.filter(task => task.status === 'DONE')].slice(0, 4);
  const stages = project || waiting ? [
    { label: 'Understand the project', id: 'CONTEXT' }, { label: 'Prepare your environment', id: 'ENVIRONMENT' }, { label: 'Make your first contribution', id: 'DELIVERY' },
  ].map(stage => {
    const items = projectPlan?.tasks.filter(task => task.stage === stage.id) ?? [];
    return { label: stage.label, total: items.length, completed: items.filter(task => task.status === 'DONE').length };
  }) : [1, 2, 3, 4].map(week => {
    const items = isCompany ? companyPlan?.tasks.filter(task => task.week === week) ?? [] : [];
    return { label: `Week ${week}`, total: items.length, completed: items.filter(task => task.status === 'DONE').length };
  });
  const firstIncomplete = stages.findIndex(stage => stage.total > stage.completed);
  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning.' : hour < 18 ? 'Good afternoon.' : 'Good evening.';

  function submit(event: FormEvent) {
    event.preventDefault();
    if (question.trim()) onAsk(question.trim());
  }
  async function toggleTask(task: typeof tasks[number]) {
    if (saving) return;
    if (!project) {
      onToggleCompany(task.id, task.status as 'TODO' | 'IN_PROGRESS' | 'DONE');
      return;
    }
    setProjectSaving(task.id); setProjectError('');
    try {
      const response = await apiFetch(`/api/v1/projects/${project.id}/onboarding/tasks/${task.id}`, {
        method: 'PATCH', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: task.status === 'DONE' ? 'TODO' : 'DONE' }),
      });
      if (!response.ok) {
        if (response.status === 404 || response.status === 403) { setProjectPlan(null); onRefreshAccess(); }
        throw new Error('Не удалось сохранить задачу. Проверьте доступ и повторите.');
      }
      setProjectPlan(await response.json() as ProjectPlan);
    } catch (reason) { setProjectError(reason instanceof Error ? reason.message : 'Ошибка сохранения.'); }
    finally { setProjectSaving(null); }
  }

  return <div className="fd-dashboard" ref={motionRoot}>
    <div className="fd-dashboard-grid">
      <div className="fd-dashboard-main">
        <section className="fd-dash-hero" aria-labelledby="dashboard-greeting">
          <div className="fd-dash-orb-motion" data-reveal="fade" data-reveal-delay="100"><img className="fd-dash-orb" src={`${art}ai-orb.png`} alt="" /></div>
          <div className="fd-dash-greeting" data-reveal>
            <p className="fd-dash-eyebrow">{greeting}</p>
            <h1 id="dashboard-greeting">{user.full_name.trim().split(/\s+/)[0]}<span>.</span></h1>
            <h2>{isCompany ? 'Your internship assistant is here.' : 'Your project onboarding assistant is here.'}</h2>
            <p className="fd-dash-intro">{project
              ? `Your next chapter starts with ${project.name}. Explore your project, find your next task, and ask anything along the way.`
              : waiting ? availableProject ? 'Your next project starts with a clear plan. Choose your assigned project to explore its documentation, tasks, and next steps.'
                : 'Your next project starts with a clear plan. Once your administrator assigns a project, your tasks and documentation will appear here.'
              : "Let’s make your first days at NovaTech smooth, productive and exciting. Ask me anything, explore your tasks, or discover what’s next."}</p>
          </div>
          <div className="fd-dash-powered"><span>AI</span><small>Powered by<br /><strong>NovaTech</strong></small></div>
          <div className="fd-dash-journey">Your journey<br />starts here<CornerDownLeft size={84} strokeWidth={0.7} aria-hidden="true" /></div>
          <form className="fd-dash-ask" onSubmit={submit} data-reveal data-reveal-delay="120">
            <span className="fd-dash-ask-icon"><Sparkle size={25} strokeWidth={1.8} /></span>
            <input aria-label="Ask FirstDay anything" placeholder="Ask FirstDay anything..." value={question} onChange={event => setQuestion(event.target.value)} />
            <button type="submit" aria-label="Send message" disabled={!question.trim()}><ArrowRight size={25} strokeWidth={1.7} /></button>
          </form>
          <nav className="fd-dash-shortcuts" aria-label="Onboarding shortcuts" data-reveal data-reveal-delay="200">
            <button onClick={() => onNavigate('onboarding')}><Orbit size={16} />{isCompany ? 'Internship tasks' : 'Onboarding tasks'}</button>
            <button onClick={() => onAsk(project ? 'Какой технологический стек используется в проекте?' : 'Какой технологический стек используется в NovaTech?')}><Orbit size={16} />Tech stack</button>
            <button onClick={() => setNotice('Your team · каталог команды появится здесь. Пока информацию об ответственных можно найти в Knowledge Hub или спросить у FirstDay AI.')}><Users size={16} />Your team</button>
            <button onClick={() => onAsk('Как устроен процесс работы и согласования изменений?')}><FileText size={16} />Processes</button>
          </nav>
        </section>

        <div className="fd-dash-bottom">
          <article className="fd-dash-welcome" data-reveal>
            <div className="fd-dash-welcome-copy"><span className="fd-dash-new">New start</span>
              <h2>Welcome to<br />{project?.name ?? 'NovaTech'}
                <button type="button" aria-label="Explore welcome materials" onClick={() => onNavigate('knowledge')}><ArrowRight size={22} /></button>
              </h2><p>Innovative technology.<br />Real impact.</p>
              <div className="fd-dash-slide-dots" aria-hidden="true"><i /><i /><i /></div>
            </div>
          </article>

          <section className="fd-dash-card fd-dash-tasks" aria-labelledby="dashboard-tasks" data-reveal data-reveal-delay="70">
            <header><h2 id="dashboard-tasks"><ClipboardList size={19} />{isCompany ? 'Your internship tasks' : 'Your onboarding tasks'}</h2>
              <button className="fd-dash-view-all" onClick={() => onNavigate('onboarding')}>View all{plan ? ` (${plan.total_tasks})` : ''}<ArrowRight size={13} /></button>
            </header>
            {error && <div className="fd-dash-error" role="alert">{error}<button onClick={onRefreshAccess}>Обновить доступы</button></div>}
            {loading && <div className="fd-dash-skeleton" role="status" aria-label="Loading your tasks"><i /><i /><i /><i /></div>}
            {!loading && !error && !visibleTasks.length && <div className="fd-dash-empty">
              <Orbit size={27} /><strong>{waiting ? availableProject ? 'Выберите назначенный проект' : 'Ожидаем назначения проекта' : 'Пока нет задач'}</strong>
              <p>{waiting ? availableProject ? 'Проекты доступны в списке шапки. Откройте свой проект, чтобы увидеть его задачи и прогресс.'
                : 'Администратор назначит вам проект. Пока можно изучить общие материалы или создать заявку на доступ.' : 'Ваши задачи появятся здесь, когда будет готов персональный маршрут.'}</p>
              <button onClick={waiting ? availableProject ? () => onOpenProject(availableProject) : onRefreshAccess : () => onNavigate('onboarding')}>
                {waiting ? availableProject ? `Открыть ${availableProject.name}` : 'Обновить доступы' : 'Открыть маршрут'}<ArrowRight size={14} /></button>
            </div>}
            {!loading && visibleTasks.map((task, index) => <div className={`fd-dash-task ${index === 0 ? 'is-current' : ''}`} key={task.id}>
              <div className="fd-dash-task-line">
                <button className={`fd-dash-checkbox ${task.status === 'DONE' ? 'is-checked' : ''}`} disabled={saving !== null}
                  aria-label={`${task.status === 'DONE' ? 'Отменить выполнение' : 'Выполнить'}: ${task.title}`} aria-pressed={task.status === 'DONE'} onClick={() => void toggleTask(task)}>
                  {task.status === 'DONE' && <Check size={13} strokeWidth={2.5} />}
                </button>
                <button className="fd-dash-task-title" onClick={() => onNavigate('onboarding')}>{task.title}{task.status === 'BLOCKED' && <small>Есть блокер</small>}<ChevronRight size={15} /></button>
              </div>
              {index === 0 && <button className="fd-dash-task-summary" onClick={() => onNavigate('onboarding')}>{plan?.completed_tasks} of {plan?.total_tasks} complete · View all tasks</button>}
            </div>)}
          </section>

          <section className="fd-dash-card fd-dash-stages" aria-labelledby="dashboard-stages" data-reveal data-reveal-delay="140">
            <header><h2 id="dashboard-stages"><Layers3 size={20} />{isCompany ? 'Internship stages' : 'Onboarding stages'}</h2></header>
            <ol>{stages.map((stage, index) => {
              const done = stage.total > 0 && stage.total === stage.completed;
              return <li key={stage.label} className={`${done ? 'is-done' : ''} ${firstIncomplete === index ? 'is-active' : ''}`}>
                <span className="fd-dash-stage-node" aria-hidden="true">{done && <Check size={12} strokeWidth={3} />}</span>
                <button onClick={() => onNavigate('onboarding')}><span><strong>{stage.label}</strong><small>{plan ? `${stage.completed} of ${stage.total} tasks completed` : 'Your route starts here'}</small></span>
                  {firstIncomplete === index && plan ? <em>In progress</em> : <ChevronRight size={16} />}</button>
              </li>;
            })}</ol>
          </section>
        </div>
      </div>

      <aside className="fd-dashboard-rail" aria-label="Your onboarding at a glance">
        <section className={`fd-dash-progress ${project ? 'is-project' : ''}`} aria-labelledby="dashboard-progress" data-reveal data-reveal-delay="70">
          <h2 id="dashboard-progress"><Sparkle size={19} />{isCompany ? 'Your internship progress' : 'Your onboarding progress'}</h2>
          <div className="fd-dash-progress-number"><strong>{plan ? `${plan.progress_percent}%` : '—'}</strong><span><ArrowUp size={17} /></span><small>{project || waiting ? 'Project onboarding' : 'Your personal plan'}</small></div>
          <progress aria-label="Onboarding progress" max={100} value={plan?.progress_percent ?? 0} />
          <div className="fd-dash-progress-foot"><span>{plan ? `${plan.completed_tasks} of ${plan.total_tasks} tasks completed` : waiting ? availableProject ? 'Select your assigned project' : 'Waiting for project assignment' : loading ? 'Loading your progress…' : 'Progress unavailable'}</span><span>{project ? projectRoles[projectPlan?.project_role ?? ''] ?? '' : user.job_title}</span></div>
        </section>

        <section className="fd-dash-recommendations" aria-labelledby="dashboard-recommendations" data-reveal data-reveal-delay="140">
          <h2 id="dashboard-recommendations"><Sparkle size={20} />AI recommendations</h2>
          <button onClick={() => onNavigate('knowledge')}><span className="fd-dash-rec-icon"><BookOpen size={27} /></span><span><strong>{project ? `${project.name} 101` : 'NovaTech 101'}</strong><small>A quick overview of your mission,<br />team, and how we work at NovaTech.</small></span><ChevronRight size={18} /></button>
          <button onClick={() => onNavigate('onboarding')}><span className="fd-dash-rec-icon is-target"><Target size={29} /></span><span><strong>Your role &amp; impact</strong><small>Explore your {isCompany ? 'internship' : 'onboarding'} plan,<br />key goals, and early milestones.</small></span><ChevronRight size={18} /></button>
        </section>

        <section className="fd-dash-card fd-dash-quick" aria-labelledby="dashboard-quick" data-reveal data-reveal-delay="100"><h2 id="dashboard-quick">Quick access</h2>
          <div><button onClick={() => onAsk('Какой технологический стек используется?')}><span><CodeXml size={22} /></span>Tech Stack</button>
            <button onClick={() => onNavigate('knowledge')}><span><BookOpen size={22} /></span>Docs</button>
            <button onClick={() => onNavigate('access')}><span><Headphones size={22} /></span>Support</button>
            <button onClick={() => setNotice('Community · пространство сообщества ещё в разработке.')}><span><Users size={21} /></span>Community</button></div>
        </section>
        <figure className="fd-dash-quote" data-reveal data-reveal-delay="180"><blockquote>“The best way to predict<br />the future is to build it.”</blockquote><figcaption>— NovaTech <ArrowRight size={12} /></figcaption></figure>
      </aside>
    </div>
    {notice && <div className="fd-dash-notice" role="status"><p>{notice}</p><button onClick={() => setNotice('')}>Понятно</button></div>}
    <footer className="fd-dash-footer"><span /><button onClick={() => onNavigate('knowledge')}>Build</button><i>·</i><button onClick={() => onNavigate('onboarding')}>Learn</button><i>·</i><button onClick={() => onNavigate('assistant')}>Grow</button><ArrowRight size={27} strokeWidth={1} /></footer>
  </div>;
}
