(() => {
    'use strict';
    const page = document.documentElement;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
    const button = document.querySelector('[data-motion-toggle]');
    let paused = false;
    let revealObserver = null;
    const revealAll = () => document.querySelectorAll('.af-reveal').forEach(el => el.classList.add('is-revealed'));
    const updateMotion = () => {
        const off = reduced.matches || paused;
        page.classList.toggle('af-motion-off', off);
        page.classList.toggle('af-motion-paused', document.hidden || off);
        if (button) {
            button.hidden = reduced.matches;
            button.setAttribute('aria-pressed', String(paused));
            button.textContent = paused ? 'Resume motion' : 'Pause motion';
        }
        if (off) {
            revealAll();
            revealObserver?.disconnect();
        }
    };
    button?.addEventListener('click', () => { paused = !paused; updateMotion(); });
    reduced.addEventListener('change', updateMotion);
    document.addEventListener('visibilitychange', updateMotion);
    updateMotion();

    if (!reduced.matches && 'IntersectionObserver' in window) {
        try {
            revealObserver = new IntersectionObserver(entries => entries.forEach(entry => {
                if (!entry.isIntersecting) return;
                entry.target.classList.add('is-revealed');
                revealObserver.unobserve(entry.target);
            }), { rootMargin: '0px 0px -30px 0px', threshold: 0.08 });
            document.querySelectorAll('.portal-feature-card,.portal-gallery-card,.news-card,.news-page-card,.info-faction-card,.realm-banner').forEach((el, i) => {
                if (el.getBoundingClientRect().top < window.innerHeight) return;
                el.style.setProperty('--af-reveal-delay', `${(i % 3) * 65}ms`);
                el.classList.add('af-reveal');
                revealObserver.observe(el);
            });
            const hero = document.querySelector('.aion-hero');
            if (hero) {
                const heroObserver = new IntersectionObserver(entries => entries.forEach(entry => {
                    hero.classList.toggle('af-hero-away', !entry.isIntersecting);
                }));
                heroObserver.observe(hero);
            }
        } catch (_) { revealAll(); }
    }

    const progress = document.createElement('div');
    progress.className = 'af-reading-progress';
    progress.setAttribute('aria-hidden', 'true');
    document.body.appendChild(progress);
    let scheduled = false;
    const readingPosition = () => {
        if (scheduled || reduced.matches || document.hidden) return;
        scheduled = true;
        requestAnimationFrame(() => {
            const length = page.scrollHeight - window.innerHeight;
            progress.style.setProperty('--af-reading', length > 0 ? Math.min(1, Math.max(0, window.scrollY / length)) : 0);
            scheduled = false;
        });
    };
    window.addEventListener('scroll', readingPosition, { passive: true });
    window.addEventListener('resize', readingPosition, { passive: true });
    readingPosition();

    let section = window.location.pathname;
    if (section.startsWith('/news/') && document.querySelector('.page-intro a[href$="/guides"]')) section = '/guides';
    document.querySelectorAll('.nav-links a').forEach(link => {
        const path = new URL(link.href, window.location.href).pathname;
        if (path === section || (path !== '/' && section.startsWith(path + '/'))) link.setAttribute('aria-current', 'page');
    });
})();
