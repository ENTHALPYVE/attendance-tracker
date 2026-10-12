/* Page script for /session.html (kept out of the HTML so the Content-Security-Policy can forbid inline scripts). */
const esc = UI.esc;
const sessionId = UI.queryParam('id');
const backUrl = safeBack(UI.queryParam('back'));

let sheet = null;        // data from the server
let absent = new Set();  // student ids currently marked absent
let dirty = false;       // unsaved changes?
let user = null;
let draft = null;        // unsaved marks kept on this device, offered after a reload

// Unsaved marks are also kept in this browser (student ids only, no names) so that they
// survive a lost connection, a reload or the app being closed. Removed once saved.
const DRAFT_KEY = 'attendance-draft-' + sessionId;
const DRAFT_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000;

function storeDraft() {
    if (draft) {                     // marking again: the older draft is replaced by this one
        draft = null;
        document.getElementById('draft-offer')?.remove();
    }
    try {
        localStorage.setItem(DRAFT_KEY, JSON.stringify({ absent: [...absent], savedAt: Date.now() }));
    } catch (e) {
        // storage blocked: the marks are still on the screen
    }
}

function readDraft() {
    try {
        const d = JSON.parse(localStorage.getItem(DRAFT_KEY));
        if (d && Array.isArray(d.absent) && Date.now() - d.savedAt < DRAFT_MAX_AGE_MS) return d;
    } catch (e) {
        // ignore a damaged draft
    }
    clearDraft();
    return null;
}

function clearDraft() {
    try { localStorage.removeItem(DRAFT_KEY); } catch (e) { /* ignore */ }
}

/** Only allow going back to a page on this site. */
function safeBack(url) {
    return url && url.startsWith('/') && !url.startsWith('//') ? url : '/index.html';
}

// ------------------------------------------------------------ drawing

function draw() {
    const s = sheet.session;
    const page = document.getElementById('page');
    const title = `${esc(s.subjectCode)} ${s.sessionType === 'LAB' ? 'Lab' : 'Theory'}${s.batchName ? ` · ${esc(s.batchName)}` : ''}`;
    const meta = [esc(s.className), Sessions.formatDate(s.date, true),
                  `${Sessions.hhmm(s.startTime)}–${Sessions.hhmm(s.endTime)}`, s.room ? esc(s.room) : null]
        .filter(Boolean).join(' · ');

    let html = `
        <div class="page-header mark-header">
            <div>
                <h1>${title}</h1>
                <p>${meta}</p>
                <p class="small">${s.subjectName ? esc(s.subjectName) : ''}${s.extra ? ' · Extra session' : ''}</p>
            </div>
            <div>${Sessions.statusBadge(s)}</div>
        </div>`;

    if (s.date > Sessions.todayIso()) {
        html += `
            <div class="card">
                <p>This session is on ${Sessions.formatDate(s.date)}. Attendance can be marked on the day.</p>
                <p class="small muted">Teacher: ${esc(s.teacherName)}</p>
            </div>`;
        page.innerHTML = html + moreActions(s);
        document.querySelector('.save-bar')?.remove();
        bindActions();
        UI.icons();
        return;
    }

    if (s.status === 'CANCELLED') {
        html += `
            <div class="card">
                <p>This session is marked as cancelled, so it does not count in anyone's attendance.</p>
                <p><button class="btn btn-primary" id="reopen-btn" type="button"><i data-lucide="rotate-ccw"></i>Reopen session</button></p>
            </div>`;
        page.innerHTML = html + moreActions(s);
        document.querySelector('.save-bar')?.remove();
        bindActions();
        UI.icons();
        return;
    }

    html += `
        ${draft ? draftOffer() : ''}
        <div class="mark-summary" id="summary"></div>
        ${sheet.students.length === 0
            ? '<div class="card empty">No students in this ' + (s.batchName ? 'batch' : 'class') + ' yet.</div>'
            : `<ul class="mark-list" id="list">${sheet.students.map(rowHtml).join('')}</ul>`}
        ${moreActions(s)}`;
    page.innerHTML = html;

    if (!document.querySelector('.save-bar')) {
        document.body.insertAdjacentHTML('beforeend', `
            <div class="save-bar">
                <button class="btn" id="all-present-btn" type="button">Mark all present</button>
                <button class="btn btn-primary" id="save-btn" type="button">Save</button>
            </div>`);
        document.getElementById('save-btn').addEventListener('click', save);
        document.getElementById('all-present-btn').addEventListener('click', markAllPresent);
    }
    document.getElementById('list')?.addEventListener('click', toggle);
    bindActions();
    updateSummary();
    UI.icons();
}

