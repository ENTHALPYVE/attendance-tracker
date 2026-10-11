/*
 * Service worker: lets teachers "Add to Home Screen" and makes the app open quickly.
 *
 *  - CSS, JS, icons and fonts are cached, so pages load fast on a slow college network.
 *  - Pages (HTML) always come from the network first, so they are up to date and the
 *    server can set the login / CSRF cookies. Without a connection an "offline" page is shown.
 *  - /api/ requests are NEVER cached: attendance data always comes from the server.
 *
 * Change CACHE when the static files change, so phones download the new versions.
 */
const CACHE = 'attendance-v1';
const PRECACHE = [
    '/offline.html',
    '/css/app.css',
    '/js/api.js', '/js/ui.js', '/js/layout.js', '/js/sessions.js', '/js/pwa.js',
    '/icons/icon-192.png', '/icons/favicon-32.png',
];

self.addEventListener('install', (event) => {
    event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(PRECACHE)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys()
            .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
            .then(() => self.clients.claim()));
});

self.addEventListener('fetch', (event) => {
    const request = event.request;
    if (request.method !== 'GET') return;                 // saving attendance etc. always goes to the server
    const url = new URL(request.url);

    if (url.origin === self.location.origin && url.pathname.startsWith('/api/')) {
        return;                                            // live data: no caching
    }

    if (request.mode === 'navigate') {                     // pages: network first, offline page as fallback
        event.respondWith(fetch(request).catch(() => caches.match('/offline.html')));
        return;
    }

    // Static files and fonts / icon / chart libraries from CDNs: answer from the cache,
    // and refresh the cached copy in the background.
    event.respondWith(caches.open(CACHE).then(async (cache) => {
        const cached = await cache.match(request);
        const network = fetch(request).then((response) => {
            if (response && (response.ok || response.type === 'opaque')) {
                cache.put(request, response.clone());
            }
            return response;
        }).catch(() => cached);
        return cached || network;
    }));
});
