/* Registers the service worker (sw.js) so the app can be added to the home screen. */
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/sw.js').catch(() => {
            // not available (e.g. plain http on another computer): the app still works normally
        });
    });
}
