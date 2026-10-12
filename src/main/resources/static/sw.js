/*
 * Service worker: lets the app be installed ("Add to Home Screen" / "Install app")
 * and keeps working screens readable on a slow or broken connection.
 *
 *  - /api/ requests are NEVER cached: attendance data always comes from the server,
 *    and nothing is saved "for later" behind the user's back.
 *  - Pages (HTML) and this app's own CSS / JS / icons: network first, so a new version
 *    is used as soon as it is deployed; the cached copy is only used when offline.
 *  - Fonts and libraries from CDNs (fixed version numbers in their URLs): cache first.
 *  - Without a connection, opening a page shows offline.html.
 *
 * Change CACHE only if the list below changes; normal updates need no version change.
 */
const CACHE = 'attendance-v2';
const PRECACHE = [
    '/offline.html',
    '/manifest.json',
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
    const sameOrigin = url.origin === self.location.origin;

    if (sameOrigin && (url.pathname.startsWith('/api/') || url.pathname.startsWith('/.well-known/'))) {
        return;                                            // live data: no caching at all
    }
    if (request.mode === 'navigate') {                     // pages: always fresh, offline page as fallback
        event.respondWith(fetch(request).catch(() => caches.match('/offline.html')));
        return;
    }
    event.respondWith(sameOrigin ? networkFirst(request) : cacheFirst(request));
});

/** This app's CSS / JS / icons: try the server, keep a copy, use the copy when offline. */
async function networkFirst(request) {
    const cache = await caches.open(CACHE);
    try {
        const response = await fetch(request);
        if (response.ok) cache.put(request, response.clone());
        return response;
    } catch (e) {
        const cached = await cache.match(request, { ignoreSearch: true });
        if (cached) return cached;
        throw e;
    }
}

/** Fonts and libraries from CDNs never change at the same URL, so the cached copy is used. */
async function cacheFirst(request) {
    const cache = await caches.open(CACHE);
    const cached = await cache.match(request);
    if (cached) return cached;
    const response = await fetch(request);
    if (response.ok || response.type === 'opaque') cache.put(request, response.clone());
    return response;
}
