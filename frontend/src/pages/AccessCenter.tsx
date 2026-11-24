import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { ChevronRight, Clock3, Github, Inbox, LockKeyhole, Plus, RefreshCw, ShieldCheck, X } from 'lucide-react';
import { apiFetch } from '../services/auth';
import './AccessCenter.css';

type RequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

interface AccessRequest {
  id: string;
  system_name: string;
  reason: string;
  status: RequestStatus;
  requested_at: string;
  resolved_at: string | null;
}

interface AccessList {
  demo: boolean;
  total: number;
  items: AccessRequest[];
}

const tools = ['GitHub', 'VPN', 'Figma', 'AWS', 'Atlas', 'Other'];

async function jsonRequest<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await apiFetch(url, options);
  if (!response.ok) {
    let detail = `HTTP ${response.status}`;
    try {
      const payload = (await response.json()) as { detail?: string | unknown };
      if (typeof payload.detail === 'string') detail = payload.detail;
    } catch { /* Response may not contain JSON */ }
    throw new Error(detail);
  }
  return (await response.json()) as T;
}

function ToolIcon({ name }: { name: string }) {
  const lower = name.toLowerCase();
  if (lower.includes('git')) return <Github size={20} />;
  if (lower.includes('vpn')) return <ShieldCheck size={20} />;
  return <LockKeyhole size={20} />;
}

const statusLabels: Record<RequestStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
};

export default function AccessCenter() {
  const [items, setItems] = useState<AccessRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [selected, setSelected] = useState<AccessRequest | null>(null);
  const [systemName, setSystemName] = useState('GitHub');
  const [customName, setCustomName] = useState('');
  const [reason, setReason] = useState('');

  const refresh = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError('');
    try {
      const data = await jsonRequest<AccessList>(
        '/api/v1/access-requests',
        { signal },
      );
      if (!signal?.aborted) setItems(data.items);
    } catch (caught) {
      if (!signal?.aborted) setError(caught instanceof Error ? caught.message : 'Could not load requests');
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setItems([]);
    setSelected(null);
    void refresh(controller.signal);
    return () => controller.abort();
  }, [refresh]);

  const createRequest = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const name = systemName === 'Other' ? customName.trim() : systemName;
    if (!name || reason.trim().length < 10 || submitting) return;
    setSubmitting(true);
    setError('');
    try {
      const created = await jsonRequest<AccessRequest>(
        '/api/v1/access-requests',
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ system_name: name, reason: reason.trim() }),
        },
      );
      setItems(old => [created, ...old]);
      setShowModal(false);
      setReason('');
      setCustomName('');
      setSystemName('GitHub');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Request failed');
    } finally {
      setSubmitting(false);
    }
  };

  const formatDate = (value: string) => new Intl.DateTimeFormat('en-GB', {
    dateStyle: 'medium',
  }).format(new Date(value));

  return (
    <section className="fd-access-page">
      <div className="access-heading">
        <header className="section-title">
          <span className="eyebrow">ACCESS CENTER</span>
          <h1>Request access to tools</h1>
          <p>Request the tools you need and track their approval at NovaTech.</p>
        </header>
        <button type="button" className="primary-button" onClick={() => setShowModal(true)}>
          <Plus size={17} /> New Access Request
        </button>
      </div>

      <section className="fd-access-banner" aria-label="Your access workspace">
        <span>YOUR ACCESS WORKSPACE</span>
        <h2>Everything you need, one request away.</h2>
        <p>Submit access requests and follow their review in one place.</p>
        <div className="fd-access-benefits">{['Personal requests', 'Admin-reviewed', 'Status tracking'].map(label => <span key={label}><i aria-hidden="true" />{label}</span>)}</div>
      </section>
      <div className="fd-access-list-heading">
        <div><h2>My access requests</h2><p>Your submitted requests and their current approval status.</p></div>
        <button type="button" className="fd-access-refresh" onClick={() => void refresh()} disabled={loading}>
          <RefreshCw size={15} /><span>Refresh</span>
        </button>
      </div>

      <p className="fd-access-notice"><span><LockKeyhole size={20} /></span> Requests belong to your account. Only an administrator can review pending requests.</p>
      {error && <p className="fd-access-error" role="alert">{error}</p>}

      <div className="glass requests-table fd-access-table">
        <div className="table-head"><span>Tool / Access</span><span>Status</span><span>Requested</span><span /></div>
        {loading && <p className="fd-access-empty">Loading saved requests…</p>}
        {!loading && items.length === 0 && !error && <div className="fd-access-empty-state"><span className="fd-access-inbox"><Inbox size={43} strokeWidth={1.5} /><i aria-hidden="true" /></span><h3>No access requests yet</h3><p>Create your first request to get started.</p><button type="button" className="primary-button" onClick={() => setShowModal(true)}><Plus size={19} />New Access Request</button></div>}
        {!loading && items.map(item => (
          <button type="button" className="request-row fd-access-row" key={item.id} onClick={() => setSelected(item)}>
            <div className="request-tool"><div className="tool-icon"><ToolIcon name={item.system_name} /></div>
              <div><strong>{item.system_name}</strong><small>{item.reason}</small></div></div>
            <div><span className={`fd-access-status fd-status-${item.status.toLowerCase()}`}>{statusLabels[item.status]}</span></div>
            <span className="date">{formatDate(item.requested_at)}</span><ChevronRight size={17} />
          </button>
        ))}
      </div>

      {showModal && (
        <div className="fd-access-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) setShowModal(false); }}>
          <form className="fd-access-dialog glass" onSubmit={event => void createRequest(event)} aria-label="New access request">
            <div className="fd-access-modalhead"><h2>New access request</h2>
              <button type="button" className="fd-access-iconbtn" onClick={() => setShowModal(false)} aria-label="Close"><X size={19} /></button></div>
            <label htmlFor="access-system">Tool or service</label>
            <select id="access-system" value={systemName} onChange={event => setSystemName(event.target.value)}>
              {tools.map(tool => <option key={tool} value={tool}>{tool}</option>)}
            </select>
            {systemName === 'Other' && <><label htmlFor="access-other">Service name</label>
              <input id="access-other" value={customName} maxLength={100} onChange={event => setCustomName(event.target.value)} required /></>}
            <label htmlFor="access-reason">Why do you need access?</label>
            <textarea id="access-reason" value={reason} minLength={10} maxLength={1000} rows={4}
              onChange={event => setReason(event.target.value)} required
              placeholder="Describe the work that requires this tool (at least 10 characters)" />
            <p className="fd-access-hint">Your request will be saved as Pending for your account.</p>
            <button type="submit" className="primary-button" disabled={submitting}>{submitting ? 'Submitting…' : 'Create request'}</button>
          </form>
        </div>
      )}

      {selected && (
        <div className="fd-access-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) setSelected(null); }}>
          <div className="fd-access-dialog glass" role="dialog" aria-modal="true" aria-label="Access request details">
            <div className="fd-access-modalhead"><h2>{selected.system_name} request</h2>
              <button type="button" className="fd-access-iconbtn" onClick={() => setSelected(null)} aria-label="Close"><X size={19} /></button></div>
            <span className={`fd-access-status fd-status-${selected.status.toLowerCase()}`}>{statusLabels[selected.status]}</span>
            <p className="fd-access-detail">{selected.reason}</p>
            <p className="fd-access-hint"><Clock3 size={14} /> Requested {formatDate(selected.requested_at)}</p>
            <p className="fd-access-hint">Approvals update this application only; granting actual tool access requires an external integration.</p>
          </div>
        </div>
      )}
    </section>
  );
}
