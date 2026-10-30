(() => {
    'use strict';
    document.querySelectorAll('[data-play-checklist]').forEach(list => {
        const key = `aetherfall.play.${list.dataset.playChecklist}`;
        const steps = [...list.querySelectorAll('[data-step]')];
        const progress = list.querySelector('[data-checklist-progress]');
        const reset = list.querySelector('[data-checklist-reset]');
        let saved = [];
        try { const value = JSON.parse(localStorage.getItem(key) || '[]'); if (Array.isArray(value)) saved = value; } catch (_) {}
        steps.forEach(step => { step.checked = saved.includes(step.dataset.step); });
        const update = () => {
            const checked = steps.filter(step => step.checked).map(step => step.dataset.step);
            progress.textContent = `${checked.length} of ${steps.length} ready`;
            try { localStorage.setItem(key, JSON.stringify(checked)); } catch (_) {}
        };
        steps.forEach(step => step.addEventListener('change', update));
        reset.hidden = false;
        reset.addEventListener('click', () => { steps.forEach(step => { step.checked = false; }); update(); });
        update();
    });
    const menus = [...document.querySelectorAll('.hub-menu')];
    document.addEventListener('click', event => { menus.forEach(menu => { if (!menu.contains(event.target)) menu.open = false; }); });
    document.addEventListener('keydown', event => {
        if (event.key !== 'Escape') return;
        menus.forEach(menu => { if (menu.open) { menu.open = false; menu.querySelector('summary').focus(); } });
    });
})();
