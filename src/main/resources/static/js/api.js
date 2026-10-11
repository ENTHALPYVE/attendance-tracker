/*
 * Small wrapper around fetch() for talking to the Spring Boot REST API.
 *  - sends and receives JSON
 *  - adds the CSRF token (from the XSRF-TOKEN cookie) to POST/PUT/DELETE requests
 *  - sends the user to the login page when the session has expired (HTTP 401)
 *  - throws an ApiError with the server's message when a request fails
 */
class ApiError extends Error {
    constructor(message, status, data) {
        super(message);
        this.status = status;
        this.data = data;   // may contain fieldErrors from validation
    }
}

const Api = (() => {

    function csrfToken() {
        const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : null;
    }

    /** The server sets the CSRF cookie on any request; make one if it is missing. */
    async function ensureCsrfToken() {
        if (!csrfToken()) {
            await fetch('/api/auth/me', { credentials: 'same-origin' });
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

        const response = await fetch(url, options);
        const isJson = (response.headers.get('Content-Type') || '').includes('application/json');
        const data = isJson ? await response.json() : null;

        if (response.status === 401 && url !== '/api/auth/login') {
            window.location.href = '/login.html';
            throw new ApiError('Please log in.', 401, data);
        }
        if (!response.ok) {
            throw new ApiError((data && data.message) || `Request failed (${response.status}).`, response.status, data);
        }
        return data;
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
