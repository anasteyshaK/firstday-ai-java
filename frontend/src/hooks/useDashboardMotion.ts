import { useEffect, useRef } from 'react';

/** Progressive enhancement: the dashboard stays visible when motion is unavailable. */
export default function useDashboardMotion() {
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const root = rootRef.current;
    if (!root || !window.matchMedia || !window.IntersectionObserver || !Element.prototype.animate) return;
    const preference = window.matchMedia('(prefers-reduced-motion: reduce)');
    let stopMotion = () => {};

    function startMotion() {
      if (!root) return () => {};
      const targets = Array.from(root.querySelectorAll<HTMLElement>('[data-reveal]'));
      const animations = new Map<HTMLElement, Animation>();
      let frame = 0;
      let stopped = false;
      let viewportHeight = window.innerHeight;
      let strength = window.innerWidth <= 650 ? 0.5 : 1;
      let rootTop = 0;
      let welcomeCenter = 0;
      let waveCenter = 0;
      const current = { orb: 0, welcome: 0, wave: 0 };
      root.dataset.motion = 'active';

      function reveal(target: HTMLElement, immediate = false) {
        if (target.dataset.revealState === 'visible' && !immediate) return;
        target.dataset.revealState = 'visible';
        animations.get(target)?.cancel();
        animations.delete(target);
        if (immediate) return;
        const fadeOnly = target.dataset.reveal === 'fade';
        const animation = target.animate([
          { opacity: 0, transform: fadeOnly ? 'none' : 'translate3d(0, 20px, 0)' },
          { opacity: 1, transform: 'none' },
        ], {
          duration: 700,
          delay: Math.min(240, Number(target.dataset.revealDelay) || 0),
          easing: 'cubic-bezier(.2,.7,.2,1)',
          fill: 'backwards',
        });
        animations.set(target, animation);
        animation.finished.then(() => {
          if (animations.get(target) === animation) animations.delete(target);
        }).catch(() => { /* Cancellation on focus, navigation or preference changes is normal. */ });
      }

      const observer = new IntersectionObserver(entries => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            reveal(entry.target as HTMLElement);
            observer.unobserve(entry.target);
          }
        }
      }, { threshold: 0.08 });
      for (const target of targets) {
        target.dataset.revealState = 'pending';
        if (target.contains(document.activeElement)) reveal(target, true);
        else observer.observe(target);
      }

      // Only decorative layers move. Text, controls and page scroll stay native.
      const hero = root.querySelector('.fd-dash-hero');
      const heroObserver = new IntersectionObserver(entries => {
        root.dataset.heroVisible = String(entries.some(entry => entry.isIntersecting));
      });
      if (hero) heroObserver.observe(hero);
      const welcome = root.querySelector('.fd-dash-welcome');
      const wave = root.querySelector('.fd-dash-progress');
      const clamp = (value: number, limit: number) => Math.max(-limit, Math.min(limit, value));

      function paint() {
        frame = 0;
        if (stopped) return;
        const scroll = window.scrollY;
        const target = {
          orb: clamp((scroll - rootTop) * 0.12, 48) * strength,
          welcome: clamp((scroll + viewportHeight / 2 - welcomeCenter) * 0.06, 10) * strength,
          wave: clamp((scroll + viewportHeight / 2 - waveCenter) * 0.045, 7) * strength,
        };
        let moving = false;
        for (const key of ['orb', 'welcome', 'wave'] as const) {
          const delta = target[key] - current[key];
          current[key] = Math.abs(delta) < 0.08 ? target[key] : current[key] + delta * 0.13;
          if (Math.abs(delta) >= 0.08) moving = true;
          root!.style.setProperty(`--fd-motion-${key}`, `${current[key].toFixed(2)}px`);
        }
        if (moving) frame = requestAnimationFrame(paint);
      }
      function schedule() {
        if (!frame && !stopped) frame = requestAnimationFrame(paint);
      }
      function measure() {
        viewportHeight = window.innerHeight;
        strength = window.innerWidth <= 650 ? 0.5 : 1;
        rootTop = root!.getBoundingClientRect().top + window.scrollY;
        const welcomeBounds = welcome?.getBoundingClientRect();
        const waveBounds = wave?.getBoundingClientRect();
        welcomeCenter = welcomeBounds ? welcomeBounds.top + window.scrollY + welcomeBounds.height / 2 : rootTop;
        waveCenter = waveBounds ? waveBounds.top + window.scrollY + waveBounds.height / 2 : rootTop;
        schedule();
      }
      function focus(event: FocusEvent) {
        const element = event.target;
        if (!(element instanceof Element)) return;
        const target = element.closest<HTMLElement>('[data-reveal]');
        if (target && root!.contains(target)) {
          reveal(target, true);
          observer.unobserve(target);
        }
      }
      const resizeObserver = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(measure) : null;
      resizeObserver?.observe(root);
      window.addEventListener('scroll', schedule, { passive: true });
      window.addEventListener('resize', measure, { passive: true });
      root.addEventListener('focusin', focus);
      measure();

      return () => {
        stopped = true;
        cancelAnimationFrame(frame);
        observer.disconnect();
        heroObserver.disconnect();
        resizeObserver?.disconnect();
        window.removeEventListener('scroll', schedule);
        window.removeEventListener('resize', measure);
        root.removeEventListener('focusin', focus);
        for (const animation of animations.values()) animation.cancel();
        for (const target of targets) delete target.dataset.revealState;
        delete root.dataset.motion;
        delete root.dataset.heroVisible;
        for (const key of ['orb', 'welcome', 'wave']) root.style.removeProperty(`--fd-motion-${key}`);
      };
    }

    function applyPreference() {
      stopMotion();
      stopMotion = preference.matches ? () => {} : startMotion();
    }
    applyPreference();
    preference.addEventListener('change', applyPreference);
    return () => {
      preference.removeEventListener('change', applyPreference);
      stopMotion();
    };
  }, []);

  return rootRef;
}
