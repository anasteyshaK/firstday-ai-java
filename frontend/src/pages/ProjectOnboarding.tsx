import { useEffect, useState, type FormEvent } from 'react';
import { ArrowRight, BookOpen, CheckCircle2, Circle, AlertCircle, RefreshCw } from 'lucide-react';
import type { Project } from '../components/ProjectSwitcher';
import type { AISource } from '../components/AIMessage';
import { apiFetch } from '../services/auth';
import './ProjectOnboarding.css';
import OnboardingHero from '../components/OnboardingHero';

type Status = 'TODO' | 'IN_PROGRESS' | 'BLOCKED' | 'DONE';
interface Task {
  id: string; position: number; stage: string; title: string; description: string;
  document_path: string | null; status: Status; blocker_note: string;
}
interface Plan {
  project_id: string; project_name: string; project_role: string; tasks: Task[];
  total_tasks: number; completed_tasks: number; blocked_tasks: number; progress_percent: number;
}
const stages = [
  { id: 'CONTEXT', title: 'Understand the project', description: 'Product, components, and ownership' },
  { id: 'ENVIRONMENT', title: 'Prepare your environment', description: 'Access, tools, and local setup' },
  { id: 'DELIVERY', title: 'Make your first contribution', description: 'Team workflow and your first task' },
];
const roles: Record<string, string> = { ENGINEERING: 'Engineering', QA: 'QA', DEVOPS: 'DevOps / SRE', GENERAL: 'General track' };

export default function ProjectOnboarding({ project, onAsk, onOpenSource }: {
  project: Project; onAsk: (question: string) => void; onOpenSource: (source: AISource) => void;
}) {
  const [plan, setPlan] = useState<Plan | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [blockedTask, setBlockedTask] = useState<Task | null>(null);
  const [blockerNote, setBlockerNote] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError('');
    apiFetch(`/api/v1/projects/${project.id}/onboarding`, { signal: controller.signal })
      .then(async response => {
        if (!response.ok) throw new Error('Unable to load your onboarding plan.');
        return await response.json() as Plan;
      }).then(setPlan).catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : 'Your plan is unavailable.');
      }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [project.id, refresh]);

  async function update(task: Task, status: Status, note = '') {
    if (saving) return;
    setSaving(true); setError('');
    try {
      const response = await apiFetch(`/api/v1/projects/${project.id}/onboarding/tasks/${task.id}`, {
        method: 'PATCH', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status, blocker_note: note }),
      });
      if (!response.ok) throw new Error('Unable to save this task. Please try again.');
      setPlan(await response.json() as Plan);
      setBlockedTask(null);
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Unable to save changes.'); }
    finally { setSaving(false); }
  }

  function saveBlocker(event: FormEvent) {
    event.preventDefault();
    if (blockedTask && blockerNote.trim()) void update(blockedTask, 'BLOCKED', blockerNote.trim());
  }

  return <section className="fd-project-page">
    <OnboardingHero projectName={project.name} />
    <div className="fd-project-heading fd-onboarding-project-toolbar">
      <button type="button" className="fd-project-text-button" disabled={loading || saving} onClick={() => setRefresh(value => value + 1)}><RefreshCw size={15} /> Refresh</button>
    </div>
    {error && <p role="alert" className="fd-project-error">{error}</p>}
    {loading && !plan && <p role="status">Loading your personal onboarding plan…</p>}
    {plan && <>
      <div className="glass fd-project-progress">
        <div><span className="eyebrow">YOUR PLAN · {roles[plan.project_role]}</span><h2>From project context to your first contribution</h2>
          <p>{plan.completed_tasks} of {plan.total_tasks} tasks completed{plan.blocked_tasks > 0 ? ` · Blocked: ${plan.blocked_tasks}` : ''}</p>
        </div><strong aria-label={`Progress ${plan.progress_percent}%`}>{plan.progress_percent}%</strong>
        <progress value={plan.completed_tasks} max={plan.total_tasks || 1} aria-label="Project onboarding progress" />
      </div>
      <div className="fd-project-stages">{stages.map((stage, index) => {
        const tasks = plan.tasks.filter(task => task.stage === stage.id);
        return <section className="glass fd-project-stage" key={stage.id} aria-labelledby={`stage-${stage.id}`}>
          <header><span className="fd-project-stage-number">0{index + 1}</span><div><h2 id={`stage-${stage.id}`}>{stage.title}</h2><p>{stage.description}</p></div></header>
          {tasks.map(task => <article className={`fd-project-task ${task.status.toLowerCase()}`} key={task.id}>
            <div className="fd-project-task-title">{task.status === 'DONE' ? <CheckCircle2 size={19} /> : task.status === 'BLOCKED' ? <AlertCircle size={19} /> : <Circle size={19} />}<h3>{task.title}</h3></div>
            <p>{task.description}</p>
            {task.blocker_note && <p className="fd-project-blocker"><strong>Blocker:</strong> {task.blocker_note}</p>}
            <div className="fd-project-task-actions">
              {task.document_path && <button type="button" onClick={() => onOpenSource({ reference: 1, title: task.title, file_path: task.document_path!, similarity: null })}><BookOpen size={14} /> View guide</button>}
              <label><span className="fd-project-sr-only">Status: {task.title}</span><select value={task.status} disabled={saving || loading} onChange={event => {
                const status = event.target.value as Status;
                if (status === 'BLOCKED') { setBlockedTask(task); setBlockerNote(task.blocker_note); }
                else void update(task, status);
              }}><option value="TODO">Not started</option><option value="IN_PROGRESS">In progress</option><option value="BLOCKED">Blocked</option><option value="DONE">Completed</option></select></label>
              {task.status === 'BLOCKED' && <button type="button" disabled={saving} onClick={() => { setBlockedTask(task); setBlockerNote(task.blocker_note); }}>Edit blocker</button>}
            </div>
            {blockedTask?.id === task.id && <form className="fd-project-blocker-form" onSubmit={saveBlocker}>
              <label htmlFor={`blocker-${task.id}`}>What is blocking your progress?</label>
              <textarea autoFocus id={`blocker-${task.id}`} required maxLength={1000} value={blockerNote} disabled={saving} onChange={event => setBlockerNote(event.target.value)} placeholder="For example: no access to the sandbox" />
              <div><button type="submit" disabled={saving || !blockerNote.trim()}>Save blocker</button><button type="button" disabled={saving} onClick={() => setBlockedTask(null)}>Cancel</button></div>
            </form>}
          </article>)}
        </section>;
      })}</div>
      <section className="fd-project-help glass"><div><span className="eyebrow">FIRSTDAY AI</span><h2>Explore the project with FirstDay AI</h2><p>Ask about the selected project’s documentation. Your task statuses and blockers are handled separately from AI.</p></div>
        <div>{[{label:'Explore the project architecture',question:'Как устроен проект и какие у него компоненты?'},{label:'See my remaining tasks',question:'Какие у меня остались задачи?'},{label:'Review my blockers',question:'Что мешает моему подключению?'}].map(item => <button key={item.label} type="button" onClick={() => onAsk(item.question)}>{item.label}<ArrowRight size={15} /></button>)}</div>
      </section>
      <p className="fd-project-footnote">{project.is_demo ? 'Demo based on fictional NovaTech materials. ' : ''}You update your task statuses; your project lead still reviews the results.</p>
    </>}
  </section>;
}
