import { useCallback, useEffect, useState } from 'react';
import { CalendarDays, ChartNoAxesColumnIncreasing, Check, ChevronRight, Circle, Info, RefreshCw } from 'lucide-react';
import OnboardingHero from '../components/OnboardingHero';
import './MyOnboarding.css';
import { apiFetch } from '../services/auth';

type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';

interface OnboardingTask {
  id: string;
  title: string;
  description: string | null;
  day_number: number;
  week: number;
  status: TaskStatus;
}

export interface OnboardingPlan {
  job_title: string;
  plan_id: string;
  title: string;
  status: string;
  total_tasks: number;
  completed_tasks: number;
  progress_percent: number;
  tasks: OnboardingTask[];
  demo: boolean;
}

interface Props {
  onPlanChange?: (plan: OnboardingPlan) => void;
}

const weeks = [
  { id: 1, name: 'Week 1', subtitle: 'Get started', days: 'Days 1–5' },
  { id: 2, name: 'Week 2', subtitle: 'Tools & foundations', days: 'Days 6–10' },
  { id: 3, name: 'Week 3', subtitle: 'Practice & development', days: 'Days 11–20' },
  { id: 4, name: 'Week 4', subtitle: 'Review & demonstration', days: 'Days 21–30' },
];

async function apiRequest<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await apiFetch(url, options);
  if (!response.ok) {
    let detail = `HTTP ${response.status}`;
    try {
      const data: { detail?: string } = await response.json();
      detail = data.detail || detail;
    } catch {
      // Keep the HTTP status when the server returns a non-JSON error.
    }
    throw new Error(detail);
  }
  return (await response.json()) as T;
}

export default function MyOnboarding({ onPlanChange }: Props) {
  const [plan, setPlan] = useState<OnboardingPlan | null>(null);
  const [week, setWeek] = useState(1);
  const [busy, setBusy] = useState(false);
  const [updatingId, setUpdatingId] = useState<string | null>(null);
  const [error, setError] = useState('');

  const loadPlan = useCallback(async () => {
    setBusy(true);
    setError('');
    setPlan(null);
    try {
      const data = await apiRequest<OnboardingPlan>(
        '/api/v1/onboarding',
        { method: 'GET' },
      );
      setPlan(data);
      onPlanChange?.(data);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to load your internship plan.');
    } finally {
      setBusy(false);
    }
  }, [onPlanChange]);

  useEffect(() => {
    void loadPlan();
  }, [loadPlan]);

  const updateTask = async (task: OnboardingTask) => {
    if (updatingId || !plan) return;
    setUpdatingId(task.id);
    setError('');
    try {
      const updated = await apiRequest<OnboardingPlan>(
        `/api/v1/onboarding/tasks/${task.id}`,
        {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ status: task.status === 'DONE' ? 'TODO' : 'DONE' }),
        },
      );
      setPlan(updated);
      onPlanChange?.(updated);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to update this task.');
    } finally {
      setUpdatingId(null);
    }
  };

  const currentTasks = plan?.tasks.filter((task) => task.week === week) ?? [];
  const currentDone = currentTasks.filter((task) => task.status === 'DONE').length;
  const activeWeek = weeks.find((item) => item.id === week) ?? weeks[0];

  return (
    <section className="fd-onboarding-page">
      <OnboardingHero />

      {error && <div className="fd-onboarding-error" role="alert">{error}</div>}
      {busy && !plan && <div className="glass fd-onboarding-loading">Loading your internship plan…</div>}
      {!plan && !busy && <button type="button" className="fd-onboarding-refresh fd-onboarding-retry" onClick={() => void loadPlan()}><RefreshCw size={15} /> Retry</button>}

      {plan && (
        <div className="fd-onboarding-board">
          <div className="glass fd-onboarding-progress">
            <div className="card-heading">
              <ChartNoAxesColumnIncreasing size={26} aria-hidden="true" />
              <strong title={plan.job_title}>Overall progress</strong>
              <button type="button" className="fd-onboarding-refresh" onClick={() => void loadPlan()} disabled={busy || !!updatingId} aria-label="Refresh internship plan" title="Refresh internship plan"><RefreshCw size={15} /></button>
              <b>{plan.progress_percent}%</b>
            </div>
            <div className="progress-track" role="progressbar" aria-label={`Overall progress · ${plan.job_title}`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={plan.progress_percent}>
              <div className="progress-fill" style={{ width: `${plan.progress_percent}%` }} />
            </div>
            <div className="progress-foot">
              <CalendarDays size={14} /> {plan.completed_tasks} of {plan.total_tasks} tasks completed
              <span className="fd-onboarding-status">{plan.status.replaceAll('_', ' ')}</span>
            </div>
          </div>

          <div className="fd-onboarding-layout">
            <div className="glass fd-onboarding-weeks" aria-label="Internship stages">
              <h2><CalendarDays size={29} strokeWidth={1.8} />Internship plan</h2>
              {weeks.map((item) => {
                const tasks = plan.tasks.filter((task) => task.week === item.id);
                const done = tasks.filter((task) => task.status === 'DONE').length;
                return (
                  <button
                    type="button"
                    key={item.id}
                    className={`fd-onboarding-week ${week === item.id ? 'selected' : ''}`}
                    onClick={() => setWeek(item.id)}
                    aria-current={week === item.id ? 'step' : undefined}
                  >
                    <span className="fd-onboarding-week-dot">
                      {tasks.length > 0 && done === tasks.length ? <Check size={17} /> : <Circle size={25} strokeWidth={1.2} />}
                    </span>
                    <span className="fd-onboarding-week-copy">
                      <strong>{item.name}</strong>
                      <small>{item.subtitle}</small>
                    </span>
                    <span className="fd-onboarding-week-count">{done}/{tasks.length}</span>
                    <ChevronRight size={18} aria-hidden="true" />
                  </button>
                );
              })}
            </div>

            <div className="glass fd-onboarding-detail">
              <div className="fd-onboarding-detail-top">
                <div>
                  <span className="eyebrow">{activeWeek.name.toUpperCase()} · {activeWeek.days.toUpperCase()}</span>
                  <h2>{activeWeek.name}: {activeWeek.subtitle}</h2>
                  <p>Build your skills and complete this week’s internship tasks.</p>
                </div>
                <span className="fd-onboarding-count">{currentDone}/{currentTasks.length} done</span>
              </div>

              <div className="fd-onboarding-task-list">
                {currentTasks.map((task) => (
                  <div className="fd-onboarding-task-row" key={task.id}>
                  <button
                    className={`fd-onboarding-task ${task.status === 'DONE' ? 'complete' : ''}`}
                    type="button"
                    disabled={!!updatingId}
                    onClick={() => void updateTask(task)}
                    aria-label={`${task.status === 'DONE' ? 'Mark incomplete' : 'Mark completed'}: ${task.title}`}
                    aria-pressed={task.status === 'DONE'}
                  >
                    <span className="fd-onboarding-task-check">
                      {task.status === 'DONE' ? <Check size={21} /> : <Circle size={21} />}
                    </span>
                    <span className="fd-onboarding-task-copy">
                      <strong>{task.title}</strong>
                    </span>
                    <span className="fd-onboarding-day">Day {task.day_number}</span>
                  </button>
                  {task.description && <details className="fd-onboarding-task-description"><summary aria-label={`Task details: ${task.title}`} title="Task details"><Info size={14} /></summary><p>{task.description}</p></details>}
                  </div>
                ))}
                {currentTasks.length === 0 && <p>No tasks are scheduled for this stage.</p>}
              </div>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
