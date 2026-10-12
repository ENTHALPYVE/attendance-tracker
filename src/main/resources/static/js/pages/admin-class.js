/* Page script for /admin/class.html (kept out of the HTML so the Content-Security-Policy can forbid inline scripts). */
const esc = UI.esc;
const classId = UI.queryParam('id');
let cls = null;        // the class with its batches
let students = [];

// ------------------------------------------------------------ loading and drawing

async function load() {
    [cls, students] = await Promise.all([
        Api.get(`/api/admin/classes/${classId}`),
        Api.get(`/api/admin/classes/${classId}/students`),
    ]);
    document.title = `${cls.name} · Attendance Tracker`;
    document.getElementById('class-name').textContent = cls.name;
    document.getElementById('timetable-link').href = `/admin/timetable.html?classId=${cls.id}`;
    document.getElementById('class-summary').textContent =
        `${cls.studentCount} students in ${cls.batches.length} batches`;
    drawBatches();
    drawBatchFilter();
    drawStudents();
}

function drawBatches() {
    const box = document.getElementById('batches');
    if (cls.batches.length === 0) {
        box.innerHTML = '<span class="muted">No batches yet. Add batches before adding students.</span>';
        return;
    }
    box.innerHTML = cls.batches.map(b => `
        <span class="chip">
            <span class="chip-name">${esc(b.name)}</span>
            <span class="muted small">${b.studentCount} students</span>
            <button class="icon-btn" data-rename-batch="${b.id}" title="Rename" aria-label="Rename ${esc(b.name)}"><i data-lucide="pencil"></i></button>
            <button class="icon-btn danger" data-delete-batch="${b.id}" title="Delete" aria-label="Delete ${esc(b.name)}"><i data-lucide="trash-2"></i></button>
        </span>`).join('');
    UI.icons();
}

function drawBatchFilter() {
    const select = document.getElementById('batch-filter');
    const current = select.value;
    select.innerHTML = '<option value="">All batches</option>' +
        cls.batches.map(b => `<option value="${b.id}">${esc(b.name)}</option>`).join('');
    select.value = cls.batches.some(b => String(b.id) === current) ? current : '';
}

function drawStudents() {
    const search = document.getElementById('search').value.trim().toLowerCase();
    const batchId = document.getElementById('batch-filter').value;
    const shown = students.filter(s =>
        (!batchId || String(s.batchId) === batchId) &&
        (!search || s.name.toLowerCase().includes(search) || s.rollNo.toLowerCase().includes(search)));

    const body = document.getElementById('students');
    if (shown.length === 0) {
        body.innerHTML = `<tr><td colspan="4" class="empty">${students.length ? 'No students match.' : 'No students yet.'}</td></tr>`;
        return;
    }
    body.innerHTML = shown.map(s => `
        <tr>
            <td class="roll">${esc(s.rollNo)}</td>
            <td>${esc(s.name)}</td>
            <td>${esc(s.batchName)}</td>
            <td class="actions">
                <button class="icon-btn" data-edit="${s.id}" title="Edit or move" aria-label="Edit ${esc(s.name)}"><i data-lucide="pencil"></i></button>
                <button class="icon-btn danger" data-delete="${s.id}" title="Remove" aria-label="Remove ${esc(s.name)}"><i data-lucide="trash-2"></i></button>
            </td>
        </tr>`).join('');
    UI.icons();
}

// ------------------------------------------------------------ batches

function openBatchForm(batch) {
    UI.formDialog({
        title: batch ? 'Rename batch' : 'Add batch',
        body: `
            <div class="field">
                <label for="name">Batch name</label>
                <input class="input" id="name" name="name" maxlength="20" value="${esc(batch ? batch.name : '')}" placeholder="B4">
            </div>`,
        onSubmit: async (form) => {
            if (batch) {
                await Api.put(`/api/admin/batches/${batch.id}`, { name: form.name.value });
                UI.notice('Batch renamed.');
            } else {
                await Api.post(`/api/admin/classes/${classId}/batches`, { name: form.name.value });
                UI.notice('Batch added.');
            }
            await load();
        },
    });
}

