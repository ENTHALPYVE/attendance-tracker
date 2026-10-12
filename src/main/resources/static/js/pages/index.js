/* Page script for /index.html (kept out of the HTML so the Content-Security-Policy can forbid inline scripts). */
const esc = UI.esc;

/** Teacher: "My sessions today" in time order, across all classes. */
async function showTeacherDay(user) {
    document.getElementById('actions').innerHTML =
        '<button class="btn" id="extra-btn" type="button"><i data-lucide="plus"></i>Extra session</button>';
    document.getElementById('extra-btn').addEventListener('click', () => Sessions.openExtraForm({ user }));

    const sessions = await Api.get('/api/me/sessions');
    const content = document.getElementById('content');
    if (sessions.length === 0) {
        content.innerHTML = '<div class="card empty">No sessions for you today.</div>';
    } else {
        const pending = sessions.filter(s => s.status === 'PENDING').length;
        content.innerHTML = `
            <div class="section-head"><h2>My sessions today</h2></div>
            <p class="muted small">${sessions.length} sessions${pending ? ` · ${pending} to mark` : ' · all marked'}</p>
            <div class="session-list">${sessions.map(sessionCard).join('')}</div>`;
    }
    content.insertAdjacentHTML('beforeend', `
        <section class="section dashboard">
            <div class="section-head"><h2>My classes</h2><span class="small muted">Semester so far</span></div>
            <div id="cards" class="card-grid"><div class="card empty">Loading...</div></div>
        </section>`);
    UI.icons();
    await showDashboard();
}

/** Teacher dashboard: one card per subject / batch taught. */
async function showDashboard() {
    const cards = await Api.get('/api/dashboard/teacher');
    const box = document.getElementById('cards');
    if (cards.length === 0) {
        box.innerHTML = '<div class="card empty">You are not in the timetable yet.</div>';
        return;
    }
    box.innerHTML = cards.map(c => {
        const title = `${esc(c.subjectCode)} ${c.sessionType === 'LAB' ? `Lab · ${esc(c.batchName)}` : 'Theory'}`;
        const pendingLinks = c.pending.map(p =>
            `<a href="${Sessions.markUrl(p.id)}">${Sessions.formatDate(p.date, true)}</a>`).join('');
        return `
            <div class="card class-card">
                <h3>${title}</h3>
                <div class="card-meta">${esc(c.className)} · ${c.students} students · ${esc(c.subjectName)}</div>
                <div class="card-stats">
                    <div><div class="label">Average</div><div class="value">${c.averagePercent == null ? '–' : c.averagePercent.toFixed(1) + '%'}</div></div>
                    <div><div class="label">Defaulters</div><div class="value">${c.defaulters
                        ? `<span class="badge badge-red">${c.defaulters}</span>` : '0'}</div></div>
                    <div><div class="label">Taken</div><div class="value">${c.sessionsTaken}</div></div>
                </div>
                ${c.pendingCount ? `
                <div class="card-pending">
                    <span class="badge badge-amber">${c.pendingCount} to mark</span>
                    ${pendingLinks}${c.pendingCount > c.pending.length ? '<span class="muted">…</span>' : ''}
                </div>` : ''}
                <div class="card-links">
                    <a href="/reports.html?classId=${c.classId}&tab=overall">Class report</a>
                </div>
            </div>`;
    }).join('');
}

function sessionCard(s) {
    return `
        <a class="session-card ${s.status === 'CANCELLED' ? 'is-cancelled' : ''}" href="${Sessions.markUrl(s.id)}">
            <div class="session-time">
                <strong>${Sessions.hhmm(s.startTime)}</strong>
                <span>${Sessions.hhmm(s.endTime)}</span>
            </div>
            <div class="session-body">
                <div class="session-title">${esc(s.subjectCode)} ${Sessions.typeLabel(s)}${s.extra ? ' <span class="tt-type">Extra</span>' : ''}</div>
                <div class="session-meta">
                    ${esc(s.className)}${s.batchName ? ` · ${esc(s.batchName)}` : ' · Whole class'}${s.room ? ` · ${esc(s.room)}` : ''}
                </div>
            </div>
            <div class="session-status">
                ${Sessions.statusBadge(s)}
                <span class="small muted">${Sessions.countText(s)}</span>
            </div>
        </a>`;
}

/** Admin: counts and shortcuts. */
async function showAdminHome() {
    const s = await Api.get('/api/admin/summary');
    document.getElementById('content').innerHTML = `
        <div class="stat-grid">
            ${[['Classes', s.classes, '/admin/classes.html'], ['Students', s.students, '/admin/classes.html'],
               ['Subjects', s.subjects, '/admin/subjects.html'], ['Teachers', s.teachers, '/admin/teachers.html']]
                .map(([label, value, href]) => `
                <a class="card stat stat-link" href="${href}">
                    <div class="label">${label}</div>
                    <div class="value">${value}</div>
                </a>`).join('')}
        </div>
        <p><a class="btn" href="/class-day.html"><i data-lucide="calendar-range"></i>Open today's class timetable</a></p>`;
    UI.icons();
}

(async () => {
    const user = await Layout.init('home');
    document.getElementById('today-date').textContent = Sessions.formatDate(Sessions.todayIso());
    document.getElementById('heading').textContent = 'Hello, ' + Layout.displayName(user);
    if (user.role === 'ADMIN') {
        await showAdminHome();
    } else {
        await showTeacherDay(user);
    }
})();
