(() => {
    'use strict';
    const roles = ['tank', 'healer', 'support', 'melee', 'ranged'];
    const kinds = ['player', 'alt', 'temporary'];
    const roleNames = {tank:'Tank', healer:'Healer', support:'Support', melee:'Melee damage', ranged:'Ranged damage'};
    const kindNames = {player:'Player', alt:'Owned alt', temporary:'Temporary companion'};
    const plans = {
        validate(value, classKeys) {
            if (!value || value.schema !== 1 || !Array.isArray(value.members) || value.members.length !== 6) throw new Error('Invalid party plan');
            return {schema:1, members:value.members.map((member, index) => {
                if (!member || typeof member.class !== 'string' || (member.class !== '' && !classKeys.includes(member.class))
                    || (index === 0 && member.class === '') || !roles.includes(member.role) || !kinds.includes(member.kind)
                    || (index === 0 && member.kind !== 'player')) throw new Error('Invalid party member');
                return {class:member.class, role:member.role, kind:member.kind};
            })};
        },
        preset(name) {
            const row = (key, role, kind='temporary') => ({class:key,role,kind});
            const leader = row('TEMPLAR','tank','player');
            const empty = () => row('','ranged');
            const presets = {
                balanced:[leader,row('CLERIC','healer'),row('CHANTER','support'),row('GLADIATOR','melee'),row('SORCERER','ranged'),row('RANGER','ranged')],
                ranged:[leader,row('CLERIC','healer'),row('BARD','support'),row('SORCERER','ranged'),row('SPIRIT_MASTER','ranged'),row('GUNNER','ranged')],
                small:[leader,row('CLERIC','healer'),empty(),empty(),empty(),empty()],
                empty:[leader,empty(),empty(),empty(),empty(),empty()]
            };
            return {schema:1,members:presets[name] || presets.balanced};
        },
        summary(plan) {
            const members = plan.members.filter(member => member.class !== '');
            const counts = Object.fromEntries(roles.map(role => [role,members.filter(member => member.role === role).length]));
            return {members:members.length, players:members.filter(member=>member.kind==='player').length,
                companions:members.filter(member=>member.kind!=='player').length,roles:counts};
        },
        encode(plan) {
            const bytes = new TextEncoder().encode(JSON.stringify(plan));
            return btoa(String.fromCharCode(...bytes)).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
        },
        decode(value, classKeys) {
            if (typeof value !== 'string' || value.length > 6000 || !/^[A-Za-z0-9_-]+$/.test(value)) throw new Error('Invalid share link');
            const raw = atob(value.replace(/-/g,'+').replace(/_/g,'/'));
            return plans.validate(JSON.parse(new TextDecoder().decode(Uint8Array.from(raw, c=>c.charCodeAt(0)))),classKeys);
        }
    };
    if (typeof module === 'object' && module.exports) module.exports = plans;
    if (typeof document === 'undefined') return;

    async function copy(text) {
        if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable');
        await navigator.clipboard.writeText(text);
    }
    document.querySelectorAll('[data-copy-text]').forEach(button => {
        button.hidden = false;
        button.addEventListener('click',async () => {
            const status=document.querySelector('[data-copy-status]');
            try { await copy(button.dataset.copyText); status.textContent='Command copied. Paste it in game chat and replace any placeholders.'; }
            catch (_) { status.textContent='Clipboard access is unavailable. Select the command text to copy it manually.'; }
        });
    });

    const root=document.querySelector('[data-party-planner]');
    if (!root) return;
    const status=root.querySelector('[data-party-status]');
    const controls=root.querySelector('[data-planner-controls]');
    const slots=[...root.querySelectorAll('[data-party-slot]')].map(node=>({node,
        class:node.querySelector('[data-party-class]'),role:node.querySelector('[data-party-role]'),kind:node.querySelector('[data-party-kind]')}));
    const classKeys=[...slots[0].class.options].map(option=>option.value);
    const names=Object.fromEntries([...slots[0].class.options].map(option=>[option.value,option.textContent]));
    const storageKey='aetherfall.party-plan.v1';
    const message=text=>{status.textContent=text;};
    const capture=()=>plans.validate({schema:1,members:slots.map(slot=>({class:slot.class.value,role:slot.role.value,kind:slot.kind.value}))},classKeys);
    function update() {
        slots.forEach((slot,index)=>{
            const empty=slot.class.value==='';slot.node.classList.toggle('is-empty',empty);
            slot.role.disabled=empty;slot.kind.disabled=index===0 || empty;
        });
        const plan=capture(),summary=plans.summary(plan),counts=root.querySelector('[data-party-counts]');
        counts.replaceChildren();
        for (const [label,count] of [['Members',summary.members],['Players',summary.players],['Companions',summary.companions],...roles.map(role=>[roleNames[role],summary.roles[role]])]) {
            const item=document.createElement('div'),value=document.createElement('strong'),name=document.createElement('span');
            value.textContent=String(count);name.textContent=label;item.append(value,name);counts.append(item);
        }
        const advice=[];
        if (!summary.roles.tank) advice.push('Consider who will hold the frontline.');
        if (!summary.roles.healer) advice.push('Consider who will handle healing and recovery.');
        if (plan.members.some(member=>member.class!=='' && member.role==='healer' && !['CLERIC','CHANTER','BARD'].includes(member.class))) advice.push('Check that your planned healer has healing skills.');
        advice.push('Roles are a plan: check learned skills, equipment and supplies in game.');
        root.querySelector('[data-party-advice]').textContent=advice.join(' ');
        root.querySelector('[data-party-share-output]').hidden=true;
    }
    function render(plan) {
        plan.members.forEach((member,index)=>{
            slots[index].class.value=member.class;slots[index].role.value=member.role;slots[index].kind.value=member.kind;
        });
        update();
    }
    let initial=plans.preset('balanced'),initialMessage='Choose your classes and roles, then save or share your plan.';
    try {
        const saved=localStorage.getItem(storageKey);
        if (saved) { initial=plans.validate(JSON.parse(saved),classKeys); initialMessage='Your saved party plan is loaded.'; }
    } catch (_) { initialMessage='No usable saved plan was found. You can build a new one below.'; }
    const requestedClass=new URLSearchParams(window.location.search).get('class');
    if (classKeys.includes(requestedClass)) {
        initial.members[0].class=requestedClass;
        initial.members[0].role=[...slots[0].class.options].find(option=>option.value===requestedClass).dataset.defaultRole;
        initialMessage='Your chosen class is in the first slot. Adjust the rest of your party below.';
    }
    if (window.location.hash.startsWith('#party=')) {
        try { initial=plans.decode(window.location.hash.slice(7),classKeys); initialMessage='Shared plan loaded. Save it on this device if you want to keep it.'; }
        catch (_) { initialMessage='This share link could not be read. Your saved plan or a starting formation is shown instead.'; }
    }
    render(initial);message(initialMessage);controls.hidden=false;
    root.querySelector('[data-party-form]').addEventListener('submit',event=>event.preventDefault());
    root.querySelector('[data-party-form]').addEventListener('change',event=>{
        const slot=slots.find(slot=>slot.class===event.target);
        if (slot && slot.class.value!=='') slot.role.value=slot.class.selectedOptions[0].dataset.defaultRole;
        update();message('Plan changed. Choose Save to keep these choices on this device.');
    });
    root.querySelector('[data-party-apply]').addEventListener('click',()=>{
        render(plans.preset(root.querySelector('[data-party-preset]').value));
        message('Starting formation applied. Your saved plan stays available until you save these choices.');
    });
    root.querySelector('[data-party-save]').addEventListener('click',()=>{
        try { localStorage.setItem(storageKey,JSON.stringify(capture()));message('Party plan saved on this device.'); }
        catch (_) { message('Browser storage is unavailable. Create a share link to keep your plan.'); }
    });
    root.querySelector('[data-party-share]').addEventListener('click',async ()=>{
        const url=new URL(window.location.href);url.search='';url.hash='party='+plans.encode(capture());
        const output=root.querySelector('[data-party-link]');output.value=url.href;
        root.querySelector('[data-party-share-output]').hidden=false;output.focus();output.select();
        try { await copy(url.href);message('Share link copied. It contains your class, role and member choices.'); }
        catch (_) { message('Share link ready. Copy it from the field above.'); }
    });
    root.querySelector('[data-party-copy]').addEventListener('click',async ()=>{
        const lines=['Aetherfall party plan',...capture().members.map((member,index)=>member.class!==''
            ? `${index+1}. ${names[member.class]} / ${roleNames[member.role]} / ${kindNames[member.kind]}` : `${index+1}. Empty slot`)];
        const text=lines.join('\n');
        try { await copy(text);message('Party roster copied.'); }
        catch (_) {
            message('Clipboard access is unavailable. Use Create Share Link to keep or share this composition.');
        }
    });
})();
