/* Page script for /admin/subjects.html (kept out of the HTML so the Content-Security-Policy can forbid inline scripts). */
const esc = UI.esc;
let subjects = [];

async function load() {
    subjects = await Api.get('/api/admin/subjects');
    const rows = document.getElementById('rows');
    if (subjects.length === 0) {
        rows.innerHTML = '<tr><td colspan="5" class="empty">No subjects yet.</td></tr>';
        return;
    }
    rows.innerHTML = subjects.map(s => `
        <tr>
            <td><strong>${esc(s.code)}</strong></td>
            <td>${esc(s.name)}</td>
            <td>${s.hasTheory ? 'Yes' : '<span class="muted">No</span>'}</td>
            <td>${s.hasLab ? 'Yes' : '<span class="muted">No</span>'}</td>
            <td class="actions">
                <button class="icon-btn" data-edit="${s.id}" title="Edit" aria-label="Edit ${esc(s.code)}"><i data-lucide="pencil"></i></button>
                <button class="icon-btn danger" data-delete="${s.id}" title="Delete" aria-label="Delete ${esc(s.code)}"><i data-lucide="trash-2"></i></button>
            </td>
        </tr>`).join('');
    UI.icons();
}

function openForm(subject) {
    const s = subject || { code: '', name: '', hasTheory: true, hasLab: false };
    UI.formDialog({
        title: subject ? 'Edit subject' : 'Add subject',
        body: `
            <div class="field">
                <label for="code">Code</label>
                <input class="input" id="code" name="code" maxlength="20" value="${esc(s.code)}" placeholder="AOA">
            </div>
            <div class="field">
                <label for="name">Name</label>
                <input class="input" id="name" name="name" maxlength="100" value="${esc(s.name)}" placeholder="Analysis of Algorithms">
            </div>
            <div class="field">
                <label>Sessions</label>
                <label class="check"><input type="checkbox" name="hasTheory" ${s.hasTheory ? 'checked' : ''}>Theory</label>
                <label class="check"><input type="checkbox" name="hasLab" ${s.hasLab ? 'checked' : ''}>Lab</label>
                <input type="hidden" name="theoryOrLab">
            </div>`,
        onSubmit: async (form) => {
            const body = {
                code: form.code.value,
                name: form.name.value,
                hasTheory: form.hasTheory.checked,
                hasLab: form.hasLab.checked,
            };
            if (subject) {
                await Api.put(`/api/admin/subjects/${subject.id}`, body);
                UI.notice('Subject saved.');
            } else {
                await Api.post('/api/admin/subjects', body);
                UI.notice('Subject added.');
            }
            await load();
        },
    });
}

document.getElementById('add-btn').addEventListener('click', () => openForm(null));

document.getElementById('rows').addEventListener('click', async (event) => {
    const editBtn = event.target.closest('[data-edit]');
    const deleteBtn = event.target.closest('[data-delete]');
    if (editBtn) {
        openForm(subjects.find(s => s.id === Number(editBtn.dataset.edit)));
    } else if (deleteBtn) {
        const s = subjects.find(x => x.id === Number(deleteBtn.dataset.delete));
        if (await UI.confirm(`Delete ${s.code} (${s.name})?`)) {
            await UI.run(() => Api.del(`/api/admin/subjects/${s.id}`), 'Subject deleted.');
            await load();
        }
    }
});

(async () => {
    await Layout.init('subjects', { adminOnly: true });
    await load();
})();
