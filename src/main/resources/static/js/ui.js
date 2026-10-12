/*
 * Small UI helpers shared by the pages: escaping text, form dialogs,
 * confirm boxes and short notices. Uses the browser's built-in <dialog> element.
 */
const UI = (() => {

    function esc(text) {
        const div = document.createElement('div');
        div.textContent = text == null ? '' : String(text);
        return div.innerHTML;
    }

    function icons() {
        if (window.lucide) lucide.createIcons();
    }

    /** Short message at the bottom of the screen that disappears by itself. */
    function notice(message, type = 'ok') {
        let box = document.getElementById('notice');
        if (!box) {
            box = document.createElement('div');
            box.id = 'notice';
            box.setAttribute('role', 'status');
            document.body.appendChild(box);
        }
        box.className = 'notice' + (type === 'error' ? ' notice-error' : '');
        box.textContent = message;
        box.hidden = false;
        clearTimeout(box._timer);
        box._timer = setTimeout(() => { box.hidden = true; }, type === 'error' ? 7000 : 3500);
    }

    // A page that loads data without its own error handling still tells the user what went wrong
    // (e.g. "No internet connection") instead of staying blank.
    window.addEventListener('unhandledrejection', (event) => {
        if (typeof ApiError !== 'undefined' && event.reason instanceof ApiError && event.reason.status !== 401) {
            notice(event.reason.message, 'error');
        }
    });

    /** A notice to show on the next page (e.g. "Attendance saved" after going back). */
    function flash(message) {
        try {
            sessionStorage.setItem('flash', message);
        } catch (e) {
            // storage blocked: the message is simply not shown
        }
    }

    function showFlash() {
        try {
            const message = sessionStorage.getItem('flash');
            if (message) {
                sessionStorage.removeItem('flash');
                notice(message);
            }
        } catch (e) {
            // ignore
        }
    }

    /** Shows an API error inside a form: the main message on top, field messages under the inputs. */
    function showFormError(form, error) {
        form.querySelectorAll('.field-error').forEach(el => el.remove());
        form.querySelectorAll('.input-invalid').forEach(el => el.classList.remove('input-invalid'));
        const box = form.querySelector('.form-error');
        box.textContent = error.message;
        box.hidden = false;

        const fieldErrors = (error.data && error.data.fieldErrors) || {};
        for (const [name, message] of Object.entries(fieldErrors)) {
            const input = form.elements[name];
            if (input && input.insertAdjacentHTML) {
                input.classList.add('input-invalid');
                input.insertAdjacentHTML('afterend', `<div class="field-error">${esc(message)}</div>`);
            }
        }
    }

    /**
     * Opens a dialog containing a form.
     *   title       - heading
     *   body        - HTML of the form fields
     *   submitLabel - text of the main button
     *   wide        - wider dialog (for tables)
     *   onSubmit(form) - async; return normally to close, throw ApiError to show the message
     */
    function formDialog({ title, body, submitLabel = 'Save', wide = false, onSubmit }) {
        const dialog = document.createElement('dialog');
        dialog.className = 'dialog' + (wide ? ' dialog-wide' : '');
        dialog.innerHTML = `
            <form method="dialog" novalidate>
                <div class="dialog-head">
                    <h2>${esc(title)}</h2>
                    <button type="button" class="icon-btn" data-close aria-label="Close"><i data-lucide="x"></i></button>
                </div>
                <div class="dialog-body">
                    <div class="alert alert-error form-error" hidden></div>
                    ${body}
                </div>
                <div class="dialog-foot">
                    <button type="button" class="btn" data-close>Cancel</button>
                    <button type="submit" class="btn btn-primary">${esc(submitLabel)}</button>
                </div>
            </form>`;
        document.body.appendChild(dialog);
        const form = dialog.querySelector('form');
        const submitBtn = form.querySelector('[type=submit]');

        const close = () => { dialog.close(); dialog.remove(); };
        dialog.querySelectorAll('[data-close]').forEach(b => b.addEventListener('click', close));
        dialog.addEventListener('cancel', (e) => { e.preventDefault(); close(); });

        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            submitBtn.disabled = true;
            try {
                await onSubmit(form);
                close();
            } catch (error) {
                if (!(error instanceof ApiError)) console.error(error);
                showFormError(form, error);
                submitBtn.disabled = false;
            }
        });

        icons();
        dialog.showModal();
        const first = form.querySelector('.dialog-body input:not([type=hidden]), .dialog-body select');
        if (first) first.focus();
        return { dialog, form, close };
    }

    /** Yes/No question. Resolves to true when confirmed. */
    function confirm(message, { title = 'Are you sure?', confirmLabel = 'Delete', danger = true } = {}) {
        return new Promise(resolve => {
            const dialog = document.createElement('dialog');
            dialog.className = 'dialog';
            dialog.innerHTML = `
                <div class="dialog-head"><h2>${esc(title)}</h2></div>
                <div class="dialog-body"><p>${esc(message)}</p></div>
                <div class="dialog-foot">
                    <button type="button" class="btn" data-answer="no">Cancel</button>
                    <button type="button" class="btn ${danger ? 'btn-danger' : 'btn-primary'}" data-answer="yes">${esc(confirmLabel)}</button>
                </div>`;
            document.body.appendChild(dialog);
            const finish = (answer) => { dialog.close(); dialog.remove(); resolve(answer); };
            dialog.querySelector('[data-answer=yes]').addEventListener('click', () => finish(true));
            dialog.querySelector('[data-answer=no]').addEventListener('click', () => finish(false));
            dialog.addEventListener('cancel', (e) => { e.preventDefault(); finish(false); });
            dialog.showModal();
        });
    }

    /** Runs an API call and shows its error as a notice instead of failing silently. */
    async function run(action, successMessage) {
        try {
            const result = await action();
            if (successMessage) notice(successMessage);
            return result;
        } catch (error) {
            notice(error.message, 'error');
            throw error;
        }
    }

    function queryParam(name) {
        return new URLSearchParams(window.location.search).get(name);
    }

    return { esc, icons, notice, flash, showFlash, formDialog, confirm, run, queryParam };
})();
