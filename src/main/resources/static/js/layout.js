/*
 * Builds the page frame shared by every screen after login:
 * sidebar on desktop, top bar + bottom navigation on mobile.
 *
 * Usage in a page:   const user = await Layout.init('home');
 */
const Layout = (() => {

    // Navigation items. "roles" says who sees the item.
    // More items are added here as each phase adds screens.
    const NAV = [
        { key: 'home', label: 'Today', href: '/index.html', icon: 'calendar-days', roles: ['ADMIN', 'TEACHER'] },
        { key: 'day', label: 'Day view', href: '/class-day.html', icon: 'calendar-range', roles: ['ADMIN', 'TEACHER'] },
        { key: 'reports', label: 'Reports', href: '/reports.html', icon: 'file-text', roles: ['ADMIN', 'TEACHER'] },
        { key: 'classes', label: 'Classes', href: '/admin/classes.html', icon: 'users', roles: ['ADMIN'] },
        { key: 'timetable', label: 'Timetable', href: '/admin/timetable.html', icon: 'table', roles: ['ADMIN'] },
        { key: 'subjects', label: 'Subjects', href: '/admin/subjects.html', icon: 'book-open', roles: ['ADMIN'] },
        { key: 'teachers', label: 'Teachers', href: '/admin/teachers.html', icon: 'id-card', roles: ['ADMIN'] },
        { key: 'settings', label: 'Settings', href: '/admin/settings.html', icon: 'settings', roles: ['ADMIN'] },
    ];

    function esc(text) {
        const div = document.createElement('div');
        div.textContent = text == null ? '' : String(text);
        return div.innerHTML;
    }

    /** The user's name, or "Admin" / "Teacher" when the name is blank. */
    function displayName(user) {
        const name = (user.name || '').trim();
        return name || (user.role === 'ADMIN' ? 'Admin' : 'Teacher');
    }

    /** Unsaved attendance marks kept by session.html; not left behind on a shared device. */
    function clearDrafts() {
        try {
            Object.keys(localStorage)
                .filter(key => key.startsWith('attendance-draft-'))
                .forEach(key => localStorage.removeItem(key));
        } catch (e) {
            // storage blocked: nothing was stored
        }
    }

    /** Dialog for changing one's own password (POST /api/auth/password). */
    function changePassword() {
        UI.formDialog({
            title: 'Change password',
            submitLabel: 'Change password',
            body: `
                <div class="field">
                    <label for="currentPassword">Current password</label>
                    <input class="input" id="currentPassword" name="currentPassword" type="password"
                           autocomplete="current-password" maxlength="72" required>
                </div>
                <div class="field">
                    <label for="newPassword">New password</label>
                    <input class="input" id="newPassword" name="newPassword" type="password"
                           autocomplete="new-password" minlength="8" maxlength="72" required>
                    <div class="small muted">At least 8 characters.</div>
                </div>
                <div class="field">
                    <label for="confirmPassword">New password again</label>
                    <input class="input" id="confirmPassword" name="confirmPassword" type="password"
                           autocomplete="new-password" maxlength="72" required>
                </div>`,
            onSubmit: async (form) => {
                if (form.newPassword.value !== form.confirmPassword.value) {
                    throw new ApiError('The two new passwords are not the same.', 400,
                        { fieldErrors: { confirmPassword: 'Type the same new password twice.' } });
                }
                const result = await Api.post('/api/auth/password', {
                    currentPassword: form.currentPassword.value,
                    newPassword: form.newPassword.value,
                });
                UI.notice(result.message);
            },
        });
    }

    function navLinks(items, active, className) {
        return items.map(item => `
            <a class="${className}${item.key === active ? ' active' : ''}" href="${item.href}">
                <i data-lucide="${item.icon}"></i><span>${esc(item.label)}</span>
            </a>`).join('');
    }

    async function logout() {
        clearDrafts();
        try {
            await Api.logout();
        } finally {
            window.location.href = '/login.html';
        }
    }

    /**
     * options.adminOnly: send teachers back to the home page. (This is only for convenience;
     * the API itself refuses admin requests from teachers.)
     */
    async function init(active, options = {}) {
        const user = await Api.get('/api/auth/me');
        if (options.adminOnly && user.role !== 'ADMIN') {
            window.location.href = '/index.html';
            throw new Error('Admin only');
        }
        const items = NAV.filter(item => item.roles.includes(user.role));
        const roleLabel = user.role === 'ADMIN' ? 'Admin' : 'Teacher';

        document.body.insertAdjacentHTML('afterbegin', `
            <aside class="sidebar">
                <div class="brand">Attendance Tracker<small>${roleLabel}</small></div>
                <nav>${navLinks(items, active, 'nav-link')}</nav>
                <div class="sidebar-footer">
                    <div class="user-name">${esc(displayName(user))}</div>
                    <div class="muted">${esc(user.email)}</div>
                    <div class="sidebar-actions">
                        <button class="btn-link" id="password-btn" type="button">Change password</button>
                        <button class="btn-link" id="logout-btn" type="button">Log out</button>
                    </div>
                </div>
            </aside>
            <header class="topbar">
                <span>Attendance Tracker</span>
                <span class="topbar-actions">
                    <button class="icon-btn" id="password-btn-mobile" type="button" title="Change password"
                            aria-label="Change password"><i data-lucide="key-round"></i></button>
                    <button class="btn-link" id="logout-btn-mobile" type="button">Log out</button>
                </span>
            </header>`);
        // Phones: at most 5 places in the bottom bar; the rest go under "More".
        const shown = items.length > 5 ? items.slice(0, 4) : items;
        const extra = items.length > 5 ? items.slice(4) : [];
        const moreActive = extra.some(item => item.key === active);
        document.body.insertAdjacentHTML('beforeend', `
            <nav class="bottom-nav">
                ${navLinks(shown, active, '')}
                ${extra.length ? `<a href="#" id="more-btn" class="${moreActive ? 'active' : ''}" aria-expanded="false">
                    <i data-lucide="menu"></i><span>More</span></a>` : ''}
            </nav>
            ${extra.length ? `<div class="more-menu" id="more-menu" hidden>${navLinks(extra, active, 'nav-link')}</div>` : ''}`);
        const moreBtn = document.getElementById('more-btn');
        if (moreBtn) {
            moreBtn.addEventListener('click', (event) => {
                event.preventDefault();
                const menu = document.getElementById('more-menu');
                menu.hidden = !menu.hidden;
                moreBtn.setAttribute('aria-expanded', String(!menu.hidden));
            });
        }

        document.getElementById('logout-btn').addEventListener('click', logout);
        document.getElementById('logout-btn-mobile').addEventListener('click', logout);
        document.getElementById('password-btn').addEventListener('click', changePassword);
        document.getElementById('password-btn-mobile').addEventListener('click', changePassword);
        lucide.createIcons();
        if (typeof UI !== 'undefined') UI.showFlash();   // e.g. "Attendance saved"
        return user;
    }

    return { init, esc, displayName };
})();
