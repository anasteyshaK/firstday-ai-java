
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Check, Clock3, RefreshCw, Search, ShieldCheck, Users, X, ListChecks } from 'lucide-react';
import { apiFetch } from '../services/auth';
import './AdminPanel.css';
import AdminProjectAssignments, { type AssignmentEmployee } from './AdminProjectAssignments';

type RequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
type Tab = 'employees' | 'requests' | 'projects';

interface Overview {
  employees_total: number;
  plans_total: number;
  completed_plans: number;
  tasks_total: number;
  tasks_done: number;
  average_progress: number;
  requests_pending: number;
  requests_approved: number;
  requests_rejected: number;
}
interface Employee extends AssignmentEmployee {
  id: string;
  full_name: string;
  email: string;
  role: string;
  job_title: string;
  department: string;
  start_date: string;
  plan_status: string | null;
  tasks_total: number;
  tasks_done: number;
  progress_percent: number;
}
interface AccessRequest {
  id: string;
  employee_id: string;
  employee_name: string;
  employee_email: string;
  system_name: string;
  reason: string;
  status: RequestStatus;
  requested_at: string;
  resolved_at: string | null;
}

async function readJSON<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init);
  if (!response.ok) {
    const message = await response.json().catch(() => ({})) as { detail?: string };
    throw new Error(message.detail || `Server returned HTTP ${response.status}`);
  }
  return await response.json() as T;
}

const safeText = (value: unknown): string =>
  typeof value === 'string' ? value : '';

const formatDate = (date: string | null | undefined) => {
  if (!date) return '—';
  const parsed = new Date(date);
  if (Number.isNaN(parsed.getTime())) return '—';
  return parsed.toLocaleDateString('en-GB', {
    year: 'numeric', month: 'short', day: 'numeric',
  });
};

