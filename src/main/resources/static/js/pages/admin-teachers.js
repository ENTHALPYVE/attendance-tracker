/* Page script for /admin/teachers.html (kept out of the HTML so the Content-Security-Policy can forbid inline scripts). */
const esc = UI.esc;
let teachers = [];

async function load() {
    teachers = await Api.get('/api/admin/teachers');
    const rows = document.getElementById('rows');
    if (teachers.length === 0) {
        rows.innerHTML = '<tr><td colspan="3" class="empty">No teachers yet.</td></tr>';
        return;
    }
    rows.innerHTML = teachers.map(t => `
        <tr>
            <td>${esc(t.name)}</td>
            <td class="muted">${esc(t.email)}</td>
            <td class="actions">
                <button class="icon-btn" data-edit="${t.id}" title="Edit" aria-label="Edit ${esc(t.name)}"><i data-lucide="pencil"></i></button>
                <button class="icon-btn danger" data-delete="${t.id}" title="Delete" aria-label="Delete ${esc(t.name)}"><i data-lucide="trash-2"></i></button>
            </td>
        </tr>`).join('');
    UI.icons();
}

function openForm(teacher) {
    const t = teacher || { name: '', email: '' };
    UI.formDialog({
        title: teacher ? 'Edit teacher' : 'Add teacher',
        body: `
            <div class="field">
                <label for="name">Name</label>
                <input class="input" id="name" name="name" maxlength="100" value="${esc(t.name)}">
            </div>
            <div class="field">
                <label for="email">Email</label>
                <input class="input" id="email" name="email" type="email" maxlength="150" value="${esc(t.email)}">
            </div>
            <div class="field">
                <label for="password">${teacher ? 'New password' : 'Password'}</label>
                <input class="input" id="password" name="password" type="password" maxlength="72" autocomplete="new-password">
                <div class="field-hint">${teacher ? 'Leave blank to keep the current password.' : 'At least 6 characters.'}</div>
            </div>`,
        onSubmit: async (form) => {
            const body = { name: form.name.value, email: form.email.value, password: form.password.value };
            if (teacher) {
                await Api.put(`/api/admin/teachers/${teacher.id}`, body);
                UI.notice('Teacher saved.');
            } else {
                await Api.post('/api/admin/teachers', body);
                UI.notice('Teacher added.');
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
        openForm(teachers.find(t => t.id === Number(editBtn.dataset.edit)));
    } else if (deleteBtn) {
        const t = teachers.find(x => x.id === Number(deleteBtn.dataset.delete));
        if (await UI.confirm(`Delete the account of ${t.name}? They will no longer be able to log in.`)) {
            await UI.run(() => Api.del(`/api/admin/teachers/${t.id}`), 'Teacher deleted.');
            await load();
        }
    }
});

(async () => {
    await Layout.init('teachers', { adminOnly: true });
    await load();
})();