function rowHtml(st) {
    const isAbsent = absent.has(st.studentId);
    const showBatch = sheet.session.sessionType === 'THEORY';
    return `
        <li>
            <button type="button" class="mark-row ${isAbsent ? 'is-absent' : ''}" data-id="${st.studentId}" aria-pressed="${isAbsent}">
                <span class="mark-roll">${esc(st.rollNo)}</span>
                <span class="mark-name">${esc(st.name)}${showBatch ? `<span class="mark-batch">${esc(st.batchName)}</span>` : ''}</span>
                <span class="mark-state">${isAbsent ? 'Absent' : 'Present'}</span>
            </button>
        </li>`;
}

function draftOffer() {
    const when = new Date(draft.savedAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
    return `
        <div class="alert alert-warning draft-offer" id="draft-offer">
            <span>Unsaved marks from ${esc(when)} were found on this device (${draft.absent.length} absent).</span>
            <span class="draft-actions">
                <button class="btn btn-sm btn-primary" id="draft-restore" type="button">Restore them</button>
                <button class="btn btn-sm" id="draft-discard" type="button">Discard</button>
            </span>
        </div>`;
}

function moreActions(s) {
    const buttons = [];
    if (user && user.role === 'ADMIN') {
        buttons.push('<button class="btn btn-sm" id="substitute-btn" type="button"><i data-lucide="user-round-cog"></i>Give to substitute</button>');
    }
    if (s.status !== 'CANCELLED' && s.date <= Sessions.todayIso()) {
        buttons.push('<button class="btn btn-sm" id="cancel-btn" type="button"><i data-lucide="ban"></i>Mark as cancelled</button>');
    }
    if (s.extra) {
        buttons.push('<button class="btn btn-sm btn-danger" id="delete-btn" type="button"><i data-lucide="trash-2"></i>Delete extra session</button>');
    }
    return buttons.length ? `<div class="more-actions">${buttons.join('')}</div>` : '';
}

function updateSummary() {
    const total = sheet.students.length;
    const a = absent.size;
    const summary = document.getElementById('summary');
    if (!summary) return;
    summary.innerHTML = `
        <span><strong class="num">${total - a}</strong> present</span>
        <span class="sep">·</span>
        <span class="${a ? 'absent-count' : ''}"><strong class="num">${a}</strong> absent</span>
        <span class="muted small">${sheet.saved ? (dirty ? 'Unsaved changes' : 'Saved') : 'Not saved yet'}</span>`;
}

// ------------------------------------------------------------ marking

function toggle(event) {
    const row = event.target.closest('.mark-row');
    if (!row) return;
    const id = Number(row.dataset.id);
    if (absent.has(id)) absent.delete(id); else absent.add(id);
    const isAbsent = absent.has(id);
    row.classList.toggle('is-absent', isAbsent);
    row.setAttribute('aria-pressed', isAbsent);
    row.querySelector('.mark-state').textContent = isAbsent ? 'Absent' : 'Present';
    dirty = true;
    storeDraft();
    updateSummary();
}

function markAllPresent() {
    if (absent.size === 0) return;
    absent.clear();
    document.querySelectorAll('.mark-row.is-absent').forEach(row => {
        row.classList.remove('is-absent');
        row.setAttribute('aria-pressed', 'false');
        row.querySelector('.mark-state').textContent = 'Present';
    });
    dirty = true;
    storeDraft();
    updateSummary();
}

async function save() {
    const button = document.getElementById('save-btn');
    if (!navigator.onLine) {
        UI.notice('No connection, so nothing was saved. Your marks are kept on this device; tap Save again when you are back online.', 'error');
        return;
    }
    button.disabled = true;
    button.textContent = 'Saving...';
    try {
        await Api.put(`/api/sessions/${sessionId}/attendance`, { absentStudentIds: [...absent] });
        dirty = false;
        clearDraft();
        UI.flash(`Attendance saved: ${sheet.students.length - absent.size} present, ${absent.size} absent.`);
        window.location.href = backUrl;
    } catch (error) {
        UI.notice(error.status === 0
            ? 'Could not reach the server, so nothing was saved. Your marks are kept on this device; tap Save to try again.'
            : error.message, 'error');
        button.disabled = false;
        button.textContent = 'Save';
    }
}

// ------------------------------------------------------------ cancel / reopen / delete

function bindActions() {
    document.getElementById('draft-restore')?.addEventListener('click', () => {
        const inList = new Set(sheet.students.map(st => st.studentId));
        absent = new Set(draft.absent.filter(id => inList.has(id)));
        draft = null;
        dirty = true;
        draw();
        UI.notice('Marks restored. Check them and tap Save.');
    });
    document.getElementById('draft-discard')?.addEventListener('click', () => {
        clearDraft();
        draft = null;
        document.getElementById('draft-offer')?.remove();
    });
    document.getElementById('substitute-btn')?.addEventListener('click', () =>
        Sessions.openSubstituteForm(sheet.session, load));
    document.getElementById('cancel-btn')?.addEventListener('click', async () => {
        const ok = await UI.confirm('Mark this session as cancelled? It will not count in attendance. You can reopen it later.',
            { title: 'Cancel session', confirmLabel: 'Mark as cancelled' });
        if (!ok) return;
        await UI.run(() => Api.post(`/api/sessions/${sessionId}/cancel`), 'Session marked as cancelled.');
        dirty = false;
        clearDraft();
        await load();
    });
    document.getElementById('reopen-btn')?.addEventListener('click', async () => {
        await UI.run(() => Api.post(`/api/sessions/${sessionId}/reopen`), 'Session reopened.');
        await load();
    });
    document.getElementById('delete-btn')?.addEventListener('click', async () => {
        const ok = await UI.confirm('Delete this extra session and any attendance saved for it?');
        if (!ok) return;
        await UI.run(() => Api.del(`/api/sessions/${sessionId}`));
        dirty = false;
        clearDraft();
        UI.flash('Extra session deleted.');
        window.location.href = backUrl;
    });
}

// ------------------------------------------------------------ start

async function load() {
    sheet = await Api.get(`/api/sessions/${sessionId}/attendance`);
    absent = new Set(sheet.students.filter(s => s.status === 'ABSENT').map(s => s.studentId));
    draft = readDraft();
    if (draft && sameMarks(draft.absent, absent)) {   // already saved on the server
        clearDraft();
        draft = null;
    }
    draw();
}

function sameMarks(ids, set) {
    return ids.length === set.size && ids.every(id => set.has(id));
}

window.addEventListener('beforeunload', (event) => {
    if (dirty) event.preventDefault();
});

(async () => {
    user = await Layout.init(null);
    document.getElementById('back-link').href = backUrl;
    if (!sessionId) {
        window.location.href = '/index.html';
        return;
    }
    try {
        await load();
    } catch (error) {
        document.getElementById('page').innerHTML = `<div class="card empty">${esc(error.message)}</div>`;
    }
})();
