/* Page script for /admin/settings.html (kept out of the HTML so the Content-Security-Policy can forbid inline scripts). */
const esc = UI.esc;
let holidays = [];

async function loadRules() {
    const s = await Api.get('/api/settings');
    const form = document.getElementById('rules-form');
    form.threshold.value = s.threshold;
    form.semesterStart.value = s.semesterStart || '';
    form.countFreeSlotsAsPresent.checked = s.countFreeSlotsAsPresent;
}

document.getElementById('rules-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    const button = form.querySelector('[type=submit]');
    button.disabled = true;
    try {
        await Api.put('/api/admin/settings', {
            threshold: Number(form.threshold.value),
            semesterStart: form.semesterStart.value || null,
            countFreeSlotsAsPresent: form.countFreeSlotsAsPresent.checked,
        });
        form.querySelector('.form-error').hidden = true;
        form.querySelectorAll('.field-error').forEach(el => el.remove());
        UI.notice('Rules saved. Reports now use them.');
    } catch (error) {
        const box = form.querySelector('.form-error');
        const fields = (error.data && error.data.fieldErrors) || {};
        box.textContent = Object.values(fields).join(' ') || error.message;
        box.hidden = false;
    } finally {
        button.disabled = false;
    }
});

async function loadHolidays() {
    holidays = await Api.get('/api/admin/holidays');
    const body = document.getElementById('holidays');
    body.innerHTML = holidays.map(h => `
        <tr>
            <td class="nowrap">${new Date(h.date + 'T00:00:00').toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })}</td>
            <td>${esc(h.reason)}</td>
            <td class="num hide-sm">${h.cancelledSessions}</td>
            <td class="actions">
                <button class="icon-btn danger" data-delete="${h.id}" title="Delete" aria-label="Delete holiday ${esc(h.reason)}"><i data-lucide="trash-2"></i></button>
            </td>
        </tr>`).join('') || '<tr><td colspan="4" class="empty">No holidays yet.</td></tr>';
    UI.icons();
}

document.getElementById('add-holiday-btn').addEventListener('click', () => {
    UI.formDialog({
        title: 'Add holiday',
        body: `
            <div class="field">
                <label for="date">Date</label>
                <input class="input" id="date" name="date" type="date">
            </div>
            <div class="field">
                <label for="reason">Reason</label>
                <input class="input" id="reason" name="reason" maxlength="150" placeholder="Diwali">
            </div>`,
        onSubmit: async (form) => {
            const h = await Api.post('/api/admin/holidays', { date: form.date.value || null, reason: form.reason.value });
            UI.notice(h.cancelledSessions ? `Holiday added; ${h.cancelledSessions} sessions cancelled.` : 'Holiday added.');
            await loadHolidays();
        },
    });
});

document.getElementById('holidays').addEventListener('click', async (event) => {
    const btn = event.target.closest('[data-delete]');
    if (!btn) return;
    const h = holidays.find(x => x.id === Number(btn.dataset.delete));
    if (await UI.confirm(`Delete the holiday on ${Sessions.formatDate(h.date)} (${h.reason})? Its sessions will be reopened.`)) {
        await UI.run(() => Api.del(`/api/admin/holidays/${h.id}`), 'Holiday deleted.');
        await loadHolidays();
    }
});

(async () => {
    await Layout.init('settings', { adminOnly: true });
    await Promise.all([loadRules(), loadHolidays()]);
})();
