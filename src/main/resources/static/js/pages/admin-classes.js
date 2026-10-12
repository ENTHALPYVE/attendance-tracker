/* Page script for /admin/classes.html (kept out of the HTML so the Content-Security-Policy can forbid inline scripts). */
const esc = UI.esc;
let classes = [];

async function load() {
    classes = await Api.get('/api/admin/classes');
    const rows = document.getElementById('rows');
    if (classes.length === 0) {
        rows.innerHTML = '<tr><td colspan="4" class="empty">No classes yet.</td></tr>';
        return;
    }
    rows.innerHTML = classes.map(c => `
        <tr>
            <td><a href="/admin/class.html?id=${c.id}"><strong>${esc(c.name)}</strong></a></td>
            <td>${c.batches.length ? c.batches.map(b => esc(b.name)).join(', ') : '<span class="muted">None</span>'}</td>
            <td class="num">${c.studentCount}</td>
            <td class="actions">
                <a class="icon-btn" href="/admin/class.html?id=${c.id}" title="Open" aria-label="Open ${esc(c.name)}"><i data-lucide="chevron-right"></i></a>
                <button class="icon-btn" data-edit="${c.id}" title="Rename" aria-label="Rename ${esc(c.name)}"><i data-lucide="pencil"></i></button>
                <button class="icon-btn danger" data-delete="${c.id}" title="Delete" aria-label="Delete ${esc(c.name)}"><i data-lucide="trash-2"></i></button>
            </td>
        </tr>`).join('');
    UI.icons();
}

function openAddForm() {
    UI.formDialog({
        title: 'Add class',
        submitLabel: 'Add class',
        body: `
            <div class="field">
                <label for="name">Class name</label>
                <input class="input" id="name" name="name" maxlength="50" placeholder="SE-CO-A">
            </div>
            <div class="field">
                <label for="batches">Batches</label>
                <input class="input" id="batches" name="batches" value="B1, B2, B3">
                <div class="field-hint">Separate with commas. You can change batches later.</div>
            </div>`,
        onSubmit: async (form) => {
            const batchNames = form.batches.value.split(',').map(s => s.trim()).filter(Boolean);
            const created = await Api.post('/api/admin/classes', { name: form.name.value, batchNames });
            UI.notice(`${created.name} added.`);
            await load();
        },
    });
}

function openRenameForm(c) {
    UI.formDialog({
        title: 'Rename class',
        body: `
            <div class="field">
                <label for="name">Class name</label>
                <input class="input" id="name" name="name" maxlength="50" value="${esc(c.name)}">
            </div>`,
        onSubmit: async (form) => {
            await Api.put(`/api/admin/classes/${c.id}`, { name: form.name.value });
            UI.notice('Class renamed.');
            await load();
        },
    });
}

document.getElementById('add-btn').addEventListener('click', openAddForm);

document.getElementById('rows').addEventListener('click', async (event) => {
    const editBtn = event.target.closest('[data-edit]');
    const deleteBtn = event.target.closest('[data-delete]');
    if (editBtn) {
        openRenameForm(classes.find(c => c.id === Number(editBtn.dataset.edit)));
    } else if (deleteBtn) {
        const c = classes.find(x => x.id === Number(deleteBtn.dataset.delete));
        if (await UI.confirm(`Delete ${c.name} and its batches?`)) {
            await UI.run(() => Api.del(`/api/admin/classes/${c.id}`), 'Class deleted.');
            await load();
        }
    }
});

(async () => {
    await Layout.init('classes', { adminOnly: true });
    await load();
})();
