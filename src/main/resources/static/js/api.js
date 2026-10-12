/*
 * Small wrapper around fetch() for talking to the Spring Boot REST API.
 *  - sends and receives JSON
 *  - adds the CSRF token (from the XSRF-TOKEN cookie) to POST/PUT/DELETE requests
 *  - sends the user to the login page when the session has expired (HTTP 401)
 *  - throws an ApiError with the server's message when a request fails, or a plain-language
 *    message when the server cannot be reached (status 0), is starting up (502/503/504) or is slow
 */
class ApiError extends Error {
    constructor(message, status, data) {
        super(message);
        this.status = status;
        this.data = data;   // may contain fieldErrors from validation
    }
}

const Api = (() => {

    const SLOW_MS = 8000;        // after this, tell the user the server may be waking up
    const TIMEOUT_MS = 90000;    // after this, give up (a sleeping free server takes ~1 minute)

    const OFFLINE_MESSAGE = 'No internet connection. Check your Wi-Fi or mobile data and try again.';
    const UNREACHABLE_MESSAGE = 'Could not reach the server. Check your connection and try again.';
    const UNAVAILABLE_MESSAGE = 'The server is starting up or busy. Please wait a minute and try again.';
    const TIMEOUT_MESSAGE = 'The server took too long to answer. Please try again.';

    function csrfToken() {
        const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : null;
    }

    /** The server sets the CSRF cookie on any request; make one if it is missing. */
    async function ensureCsrfToken() {
        if (!csrfToken()) {
            try {
                await fetch('/api/auth/me', { credentials: 'same-origin' });
            } catch (e) {
                // offline: the request itself will report the problem
            }
        }
        return csrfToken();
    }

    async function request(method, url, body) {
        const options = { method, headers: { 'Accept': 'application/json' }, credentials: 'same-origin' };

        if (method !== 'GET') {
            options.headers['X-XSRF-TOKEN'] = await ensureCsrfToken();
        }
        if (body instanceof FormData || body instanceof URLSearchParams) {
            options.body = body;
        } else if (body !== undefined) {
            options.headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(body);
        }

        const response = await send(url, options);
        const isJson = (response.headers.get('Content-Type') || '').includes('application/json');
        const data = isJson ? await response.json().catch(() => null) : null;

        if (response.status === 401 && url !== '/api/auth/login') {
            window.location.href = '/login.html';
            throw new ApiError('Please log in.', 401, data);
        }
        if (!response.ok) {
            const fallback = [502, 503, 504].includes(response.status)
                ? UNAVAILABLE_MESSAGE
                : `Something went wrong (error ${response.status}). Please try again.`;
            throw new ApiError((data && data.message) || fallback, response.status, data);
        }
        return data;
    }

    /** fetch() with a time limit, a "waking up" notice for slow answers and clear network errors. */
    async function send(url, options) {
        if (!navigator.onLine) {
            throw new ApiError(OFFLINE_MESSAGE, 0);
        }
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);
        const slow = setTimeout(() => {
            if (typeof UI !== 'undefined') {
                UI.notice('Still working... the server may be waking up, this can take up to a minute.');
            }
        }, SLOW_MS);
        try {
            return await fetch(url, { ...options, signal: controller.signal });
        } catch (error) {
            if (error.name === 'AbortError') throw new ApiError(TIMEOUT_MESSAGE, 0);
            throw new ApiError(navigator.onLine ? UNREACHABLE_MESSAGE : OFFLINE_MESSAGE, 0);
        } finally {
            clearTimeout(timeout);
            clearTimeout(slow);
        }
    }

    return {
        get: (url) => request('GET', url),
        post: (url, body) => request('POST', url, body),
        put: (url, body) => request('PUT', url, body),
        del: (url) => request('DELETE', url),

        login: (email, password) => request('POST', '/api/auth/login', new URLSearchParams({ email, password })),
        logout: () => request('POST', '/api/auth/logout'),
    };
})();
