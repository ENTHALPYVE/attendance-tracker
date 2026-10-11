/*
 * Helpers shared by the pages that show sessions:
 * dates and times, status badges, links to the marking page and the "Add extra session" form.
 */
const Sessions = (() => {
    const esc = UI.esc;

    const hhmm = (t) => (t ? t.slice(0, 5) : '');

    /** Today as YYYY-MM-DD in the browser's time zone. */
    function todayIso() {
        const d = new Date();
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    }

    function addDays(iso, days) {
        const d = new Date(iso + 'T00:00:00');
        d.setDate(d.getDate() + days);
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    }

    /** "Monday, 12 October 2026" or the short form "Mon 12 Oct". */
    function formatDate(iso, short = false) {
        const d = new Date(iso + 'T00:00:00');
        return d.toLocaleDateString('en-IN', short
            ? { weekday: 'short', day: 'numeric', month: 'short' }
            : { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
    }

    function statusBadge(s) {
        if (s.status === 'TAKEN') {
            return '<span class="badge badge-green status"><i data-lucide="check"></i>Done</span>';
        }
        if (s.status === 'CANCELLED') {
            return '<span class="badge status status-cancelled">Cancelled</span>';
        }
        if (s.status === 'PENDING') {
            return '<span class="badge status">Pending</span>';
        }
        return '<span class="badge status">Planned</span>';
    }

    function typeLabel(s) {
        return `<span class="tt-type">${s.sessionType === 'LAB' ? 'Lab' : 'Theory'}</span>`;
    }

    /** "18/20 present" for saved sessions. */
    function countText(s) {
        if (s.status !== 'TAKEN' || s.presentCount == null) return '';
        return `${s.presentCount}/${s.presentCount + s.absentCount} present`;
    }

    /** Link to the marking page; "back" brings the user to the current page afterwards. */
    function markUrl(sessionId) {
        const back = window.location.pathname + window.location.search;
        return `/session.html?id=${sessionId}&back=${encodeURIComponent(back)}`;
    }

    function canChange(s, user) {
        return s.id != null && (user.role === 'ADMIN' || s.teacherId === user.id);
    }

    // ------------------------------------------------------------ extra session form

    /**
     * Opens the "Add extra session" form. On success the user goes straight to marking it.
     *   user     - the logged-in user
     *   classId  - class to preselect (optional)
     *   date     - date to preselect (default today)
     */
    async function openExtraForm({ user, classId, date }) {
        const requests = [Api.get('/api/classes'), Api.get('/api/subjects')];
        if (user.role === 'ADMIN') requests.push(Api.get('/api/admin/teachers'));
        const [classes, subjects, teachers = []] = await Promise.all(requests);
        if (classes.length === 0) {
            UI.notice('There are no classes yet.', 'error');
            return;
        }
        const today = todayIso();

        const { form } = UI.formDialog({
            title: 'Add extra session',
            submitLabel: 'Add and mark attendance',
            body: `
                <p class="small muted" style="margin:0 0 12px">For a lecture or lab that is not in the timetable.
                    Use a slot number that is free on that day (e.g. 6).</p>
                <div class="form-row">
                    <div class="field">
                        <label for="classId">Class</label>
                        <select class="input" id="classId" name="classId">
                            ${classes.map(c => `<option value="${c.id}" ${c.id === Number(classId) ? 'selected' : ''}>${esc(c.name)}</option>`).join('')}
                        </select>
                    </div>
                    <div class="field">
                        <label for="date">Date</label>
                        <input class="input" id="date" name="date" type="date" max="${today}" value="${date || today}">
                    </div>
                </div>
                <div class="form-row">
                    <div class="field">
                        <label for="slotNo">Slot</label>
                        <input class="input" id="slotNo" name="slotNo" type="number" min="1" max="12" value="6">
                    </div>
                    <div class="field">
                        <label for="room">Room</label>
                        <input class="input" id="room" name="room" maxlength="30">
                    </div>
                </div>
                <div class="form-row">
                    <div class="field">
                        <label for="startTime">Start</label>
                        <input class="input" id="startTime" name="startTime" type="time">
                    </div>
                    <div class="field">
                        <label for="endTime">End</label>
                        <input class="input" id="endTime" name="endTime" type="time">
                    </div>
                </div>
                <div class="field">
                    <label>Type</label>
                    <label class="check"><input type="radio" name="sessionType" value="THEORY" checked>Theory (whole class)</label>
                    <label class="check"><input type="radio" name="sessionType" value="LAB">Lab (one batch)</label>
                </div>
                <div class="field" id="batch-field" hidden>
                    <label for="batchId">Batch</label>
                    <select class="input" id="batchId" name="batchId"></select>
                </div>
                <div class="field">
                    <label for="subjectId">Subject</label>
                    <select class="input" id="subjectId" name="subjectId"></select>
                </div>
                ${user.role === 'ADMIN' ? `
                <div class="field">
                    <label for="teacherId">Teacher</label>
                    <select class="input" id="teacherId" name="teacherId">
                        <option value="">Choose a teacher</option>
                        ${teachers.map(t => `<option value="${t.id}">${esc(t.name)}</option>`).join('')}
                    </select>
                </div>` : ''}`,
            onSubmit: async (form) => {
                const type = form.sessionType.value;
                const created = await Api.post('/api/sessions/extra', {
                    classId: Number(form.classId.value),
                    date: form.date.value || null,
                    slotNo: Number(form.slotNo.value),
                    startTime: form.startTime.value || null,
                    endTime: form.endTime.value || null,
                    sessionType: type,
                    batchId: type === 'LAB' ? Number(form.batchId.value) : null,
                    subjectId: Number(form.subjectId.value) || null,
                    room: form.room.value,
                    teacherId: form.teacherId ? (Number(form.teacherId.value) || null) : null,
                });
                window.location.href = markUrl(created.id);
            },
        });

        const update = () => {
            const type = form.sessionType.value;
            const cls = classes.find(c => c.id === Number(form.classId.value));
            form.querySelector('#batch-field').hidden = type !== 'LAB';
            form.batchId.innerHTML = cls.batches.map(b => `<option value="${b.id}">${esc(b.name)}</option>`).join('');
            const current = Number(form.subjectId.value);
            form.subjectId.innerHTML = '<option value="">Choose a subject</option>' + subjects
                .filter(s => type === 'THEORY' ? s.hasTheory : s.hasLab)
                .map(s => `<option value="${s.id}" ${s.id === current ? 'selected' : ''}>${esc(s.code)} – ${esc(s.name)}</option>`)
                .join('');
        };
        form.querySelectorAll('[name=sessionType]').forEach(r => r.addEventListener('change', update));
        form.classId.addEventListener('change', update);
        update();
    }

    // ------------------------------------------------------------ substitute teacher (admin)

    /**
     * Gives a session to a substitute teacher.
     *   s      - an existing session (has id) or a planned row of an upcoming day (has timetableEntryId and date)
     *   onDone - called after saving
     */
    async function openSubstituteForm(s, onDone) {
        const teachers = (await Api.get('/api/admin/teachers')).filter(t => t.id !== s.teacherId);
        const what = `${esc(s.subjectCode)} ${s.sessionType === 'LAB' ? 'Lab · ' + esc(s.batchName) : 'Theory'}`;
        UI.formDialog({
            title: 'Give to a substitute',
            submitLabel: 'Save',
            body: `
                <p style="margin:0 0 12px">${what} · ${esc(s.className)} · ${formatDate(s.date, true)},
                    ${hhmm(s.startTime)}–${hhmm(s.endTime)}<br>
                    <span class="small muted">Now: ${esc(s.teacherName)}</span></p>
                <div class="field">
                    <label for="teacherId">Substitute teacher</label>
                    <select class="input" id="teacherId" name="teacherId">
                        <option value="">Choose a teacher</option>
                        ${teachers.map(t => `<option value="${t.id}">${esc(t.name)}</option>`).join('')}
                    </select>
                    <div class="field-hint">The substitute will see it in "My sessions today" and can mark attendance.</div>
                </div>`,
            onSubmit: (form) => saveSubstitute(s, Number(form.teacherId.value) || null, false, onDone),
        });
    }

    async function saveSubstitute(s, teacherId, allowClash, onDone) {
        try {
            if (s.id != null) {
                await Api.put(`/api/admin/sessions/${s.id}/teacher`, { teacherId, allowClash });
            } else {
                await Api.post('/api/admin/sessions/upcoming',
                    { teacherId, timetableEntryId: s.timetableEntryId, date: s.date, allowClash });
            }
        } catch (error) {
            if (error.status === 409 && error.data && error.data.clash) {
                const ok = await UI.confirm(error.message + ' Give it to them anyway?',
                    { title: 'Teacher is busy', confirmLabel: 'Give anyway', danger: false });
                if (ok) return saveSubstitute(s, teacherId, true, onDone);
            }
            throw error;
        }
        UI.notice('Substitute saved.');
        if (onDone) await onDone();
    }

    return { hhmm, todayIso, addDays, formatDate, statusBadge, typeLabel, countText, markUrl, canChange,
             openExtraForm, openSubstituteForm };
})();
