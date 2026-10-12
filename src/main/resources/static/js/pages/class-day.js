/* Page script for /class-day.html (kept out of the HTML so the Content-Security-Policy can forbid inline scripts). */
const esc = UI.esc;
let user = null, classes = [];

const classSelect = document.getElementById('class-select');
const dateInput = document.getElementById('date-input');

function selectedClass() {
    return classes.find(c => c.id === Number(classSelect.value));
}

async function load() {
    const cls = selectedClass();
    const date = dateInput.value || Sessions.todayIso();
    history.replaceState(null, '', `?classId=${cls.id}&date=${date}`);
    document.getElementById('heading').textContent = cls.name;
    document.getElementById('date-label').textContent = Sessions.formatDate(date);

    const day = await Api.get(`/api/classes/${cls.id}/day?date=${date}`);
    document.getElementById('extra-btn').hidden = !day.open;

    const notices = [];
    if (day.holiday) notices.push(`Holiday: ${esc(day.holiday)}. Sessions are cancelled.`);
    if (!day.open && date > Sessions.todayIso()) notices.push('Planned timetable. Attendance can be marked on the day.'
        + (user.role === 'ADMIN' && date <= Sessions.addDays(Sessions.todayIso(), 14)
            ? ' Click a lecture to give it to a substitute.' : ''));
    if (!day.open && date <= Sessions.todayIso()) notices.push('This date is before the semester start.');
    document.getElementById('notice-box').innerHTML =
        notices.map(n => `<div class="alert alert-info">${n}</div>`).join('');

    daySessions = day.sessions;
    drawGrid(cls, day.sessions);
}

/** Rows = slots, columns = batches. A theory session fills the whole row. */
function drawGrid(cls, sessions) {
    const grid = document.getElementById('grid');
    const batches = cls.batches;
    if (sessions.length === 0) {
        grid.innerHTML = '<tbody><tr><td class="empty">No sessions on this day.</td></tr></tbody>';
        return;
    }
    const slots = [...new Set(sessions.map(s => s.slotNo))].sort((a, b) => a - b);

    let html = `<thead><tr><th class="day-slot">Slot</th>${batches.map(b => `<th>${esc(b.name)}</th>`).join('')}</tr></thead><tbody>`;
    for (const slot of slots) {
        const inSlot = sessions.filter(s => s.slotNo === slot);
        const first = inSlot[0];
        html += `<tr><td class="day-slot"><strong>${slot}</strong><span>${Sessions.hhmm(first.startTime)}</span></td>`;
        const theory = inSlot.find(s => s.sessionType === 'THEORY');
        if (theory) {
            html += `<td colspan="${Math.max(batches.length, 1)}">${cellHtml(theory)}</td>`;
        } else {
            for (const b of batches) {
                const lab = inSlot.find(s => s.batchId === b.id);
                html += `<td>${lab ? cellHtml(lab) : '<span class="day-free">Free</span>'}</td>`;
            }
        }
        html += '</tr>';
    }
    grid.innerHTML = html + '</tbody>';
    UI.icons();
}

/** Admin: a planned (not yet created) session in the next 14 days can be given to a substitute. */
function canArrangeCover(s) {
    return user.role === 'ADMIN' && s.id == null && s.timetableEntryId != null
        && s.date > Sessions.todayIso() && s.date <= Sessions.addDays(Sessions.todayIso(), 14);
}

function cellHtml(s) {
    if (canArrangeCover(s)) {
        return `
            <button type="button" class="day-cell ${s.sessionType === 'LAB' ? 'is-lab' : ''} is-mine" data-cover="${s.timetableEntryId}"
                    title="Give to a substitute">
                <span class="day-subject">${esc(s.subjectCode)} ${Sessions.typeLabel(s)}</span>
                <span class="day-meta">${esc(s.teacherName)}</span>
                ${s.room ? `<span class="day-meta">${esc(s.room)}</span>` : ''}
                <span class="day-status">${Sessions.statusBadge(s)}</span>
            </button>`;
    }
    const mine = Sessions.canChange(s, user);
    const tag = mine ? 'a' : 'div';
    const href = mine ? ` href="${Sessions.markUrl(s.id)}"` : '';
    const cssClass = ['day-cell', s.sessionType === 'LAB' ? 'is-lab' : '', mine ? 'is-mine' : '',
                      s.status === 'CANCELLED' ? 'is-cancelled' : ''].join(' ');
    return `
        <${tag} class="${cssClass}"${href}>
            <span class="day-subject">${esc(s.subjectCode)} ${Sessions.typeLabel(s)}</span>
            <span class="day-meta">${esc(s.teacherName)}</span>
            ${s.room ? `<span class="day-meta">${esc(s.room)}</span>` : ''}
            <span class="day-status">${Sessions.statusBadge(s)}</span>
        </${tag}>`;
}

function goTo(date) {
    dateInput.value = date;
    load();
}

let daySessions = [];
document.getElementById('grid').addEventListener('click', (event) => {
    const cover = event.target.closest('[data-cover]');
    if (!cover) return;
    const s = daySessions.find(x => x.id == null && x.timetableEntryId === Number(cover.dataset.cover));
    Sessions.openSubstituteForm(s, load);
});

classSelect.addEventListener('change', load);
dateInput.addEventListener('change', () => { if (dateInput.value) load(); });
document.getElementById('prev-day').addEventListener('click', () => goTo(Sessions.addDays(dateInput.value, -1)));
document.getElementById('next-day').addEventListener('click', () => goTo(Sessions.addDays(dateInput.value, 1)));
document.getElementById('today-btn').addEventListener('click', () => goTo(Sessions.todayIso()));
document.getElementById('extra-btn').addEventListener('click', () =>
    Sessions.openExtraForm({ user, classId: classSelect.value, date: dateInput.value }));

(async () => {
    user = await Layout.init('day');
    classes = await Api.get('/api/classes');
    if (classes.length === 0) {
        document.querySelector('.card').innerHTML = '<div class="empty">There are no classes yet.</div>';
        return;
    }
    classSelect.innerHTML = classes.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('');
    const wantedClass = UI.queryParam('classId');
    if (wantedClass && classes.some(c => String(c.id) === wantedClass)) classSelect.value = wantedClass;
    dateInput.value = UI.queryParam('date') || Sessions.todayIso();
    await load();
})();