export default function AdminPanel() {
  const [overview, setOverview] = useState<Overview | null>(null);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [requests, setRequests] = useState<AccessRequest[]>([]);
  const [tab, setTab] = useState<Tab>('employees');
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | RequestStatus>('ALL');
  const [busy, setBusy] = useState(false);
  const [reviewing, setReviewing] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<AccessRequest | null>(null);

  const refresh = useCallback(async () => {
    setBusy(true);
    setError('');
    try {
      const [overviewData, employeeData, requestData] = await Promise.all([
        readJSON<Overview>('/api/v1/admin/overview'),
        readJSON<Employee[]>('/api/v1/admin/employees'),
        readJSON<AccessRequest[]>('/api/v1/admin/access-requests'),
      ]);
      setOverview(overviewData);
      setEmployees(employeeData);
      setRequests(requestData);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Admin data could not be loaded');
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  const filteredEmployees = useMemo(() => employees.filter(employee =>
    [employee.full_name, employee.email, employee.job_title, employee.department]
      .some(value => safeText(value).toLowerCase().includes(query.toLowerCase().trim())),
  ), [employees, query]);
  const filteredRequests = useMemo(() => requests.filter(request =>
    (statusFilter === 'ALL' || request.status === statusFilter) &&
    [request.employee_name, request.employee_email, request.system_name, request.reason]
      .some(value => safeText(value).toLowerCase().includes(query.toLowerCase().trim())),
  ), [requests, query, statusFilter]);

  const review = async (request: AccessRequest, status: 'APPROVED' | 'REJECTED') => {
    if (reviewing || request.status !== 'PENDING') return;
    if (!window.confirm(`${status === 'APPROVED' ? 'Approve' : 'Reject'} ${request.system_name} request from ${request.employee_name}? This does not grant real access.`)) return;
    setReviewing(request.id);
    setError('');
    try {
      const updated = await readJSON<AccessRequest>(`/api/v1/admin/access-requests/${request.id}/review`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status }),
      });
      setRequests(current => current.map(value => value.id === updated.id ? updated : value));
      setSelected(current => current?.id === updated.id ? updated : current);
      const nextOverview = await readJSON<Overview>('/api/v1/admin/overview');
      setOverview(nextOverview);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not update request');
    } finally {
      setReviewing(null);
    }
  };

  return (
    <section className="fd-admin">
      <div className="fd-admin-heading">
        <header className="section-title">
          <span className="eyebrow">ADMIN WORKSPACE</span>
          <h1>Team overview <ShieldCheck size={26} /></h1>
          <p>Monitor employee onboarding and review incoming access requests.</p>
        </header>
        <button className="fd-admin-refresh" type="button" onClick={() => void refresh()} disabled={busy}>
          <RefreshCw size={16} className={busy ? 'fd-admin-spin' : ''} /> Refresh data
        </button>
      </div>
      {error && <div className="fd-admin-error" role="alert">{error}</div>}
      <div className="fd-admin-cards">
        <div className="fd-admin-card glass"><span><Users size={17} /> Employees</span><strong>{overview?.employees_total ?? '—'}</strong><small>{overview?.plans_total ?? '—'} onboarding plans</small></div>
        <div className="fd-admin-card glass"><span><ListChecks size={17} /> Average progress</span><strong>{overview ? `${overview.average_progress}%` : '—'}</strong><small>{overview?.tasks_done ?? '—'} of {overview?.tasks_total ?? '—'} tasks done</small></div>
        <div className="fd-admin-card glass"><span><Clock3 size={17} /> Awaiting review</span><strong>{overview?.requests_pending ?? '—'}</strong><small>Pending access requests</small></div>
        <div className="fd-admin-card glass"><span><Check size={17} /> Approved</span><strong>{overview?.requests_approved ?? '—'}</strong><small>{overview?.requests_rejected ?? '—'} rejected requests</small></div>
      </div>
      <div className="fd-admin-panel glass">
        <div className="fd-admin-toolbar">
          <div className="fd-admin-tabs" role="tablist" aria-label="Admin sections">
            <button className={tab === 'employees' ? 'selected' : ''} type="button" role="tab" aria-selected={tab === 'employees'} onClick={() => {setTab('employees'); setQuery('');}}>Employees</button>
            <button className={tab === 'requests' ? 'selected' : ''} type="button" role="tab" aria-selected={tab === 'requests'} onClick={() => {setTab('requests'); setQuery('');}}>Access requests <span>{overview?.requests_pending ?? 0}</span></button>
            <button className={tab === 'projects' ? 'selected' : ''} type="button" role="tab" aria-selected={tab === 'projects'} onClick={() => {setTab('projects'); setQuery('');}}>Назначение проектов</button>
          </div>
          {tab !== 'projects' && <label className="fd-admin-search"><Search size={16} /><input value={query} onChange={e => setQuery(e.target.value)} placeholder={tab === 'employees' ? 'Search employees…' : 'Search requests…'} aria-label="Search" /></label>}
          {tab === 'requests' && <select className="fd-admin-filter" value={statusFilter} onChange={e => setStatusFilter(e.target.value as 'ALL' | RequestStatus)} aria-label="Filter requests by status"><option value="ALL">All statuses</option><option value="PENDING">Pending</option><option value="APPROVED">Approved</option><option value="REJECTED">Rejected</option></select>}
        </div>
        {tab === 'projects' ? <AdminProjectAssignments employees={employees} onUpdated={() => void refresh()} /> : tab === 'employees' ? <div className="fd-admin-scroll"><table className="fd-admin-table"><thead><tr><th>Employee</th><th>Position</th><th>Started</th><th>Onboarding</th><th>Tasks</th></tr></thead><tbody>
          {filteredEmployees.map(employee => <tr key={employee.id}><td><div className="fd-admin-person"><span className="fd-admin-initial">{(safeText(employee.full_name).charAt(0) || '?').toUpperCase()}</span><span><strong>{employee.full_name}</strong><small>{employee.email} · {employee.role}</small></span></div></td><td>{employee.job_title}<small className="fd-admin-sub">{employee.department}</small></td><td>{formatDate(employee.start_date)}</td><td><div className="fd-admin-progress"><div><i style={{width:`${employee.progress_percent}%`}} /></div><strong>{employee.progress_percent}%</strong></div><small className="fd-admin-sub">{employee.plan_status || 'No plan'}</small></td><td>{employee.tasks_done}/{employee.tasks_total}</td></tr>)}
          {!filteredEmployees.length && <tr><td colSpan={5} className="fd-admin-empty">{busy ? 'Loading employees…' : 'No matching employees'}</td></tr>}
        </tbody></table></div> : <div className="fd-admin-scroll"><table className="fd-admin-table"><thead><tr><th>Employee</th><th>System</th><th>Requested</th><th>Status</th><th>Actions</th></tr></thead><tbody>
          {filteredRequests.map(request => <tr key={request.id}><td><div className="fd-admin-person"><span className="fd-admin-initial">{(safeText(request.employee_name).charAt(0) || '?').toUpperCase()}</span><span><strong>{request.employee_name}</strong><small>{request.employee_email}</small></span></div></td><td><button type="button" className="fd-admin-link" onClick={() => setSelected(request)}>{request.system_name}</button><small className="fd-admin-sub fd-admin-reason">{request.reason}</small></td><td>{formatDate(request.requested_at)}</td><td><span className={`fd-admin-status ${safeText(request.status).toLowerCase()}`}>{request.status}</span></td><td>{request.status === 'PENDING' ? <div className="fd-admin-actions"><button type="button" className="approve" disabled={reviewing !== null} onClick={() => void review(request, 'APPROVED')}><Check size={14} /> Approve</button><button type="button" className="reject" disabled={reviewing !== null} onClick={() => void review(request, 'REJECTED')}><X size={14} /> Reject</button></div> : <span className="fd-admin-muted">Reviewed</span>}</td></tr>)}
          {!filteredRequests.length && <tr><td colSpan={5} className="fd-admin-empty">{busy ? 'Loading requests…' : 'No matching requests'}</td></tr>}
        </tbody></table></div>}
      </div>
      <p className="fd-admin-disclaimer">Access decisions are stored in FirstDay AI only. Approving a request does not provision external permissions.</p>
      {selected && <div className="fd-admin-backdrop" onMouseDown={e => { if(e.target === e.currentTarget) setSelected(null); }}><div className="fd-admin-dialog glass" role="dialog" aria-modal="true" aria-label="Access request details"><div className="fd-admin-dialoghead"><h2>{selected.system_name} access request</h2><button type="button" onClick={() => setSelected(null)} aria-label="Close details"><X size={18}/></button></div><p className="fd-admin-muted">{selected.employee_name} · {selected.employee_email}</p><span className={`fd-admin-status ${safeText(selected.status).toLowerCase()}`}>{selected.status}</span><p className="fd-admin-request-text">{selected.reason}</p><p className="fd-admin-muted">Submitted {formatDate(selected.requested_at)}</p>{selected.status === 'PENDING' && <div className="fd-admin-actions"><button className="approve" type="button" disabled={reviewing !== null} onClick={() => void review(selected, 'APPROVED')}>Approve request</button><button className="reject" type="button" disabled={reviewing !== null} onClick={() => void review(selected, 'REJECTED')}>Reject request</button></div>}</div></div>}
    </section>
  );
}
