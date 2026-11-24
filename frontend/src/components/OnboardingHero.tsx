import { Sparkle } from 'lucide-react';

export default function OnboardingHero({ projectName, description, variant }: { projectName?: string; description?: string; variant?: 'project' | 'internship' }) {
  const isProject = !!projectName || variant === 'project';
  return <header className="fd-onboarding-hero">
    <img src="/assets/onboarding/calendar-wave.png" alt="" aria-hidden="true" />
    <span className="fd-onboarding-eyebrow">{isProject ? 'PROJECT ONBOARDING' : 'INTERNSHIP'}</span>
    <h1>{isProject ? 'Project Onboarding' : 'My Internship'} <Sparkle aria-hidden="true" /></h1>
    <p className="fd-onboarding-subtitle">{isProject ? projectName ? `Your personal onboarding plan for ${projectName}.` : 'Your personal project onboarding plan.' : 'Your personal internship plan at NovaTech.'}</p>
    <p className="fd-onboarding-intro">{description || (isProject ? 'Explore the project, prepare your environment, and make your first contribution.' : 'Follow your learning plan, complete practical tasks, and track your progress throughout your internship.')}</p>
  </header>;
}
