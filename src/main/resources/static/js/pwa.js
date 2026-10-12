/*
 * Loaded by every page:
 *  - registers the service worker (sw.js) so the app can be installed on phones and computers;
 *  - shows a bar at the top while the device is offline, so nobody thinks their work was saved;
 *  - makes buttons with data-reload="" reload the page (used on offline.html).
 */
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/sw.js').catch(() => {
            // not available (e.g. plain http on another computer): the app still works normally
        });
    });
}

(() => {
    function updateOfflineBar() {
        let bar = document.getElementById('offline-bar');
        if (navigator.onLine) {
            if (bar) bar.hidden = true;
            return;
        }
        if (!bar) {
            bar = document.createElement('div');
            bar.id = 'offline-bar';
            bar.className = 'offline-bar';
            bar.setAttribute('role', 'status');
            bar.textContent = 'You are offline. Loading and saving attendance needs a connection; '
                + 'anything you mark stays on this screen until you save it.';
            document.body.prepend(bar);
        }
        bar.hidden = false;
    }

    document.addEventListener('DOMContentLoaded', () => {
        updateOfflineBar();
        document.querySelectorAll('[data-reload]').forEach(button =>
            button.addEventListener('click', () => window.location.reload()));
    });
    window.addEventListener('online', updateOfflineBar);
    window.addEventListener('offline', updateOfflineBar);
})();