document.getElementById('add-batch-btn').addEventListener('click', () => openBatchForm(null));

document.getElementById('batches').addEventListener('click', async (event) => {
    const renameBtn = event.target.closest('[data-rename-batch]');
    const deleteBtn = event.target.closest('[data-delete-batch]');
    if (renameBtn) {
        openBatchForm(cls.batches.find(b => b.id === Number(renameBtn.dataset.renameBatch)));
    } else if (deleteBtn) {
        const b = cls.batches.find(x => x.id === Number(deleteBtn.dataset.deleteBatch));
        if (await UI.confirm(`Delete batch ${b.name}?`)) {
            await UI.run(() => Api.del(`/api/admin/batches/${b.id}`), 'Batch deleted.');
            await load();
        }
    }
});

// ------------------------------------------------------------ single students

function batchOptions(selectedId) {
    return cls.batches.map(b =>
        `<option value="${b.id}" ${b.id === selectedId ? 'selected' : ''}>${esc(b.name)}</option>`).join('');
}

function openStudentForm(student) {
    if (cls.batches.length === 0) {
        UI.notice('Add a batch first.', 'error');
        return;
    }
    const s = student || { rollNo: '', name: '', batchId: Number(document.getElementById('batch-filter').value) || null };
    UI.formDialog({
        title: student ? 'Edit student' : 'Add student',
        body: `
            <div class="field">
                <label for="rollNo">Roll No</label>
                <input class="input" id="rollNo" name="rollNo" maxlength="20" value="${esc(s.rollNo)}">
            </div>
            <div class="field">
                <label for="name">Name</label>
                <input class="input" id="name" name="name" maxlength="100" value="${esc(s.name)}">
            </div>
            <div class="field">
                <label for="batchId">Batch</label>
                <select class="input" id="batchId" name="batchId">${batchOptions(s.batchId)}</select>
                ${student ? '<div class="field-hint">Choose another batch to move the student.</div>' : ''}
            </div>`,
        onSubmit: async (form) => {
            const body = { rollNo: form.rollNo.value, name: form.name.value, batchId: Number(form.batchId.value) };
            if (student) {
                await Api.put(`/api/admin/students/${student.id}`, body);
                UI.notice('Student saved.');
            } else {
                await Api.post(`/api/admin/classes/${classId}/students`, body);
                UI.notice('Student added.');
            }
            await load();
        },
    });
}

document.getElementById('add-student-btn').addEventListener('click', () => openStudentForm(null));
document.getElementById('search').addEventListener('input', drawStudents);
document.getElementById('batch-filter').addEventListener('change', drawStudents);

document.getElementById('students').addEventListener('click', async (event) => {
    const editBtn = event.target.closest('[data-edit]');
    const deleteBtn = event.target.closest('[data-delete]');
    if (editBtn) {
        openStudentForm(students.find(s => s.id === Number(editBtn.dataset.edit)));
    } else if (deleteBtn) {
        const s = students.find(x => x.id === Number(deleteBtn.dataset.delete));
        const ok = await UI.confirm(
            `Remove ${s.name} (Roll No ${s.rollNo})? Their attendance records will also be deleted.`,
            { confirmLabel: 'Remove' });
        if (ok) {
            await UI.run(() => Api.del(`/api/admin/students/${s.id}`), 'Student removed.');
            await load();
        }
    }
});

// ------------------------------------------------------------ upload from Excel / CSV

