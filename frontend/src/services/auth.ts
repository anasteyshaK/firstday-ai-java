export interface CurrentUser {
  id: string;
  full_name: string;
  email: string;
  role: 'EMPLOYEE' | 'HR' | 'ADMIN';
  job_title: string;
  department: string;
  start_date: string;
  employment_type: 'INTERN' | 'EMPLOYEE';
  onboarding_mode: 'COMPANY' | 'PROJECT';
}

interface AuthResponse {
  access_token: string;
  token_type: string;
  user: CurrentUser;
}

const STORAGE_KEY = 'firstday_auth_token';
let token = sessionStorage.getItem(STORAGE_KEY);

export function setToken(value: string | null) {
  token = value;
  if (value) sessionStorage.setItem(STORAGE_KEY, value);
  else sessionStorage.removeItem(STORAGE_KEY);
}

export function hasToken(): boolean {
  return token !== null;
}

export async function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const response = await fetch(input, { ...init, headers });
  if (response.status === 401) {
    setToken(null);
    window.dispatchEvent(new Event('firstday:session-expired'));
  }
  return response;
}

async function authPost(url: string, body: object): Promise<AuthResponse> {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    let reason = `HTTP ${response.status}`;
    try {
      const payload = await response.json() as { detail?: string };
      if (typeof payload.detail === 'string') reason = payload.detail;
    } catch { /* keep status */ }
    throw new Error(reason);
  }
  const result = await response.json() as AuthResponse;
  setToken(result.access_token);
  return result;
}

export const login = (email: string, password: string) =>
  authPost('/api/v1/auth/login', { email, password });

export const register = (full_name: string, email: string, password: string, job_track: string,
  employment_type: CurrentUser['employment_type'], onboarding_mode: CurrentUser['onboarding_mode']) =>
  authPost('/api/v1/auth/register', { full_name, email, password, job_track, employment_type, onboarding_mode });

export async function getMe(): Promise<CurrentUser | null> {
  if (!hasToken()) return null;
  const response = await apiFetch('/api/v1/auth/me');
  if (!response.ok) return null;
  return await response.json() as CurrentUser;
}

export async function logout() {
  try { await apiFetch('/api/v1/auth/logout', { method: 'POST' }); }
  finally { setToken(null); }
}
