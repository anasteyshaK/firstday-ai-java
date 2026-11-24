import { useState, type FormEvent } from 'react';
import { ArrowRight, ChevronDown, Eye, EyeOff, LockKeyhole, Mail, Sparkles, UserRound } from 'lucide-react';
import { login, register, type CurrentUser } from '../services/auth';
import './LoginPage.css';

type Props = { onLogin: (user: CurrentUser) => void };

export default function LoginPage({ onLogin }: Props) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmation, setShowConfirmation] = useState(false);
  const [track, setTrack] = useState('JAVA');
  const [employmentType, setEmploymentType] = useState<CurrentUser['employment_type']>('EMPLOYEE');
  const [onboardingMode, setOnboardingMode] = useState<CurrentUser['onboarding_mode']>('PROJECT');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (mode === 'register' && password !== confirmation) {
      setError('Пароли не совпадают. Повторите пароль.');
      return;
    }
    setPending(true);
    setError('');
    try {
      const result = mode === 'login'
        ? await login(email, password)
        : await register(name, email, password, track, employmentType, onboardingMode);
      onLogin(result.user);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Ошибка входа');
    } finally {
      setPending(false);
    }
  }

  return (
    <main className={`fd-auth-page fd-auth-${mode}`}>
      <div className="fd-auth-art" aria-hidden="true" />
      <section className="fd-auth-card" aria-labelledby="fd-auth-title">
        <div className="fd-auth-brand"><span><Sparkles size={22} /></span>FirstDay AI</div>
        <div className="fd-auth-eyebrow">NOVATECH · EMPLOYEE WORKSPACE</div>
        <h1 id="fd-auth-title">{mode === 'login' ? 'Welcome back' : 'Create your account'}</h1>
        <p>{mode === 'login' ? 'Sign in to continue your journey.' : 'Join FirstDay AI and start your onboarding.'}</p>
        <form onSubmit={event => void submit(event)}>
          {mode === 'register' && <>
            <label htmlFor="fd-auth-name">Full name</label>
            <div className="fd-auth-input"><UserRound size={20} aria-hidden="true" /><input id="fd-auth-name" value={name} onChange={event => setName(event.target.value)}
              minLength={2} maxLength={150} autoComplete="name" required placeholder="Your full name" /></div>
          </>}
          <label htmlFor="fd-auth-email">{mode === 'login' ? 'Email' : 'Work email'}</label>
          <div className="fd-auth-input"><Mail size={20} aria-hidden="true" /><input id="fd-auth-email" value={email} onChange={event => setEmail(event.target.value)}
            type="email" maxLength={255} autoComplete="email" required placeholder={mode === 'login' ? 'you@example.com' : 'you@novatech.com'} /></div>
          <label htmlFor="fd-auth-password">Password</label>
          <div className="fd-auth-input"><LockKeyhole size={20} aria-hidden="true" /><input id="fd-auth-password" value={password} onChange={event => setPassword(event.target.value)}
            type={showPassword ? 'text' : 'password'} autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            minLength={mode === 'register' ? 12 : undefined} required
            placeholder={mode === 'login' ? 'Your password' : 'At least 12 characters'} />
            <button type="button" className="fd-auth-eye" aria-label={showPassword ? 'Hide password' : 'Show password'} aria-pressed={showPassword} onClick={() => setShowPassword(value => !value)}>{showPassword ? <EyeOff size={20} /> : <Eye size={20} />}</button>
          </div>
          {mode === 'register' && <>
            <label htmlFor="fd-auth-confirm">Confirm password</label>
            <div className="fd-auth-input"><LockKeyhole size={20} aria-hidden="true" /><input id="fd-auth-confirm" value={confirmation} onChange={event => setConfirmation(event.target.value)} type={showConfirmation ? 'text' : 'password'} autoComplete="new-password" required placeholder="Repeat your password" />
              <button type="button" className="fd-auth-eye" aria-label={showConfirmation ? 'Hide confirmation password' : 'Show confirmation password'} aria-pressed={showConfirmation} onClick={() => setShowConfirmation(value => !value)}>{showConfirmation ? <EyeOff size={20} /> : <Eye size={20} />}</button>
            </div>
            <details className="fd-auth-profile"><summary>Рабочий профиль <span>{employmentType === 'INTERN' ? 'Стажёр' : 'Сотрудник'} · {onboardingMode === 'PROJECT' ? 'Project' : 'Internship'}</span><ChevronDown size={16} /></summary>
            <div className="fd-auth-profile-fields">
            <label htmlFor="fd-auth-type">Ваш статус</label>
            <select id="fd-auth-type" value={employmentType} onChange={event => {
              const value = event.target.value as CurrentUser['employment_type'];
              setEmploymentType(value); setOnboardingMode(value === 'INTERN' ? 'COMPANY' : 'PROJECT');
            }}>
              <option value="EMPLOYEE">Сотрудник</option><option value="INTERN">Стажёр</option>
            </select>
            <label htmlFor="fd-auth-mode">С чего начнёте</label>
            <select id="fd-auth-mode" value={onboardingMode} onChange={event => setOnboardingMode(event.target.value as CurrentUser['onboarding_mode'])}>
              <option value="PROJECT">Project onboarding</option>
              <option value="COMPANY">Internship · personal learning plan</option>
            </select>
            <small>Статус не открывает проекты автоматически. Администратор сможет уточнить ваш сценарий.</small>
            <label htmlFor="fd-auth-track">Your job track</label>
            <select id="fd-auth-track" value={track} onChange={event => setTrack(event.target.value)}>
              <option value="JAVA">Java Developer</option>
              <option value="QA">QA Engineer</option>
              <option value="DEVOPS">DevOps / SRE</option>
            </select>
            </div></details>
          </>}
          {mode === 'login' && <div className="fd-auth-options"><span><LockKeyhole size={14} /> Private session</span><button type="button" disabled title="Восстановление пароля пока не подключено">Forgot password?</button></div>}
          {error && <div className="fd-auth-error" role="alert">{error}</div>}
          <button className="fd-auth-submit" disabled={pending} type="submit">
            {pending ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
            <ArrowRight size={18} />
          </button>
        </form>
        <div className="fd-auth-switch">{mode === 'login' ? 'New to FirstDay AI? ' : 'Already have an account? '}<button type="button" disabled={pending} onClick={() => {
          setMode(current => current === 'login' ? 'register' : 'login'); setError(''); setPassword(''); setConfirmation(''); setShowPassword(false); setShowConfirmation(false);
        }}>{mode === 'login' ? 'Create an account' : 'Sign in'}</button></div>
        {mode === 'login' && <div className="fd-auth-foot"><LockKeyhole size={14} /> Secure access to your NovaTech workspace</div>}
      </section>
    </main>
  );
}
