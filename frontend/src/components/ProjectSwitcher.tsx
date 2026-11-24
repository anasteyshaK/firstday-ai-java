import { FolderOpen, RefreshCw } from 'lucide-react';
import type { CurrentUser } from '../services/auth';
import '../pages/ProjectOnboarding.css';

export interface Project {
  id: string;
  slug: string;
  name: string;
  description: string;
  is_demo: boolean;
  joined: boolean;
  project_role: string | null;
}

export default function ProjectSwitcher({ selected, projects, user, onChange, onRefresh, disabled, refreshing, error, compact = false }: {
  selected: Project | null;
  projects: Project[];
  user: CurrentUser;
  onChange: (project: Project | null) => void;
  onRefresh: () => void;
  disabled: boolean;
  refreshing: boolean;
  error: string;
  compact?: boolean;
}) {
  if (compact) return <div className="fd-compact-context">
    <select aria-label="Рабочее пространство" title={selected ? `Проект: ${selected.name}` : 'Общие материалы компании'}
      value={selected?.id ?? ''} disabled={disabled || refreshing}
      onChange={event => onChange(projects.find(project => project.id === event.target.value) ?? null)}>
      <option value="">{user.onboarding_mode === 'COMPANY' ? 'NovaTech · Internship' : 'NovaTech · company'}</option>
      {projects.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}
    </select>
    {error && <span role="alert" className="fd-context-error">{error}</span>}
  </div>;
  return <section className="fd-project-context" aria-label="Рабочий контекст">
    <div className="fd-project-picker">
      <FolderOpen size={20} aria-hidden="true" />
      <label htmlFor="project-context">{user.employment_type === 'INTERN' ? 'Стажёр' : 'Сотрудник'} · рабочее пространство
        <select id="project-context" aria-label="Рабочее пространство" value={selected?.id ?? ''}
          disabled={disabled || refreshing} onChange={event => onChange(projects.find(project => project.id === event.target.value) ?? null)}>
          <option value="">{user.onboarding_mode === 'COMPANY' ? 'NovaTech · Internship' : 'NovaTech · company resources'}</option>
          {projects.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}
        </select>
      </label>
      <span className="fd-project-context-note">{selected ? 'Вам назначен этот проект · документы, маршрут и чат' :
        projects.length ? 'Общие материалы компании · проекты доступны в списке' : 'Проект пока не назначен. Доступ выдаёт администратор.'}</span>
      <button className="fd-project-text-button" type="button" onClick={onRefresh} disabled={disabled || refreshing}>
        <RefreshCw size={15} /> {refreshing ? 'Обновляем…' : 'Обновить доступы'}
      </button>
    </div>
    {error && <p className="fd-project-error" role="alert">{error}</p>}
  </section>;
}
