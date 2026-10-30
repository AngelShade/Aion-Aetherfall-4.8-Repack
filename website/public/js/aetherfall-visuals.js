/* One motion policy for the hero, reveals, transitions and saved visitor choice. */
(() => {
    'use strict';
    const page = document.documentElement;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
    const buttons = [...document.querySelectorAll('[data-motion-toggle]')];
    let preference = null;
    try { preference = localStorage.getItem('aetherfall-motion'); } catch (_) { /* Private browsing can block storage. */ }
    let revealObserver = null;
    const motionEnabled = () => preference === 'on' || (preference !== 'off' && !reduced.matches);
    const revealAll = () => document.querySelectorAll('.af-reveal').forEach(el => el.classList.add('is-revealed'));
    const notifyMotion = () => document.dispatchEvent(new CustomEvent('af:motionchange'));
    const updateMotion = () => {
        const enabled = motionEnabled();
        page.classList.toggle('af-motion-enabled', enabled);
        page.classList.toggle('af-motion-off', !enabled);
        page.classList.toggle('af-motion-paused', document.hidden || !enabled);
        buttons.forEach(button => {
            button.hidden = false;
            button.setAttribute('aria-pressed', String(enabled));
            button.textContent = enabled ? 'Motion on' : 'Motion off';
            button.title = enabled ? 'Turn off decorative motion' : 'Turn on decorative motion';
        });
        if (!enabled) { revealAll(); revealObserver?.disconnect(); }
        notifyMotion();
    };
    buttons.forEach(button => button.addEventListener('click', () => {
        preference = motionEnabled() ? 'off' : 'on';
        try { localStorage.setItem('aetherfall-motion', preference); } catch (_) { /* The choice still works for this page. */ }
        updateMotion();
    }));
    reduced.addEventListener('change', updateMotion);
    document.addEventListener('visibilitychange', updateMotion);
    updateMotion();

    if (motionEnabled() && 'IntersectionObserver' in window) {
        try {
            revealObserver = new IntersectionObserver(entries => entries.forEach(entry => {
                if (!entry.isIntersecting) return;
                entry.target.classList.add('is-revealed');
                revealObserver.unobserve(entry.target);
            }), { rootMargin: '0px 0px -24px 0px', threshold: 0.04 });
            document.querySelectorAll('.portal-features,.portal-gallery-card,.news-card,.news-page-card,.info-faction-card,.realm-banner,.af-continue-card,.aion-home-explore-card').forEach((el, i) => {
                if (el.getBoundingClientRect().top < window.innerHeight) return;
                el.style.setProperty('--af-reveal-delay', `${(i % 3) * 55}ms`);
                el.classList.add('af-reveal');
                revealObserver.observe(el);
            });
        } catch (_) { revealAll(); }
    }
    // Continuous hero effects and the existing slideshow sleep when off-screen.
    const hero = document.querySelector('.aion-hero');
    if (hero && 'IntersectionObserver' in window) {
        const heroObserver = new IntersectionObserver(entries => entries.forEach(entry => {
            hero.classList.toggle('af-hero-away', !entry.isIntersecting);
            notifyMotion();
        }));
        heroObserver.observe(hero);
    }

    document.querySelectorAll('[data-feature-showcase]').forEach(showcase => {
        const tablist = showcase.querySelector('[data-feature-tabs]');
        const grid = showcase.querySelector('[data-feature-panels]');
        const tabs = [...showcase.querySelectorAll('[data-feature-tab]')];
        const panels = [...showcase.querySelectorAll('[data-feature-panel]')];
        if (!tablist || !grid || !tabs.length || tabs.length !== panels.length) return;
        const select = (index, focus = false) => {
            tabs.forEach((tab, i) => {
                tab.setAttribute('aria-selected', String(i === index));
                tab.tabIndex = i === index ? 0 : -1;
                panels[i].hidden = i !== index;
                panels[i].setAttribute('role', 'tabpanel');
                panels[i].setAttribute('aria-labelledby', tab.id);
            });
            if (focus) tabs[index].focus();
        };
        tabs.forEach((tab, i) => {
            tab.addEventListener('click', () => select(i));
            tab.addEventListener('keydown', event => {
                let next = i;
                if (event.key === 'ArrowRight') next = (i + 1) % tabs.length;
                else if (event.key === 'ArrowLeft') next = (i + tabs.length - 1) % tabs.length;
                else if (event.key === 'Home') next = 0;
                else if (event.key === 'End') next = tabs.length - 1;
                else return;
                event.preventDefault();
                select(next, true);
            });
        });
        grid.classList.add('af-feature-deck');
        tablist.hidden = false;
        select(0);
    });

    const progress = document.createElement('div');
    progress.className = 'af-reading-progress';
    progress.setAttribute('aria-hidden', 'true');
    document.body.appendChild(progress);
    let scheduled = false;
    const readingPosition = () => {
        if (scheduled || !motionEnabled() || document.hidden) return;
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
