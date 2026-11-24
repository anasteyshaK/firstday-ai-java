import { useEffect, useRef, useState } from 'react';
import { apiFetch, type CurrentUser } from '../services/auth';

export interface AssignmentEmployee {
  id: string; full_name: string; email: string;
  employment_type: CurrentUser['employment_type']; onboarding_mode: CurrentUser['onboarding_mode'];
}
interface CatalogProject { id: string; name: string; is_demo: boolean }
interface Assignment { project_id: string; name: string; project_role: string; is_active: boolean }
const roleLabels: Record<string, string> = { ENGINEERING: 'Разработка', QA: 'QA', DEVOPS: 'DevOps / SRE', GENERAL: 'Общий маршрут' };
async function request<T>(path: string, body?: object, method = 'GET'): Promise<T> {
  const response = await apiFetch(path, body ? {
    method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  } : undefined);
  if (!response.ok) {
    const payload = await response.json().catch(() => ({})) as { detail?: unknown };
    throw new Error(typeof payload.detail === 'string' ? payload.detail : 'Не удалось сохранить или загрузить назначения.');
  }
  return await response.json() as T;
}

export default function AdminProjectAssignments({ employees, onUpdated }: {
  employees: AssignmentEmployee[]; onUpdated: () => void;
}) {
  const [employeeId, setEmployeeId] = useState('');
  const employee = employees.find(item => item.id === employeeId);
  const [catalog, setCatalog] = useState<CatalogProject[]>([]);
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [projectId, setProjectId] = useState('');
  const [role, setRole] = useState('ENGINEERING');
  const [employmentType, setEmploymentType] = useState<CurrentUser['employment_type']>('EMPLOYEE');
  const [mode, setMode] = useState<CurrentUser['onboarding_mode']>('PROJECT');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [reload, setReload] = useState(0);
  const version = useRef(0);

  useEffect(() => {
    if (employee) { setEmploymentType(employee.employment_type); setMode(employee.onboarding_mode); }
  }, [employee?.id, employee?.employment_type, employee?.onboarding_mode]);
  useEffect(() => {
    const current = ++version.current;
    setAssignments([]); setCatalog([]); setProjectId(''); setError(''); setMessage('');
    if (!employeeId) return;
    setLoading(true);
    Promise.all([
      request<CatalogProject[]>('/api/v1/admin/projects'),
      request<Assignment[]>(`/api/v1/admin/employees/${employeeId}/projects`),
    ]).then(([projects, memberships]) => {
      if (version.current !== current) return;
      setCatalog(projects); setAssignments(memberships);
    }).catch(reason => { if (version.current === current) setError(reason instanceof Error ? reason.message : 'Назначения недоступны.'); })
      .finally(() => { if (version.current === current) setLoading(false); });
    return () => { version.current += 1; };
  }, [employeeId, reload]);

  async function saveProfile() {
    if (!employee || saving) return;
    setSaving(true); setError(''); setMessage('');
    try {
      await request(`/api/v1/admin/employees/${employee.id}/profile`, { employment_type: employmentType, onboarding_mode: mode }, 'PATCH');
      setMessage('Профиль сохранён. Права на проекты не изменились.');
      onUpdated(); window.dispatchEvent(new Event('firstday:projects-updated'));
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Ошибка сохранения.'); }
    finally { setSaving(false); }
  }
  async function assign(id: string, projectRole: string, active: boolean) {
    if (!employee || saving) return;
    setSaving(true); setError(''); setMessage('');
    try {
      const updated = await request<Assignment>(`/api/v1/admin/employees/${employee.id}/projects/${id}`,
        { project_role: projectRole, is_active: active }, 'PUT');
      setAssignments(previous => [...previous.filter(item => item.project_id !== id), updated].sort((a, b) => a.name.localeCompare(b.name)));
      setProjectId('');
      setMessage(active ? 'Проект назначен. Сотрудник увидит его после обновления доступов.' : 'Доступ отозван. Прогресс и история сохранены.');
      window.dispatchEvent(new Event('firstday:projects-updated'));
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Ошибка назначения.'); }
    finally { setSaving(false); }
  }
  return <section className="fd-admin-assignments" aria-label="Назначение проектов">
    <h2>Проекты и сценарий сотрудника</h2>
    <p>В личном списке сотрудника отображаются только назначенные проекты. Статус стажёра не выдаёт доступ автоматически.</p>
    <label>Сотрудник
      <select aria-label="Сотрудник для назначения" value={employeeId} disabled={saving} onChange={event => setEmployeeId(event.target.value)}>
        <option value="">Выберите сотрудника</option>
        {employees.map(item => <option key={item.id} value={item.id}>{item.full_name} · {item.email}</option>)}
      </select>
    </label>
    {error && <p className="fd-admin-error" role="alert">{error}</p>}
    {message && <p role="status">{message}</p>}
    {employee && <>
      <div className="fd-admin-assignment-form">
        <label>Статус<select value={employmentType} onChange={event => setEmploymentType(event.target.value as CurrentUser['employment_type'])} disabled={saving}>
          <option value="EMPLOYEE">Сотрудник</option><option value="INTERN">Стажёр</option>
        </select></label>
        <label>Стартовый сценарий<select value={mode} onChange={event => setMode(event.target.value as CurrentUser['onboarding_mode'])} disabled={saving}>
          <option value="PROJECT">Project onboarding</option><option value="COMPANY">Internship</option>
        </select></label>
        <button type="button" className="fd-admin-refresh" onClick={() => void saveProfile()} disabled={saving}>Сохранить профиль</button>
      </div>
      <h3>Назначенные проекты</h3>
      {loading ? <p role="status">Загружаем назначения…</p> : <>
        <div className="fd-admin-assignment-list">{assignments.map(item => <article key={item.project_id}>
          <div><strong>{item.name}</strong><small>{roleLabels[item.project_role]} · {item.is_active ? 'Доступ открыт' : 'Доступ отозван'}</small></div>
          <button className="fd-admin-refresh" type="button" disabled={saving} onClick={() => void assign(item.project_id, item.project_role, !item.is_active)}>
            {item.is_active ? 'Отозвать доступ' : 'Восстановить доступ'}
          </button>
        </article>)}</div>
        {!assignments.length && !error && <p>Проекты ещё не назначены.</p>}
        {!error && <div className="fd-admin-assignment-form">
          <label>Проект<select aria-label="Проект для назначения" value={projectId} disabled={saving} onChange={event => setProjectId(event.target.value)}>
            <option value="">Выберите проект</option>
            {catalog.filter(project => !assignments.some(item => item.project_id === project.id)).map(project =>
              <option key={project.id} value={project.id}>{project.name}{project.is_demo ? ' · демо' : ''}</option>)}
          </select></label>
          <label>Роль в проекте<select aria-label="Назначаемая роль в проекте" value={role} disabled={saving} onChange={event => setRole(event.target.value)}>
            {Object.entries(roleLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select></label>
          <button className="fd-admin-refresh" type="button" disabled={saving || !projectId} onClick={() => void assign(projectId, role, true)}>Назначить проект</button>
        </div>}
        <button className="fd-project-text-button" type="button" disabled={saving} onClick={() => setReload(value => value + 1)}>Обновить назначения</button>
      </>}
    </>}
  </section>;
}