function openUpload() {
    let preview = null;   // last preview returned by the server

    const { form, close } = UI.formDialog({
        title: `Upload students to ${cls.name}`,
        submitLabel: 'Save students',
        wide: true,
        body: `
            <div id="upload-step">
                <div class="upload-box">
                    <p>Excel (.xlsx, .xls) or CSV file with the columns <strong>Roll No</strong>, <strong>Name</strong> and <strong>Batch</strong>.</p>
                    <p class="small muted">Batch must match one of: ${cls.batches.map(b => esc(b.name)).join(', ') || 'no batches yet'}.</p>
                    <p><input type="file" id="file" accept=".xlsx,.xls,.csv"></p>
                    <a class="small" href="/api/admin/students/template.xlsx">Download template</a>
                </div>
            </div>
            <div id="preview-step" hidden></div>`,
        onSubmit: async () => {
            if (!preview) {
                throw new ApiError('Choose a file first.');
            }
            const rows = preview.rows.map(r => ({ rollNo: r.rollNo, name: r.name, batch: r.batch }));
            try {
                const result = await Api.post(`/api/admin/classes/${classId}/students/import`, { rows });
                UI.notice(`${result.validCount} students added.`);
                await load();
            } catch (error) {
                // Someone else may have added students meanwhile: show the fresh check.
                if (error.data && error.data.rows) {
                    showPreview(error.data);
                    throw new ApiError('Some rows have errors now. Nothing was saved.');
                }
                throw error;
            }
        },
    });

    const saveBtn = form.querySelector('[type=submit]');
    saveBtn.disabled = true;

    form.querySelector('#file').addEventListener('change', async (event) => {
        const file = event.target.files[0];
        if (!file) return;
        const data = new FormData();
        data.append('file', file);
        form.querySelector('.form-error').hidden = true;
        try {
            showPreview(await Api.post(`/api/admin/classes/${classId}/students/import/preview`, data));
        } catch (error) {
            const box = form.querySelector('.form-error');
            box.textContent = error.message;
            box.hidden = false;
        }
    });

    function showPreview(p) {
        preview = p;
        const canSave = p.fileErrors.length === 0 && p.errorCount === 0 && p.validCount > 0;
        saveBtn.disabled = !canSave;
        saveBtn.textContent = canSave ? `Save ${p.validCount} students` : 'Save students';

        const step = form.querySelector('#preview-step');
        step.hidden = false;
        form.querySelector('#upload-step').hidden = true;

        const fileErrors = p.fileErrors.map(e => `<div class="alert alert-error">${esc(e)}</div>`).join('');
        step.innerHTML = `
            ${fileErrors}
            <div class="upload-summary">
                <span class="badge badge-green">${p.validCount} ready</span>
                ${p.errorCount ? `<span class="badge badge-red">${p.errorCount} with errors</span>` : ''}
                <button type="button" class="btn btn-sm" id="choose-again">Choose another file</button>
            </div>
            ${p.errorCount ? '<p class="small muted">Fix the rows marked below in your file and upload it again. Nothing is saved until every row is correct.</p>' : ''}
            ${p.rows.length ? `
            <div class="table-wrap">
                <table class="table">
                    <thead><tr><th class="num">Row</th><th>Roll No</th><th>Name</th><th>Batch</th><th>Problem</th></tr></thead>
                    <tbody>
                    ${p.rows.map(r => `
                        <tr class="${r.errors.length ? 'row-error' : ''}">
                            <td class="num muted">${r.rowNo}</td>
                            <td>${esc(r.rollNo)}</td>
                            <td>${esc(r.name)}</td>
                            <td>${esc(r.batch)}</td>
                            <td class="cell-errors">${r.errors.map(e => `<div>${esc(e)}</div>`).join('')}</td>
                        </tr>`).join('')}
                    </tbody>
                </table>
            </div>` : ''}`;

        step.querySelector('#choose-again').addEventListener('click', () => {
            preview = null;
            saveBtn.disabled = true;
            saveBtn.textContent = 'Save students';
            step.hidden = true;
            const upload = form.querySelector('#upload-step');
            upload.hidden = false;
            upload.querySelector('#file').value = '';
        });
    }
}

document.getElementById('upload-btn').addEventListener('click', () => {
    if (cls.batches.length === 0) {
        UI.notice('Add a batch first.', 'error');
        return;
    }
    openUpload();
});

(async () => {
    await Layout.init('classes', { adminOnly: true });
    if (!classId) {
        window.location.href = '/admin/classes.html';
        return;
    }
    try {
        await load();
    } catch (error) {
        document.querySelector('.main').innerHTML =
            `<div class="card empty">${esc(error.message)} <a href="/admin/classes.html">Back to classes</a></div>`;
    }
})();
