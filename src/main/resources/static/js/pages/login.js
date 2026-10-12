/* Page script for /login.html (kept out of the HTML so the Content-Security-Policy can forbid inline scripts). */
const form = document.getElementById('login-form');
const errorBox = document.getElementById('login-error');
const button = document.getElementById('login-btn');

function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
}

form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const email = form.email.value.trim();
    const password = form.password.value;
    if (!email || !password) {
        showError('Enter your email and password.');
        return;
    }
    errorBox.hidden = true;
    button.disabled = true;
    button.textContent = 'Logging in...';
    try {
        await Api.login(email, password);
        window.location.href = '/index.html';
    } catch (e) {
        showError(e.message);
        button.disabled = false;
        button.textContent = 'Log in';
    }
});
