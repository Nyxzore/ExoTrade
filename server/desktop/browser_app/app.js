const API_BASE = '/';
const storage = window.localStorage;

const state = {
    user: JSON.parse(storage.getItem('exotrade_user')) || null,
    listings: [],
    breeding: []
};

// --- Utilities ---
function formatCurrency(val) {
    if (!val) return 'Contact';
    if (typeof val === 'string' && val.includes('R')) return val;
    return new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR' }).format(val);
}

// --- Auth Handling ---
function saveSession(data) {
    state.user = {
        uuid: data.uuid,
        token: data.auth_token,
        username: data.username,
        isAdmin: data.is_admin
    };
    storage.setItem('exotrade_user', JSON.stringify(state.user));
    window.location.hash = 'discovery';
}

function logout() {
    state.user = null;
    storage.removeItem('exotrade_user');
    window.location.hash = 'login';
}

async function apiPost(endpoint, body) {
    const formData = new FormData();
    if (state.user) {
        formData.append('user_id', state.user.uuid);
        formData.append('auth_token', state.user.token);
    }
    for (const key in body) formData.append(key, body[key]);

    const res = await fetch(API_BASE + endpoint, {
        method: 'POST',
        body: formData
    });
    const json = await res.json();
    if (json.status === 'error' && json.message.includes('Authentication')) {
        logout();
    }
    return json;
}

// --- Routing ---
const routes = {
    login: renderLogin,
    register: renderRegister,
    discovery: renderDiscovery,
    breeding: renderBreeding,
    profile: renderProfile
};

function router() {
    const hash = window.location.hash.slice(1) || 'discovery';
    const isAuthPath = hash === 'login' || hash === 'register';

    if (!state.user && !isAuthPath) {
        window.location.hash = 'login';
        return;
    }

    if (state.user && isAuthPath) {
        window.location.hash = 'discovery';
        return;
    }

    document.getElementById('main-header').classList.toggle('hidden', isAuthPath);

    const renderer = routes[hash] || renderDiscovery;
    renderer();
}

// --- View Renderers ---
function renderLogin() {
    const content = document.getElementById('content');
    content.innerHTML = `
        <div class="auth-container">
            <h1>ExoTrade Login</h1>
            <form id="form-login">
                <div class="form-group">
                    <label>Username</label>
                    <input type="text" id="login-user" required>
                </div>
                <div class="form-group">
                    <label>Password</label>
                    <input type="password" id="login-pass" required>
                </div>
                <button type="submit">Login</button>
            </form>
            <p class="auth-switch">Don't have an account? <span onclick="window.location.hash='register'">Register</span></p>
        </div>
    `;

    document.getElementById('form-login').onsubmit = async (e) => {
        e.preventDefault();
        const res = await apiPost('auth/auth', {
            username: document.getElementById('login-user').value,
            password: document.getElementById('login-pass').value,
            mode: 'login'
        });
        if (res.status === 'success') saveSession({ ...res.data, username: document.getElementById('login-user').value });
        else alert(res.message);
    };
}

function renderRegister() {
    const content = document.getElementById('content');
    content.innerHTML = `
        <div class="auth-container">
            <h1>Create Account</h1>
            <form id="form-register">
                <div class="form-group"><label>Username</label><input type="text" id="reg-user" required></div>
                <div class="form-group"><label>Email</label><input type="email" id="reg-email" required></div>
                <div class="form-group"><label>Password</label><input type="password" id="reg-pass" required></div>
                <p style="font-size:0.7rem; color:#888; margin-bottom:1rem;">Note: Desktop registration uses temporary keys. Mobile is recommended for E2EE.</p>
                <button type="submit">Register</button>
            </form>
            <p class="auth-switch">Already have an account? <span onclick="window.location.hash='login'">Login</span></p>
        </div>
    `;

    document.getElementById('form-register').onsubmit = async (e) => {
        e.preventDefault();
        // Simplified reg for web (mocking E2EE keys for now to bypass check)
        const res = await apiPost('auth/auth', {
            username: document.getElementById('reg-user').value,
            email: document.getElementById('reg-email').value,
            password: document.getElementById('reg-pass').value,
            mode: 'register',
            public_key: 'WEB_TEMP_KEY',
            encrypted_private_key: 'WEB_TEMP_KEY',
            private_key_nonce: 'AAA=',
            kdf_salt: 'AAA='
        });
        if (res.status === 'success') saveSession({ ...res.data, username: document.getElementById('reg-user').value });
        else alert(res.message);
    };
}

async function renderDiscovery() {
    const content = document.getElementById('content');
    content.innerHTML = '<div class="loader">Fetching the latest listings...</div>';

    const res = await apiPost('listings/get_all_listings', {});
    if (res.status === 'success') {
        const list = res.data.listings;
        content.innerHTML = `
            <h1 style="margin-bottom:2rem">Discovery Feed</h1>
            <div class="feed-grid">
                ${list.map(l => `
                    <div class="listing-card">
                        ${(l.is_unverified_scientific || l.is_unverified_common) ? '<div class="unverified-badge" title="Unverified name">!</div>' : ''}
                        <img src="${l.image_url ? l.image_url : '/logo.png'}" onerror="this.src='/logo.png'">
                        <div class="listing-info">
                            <h3>${l.common_name}</h3>
                            <span class="scientific-name">${l.scientific_name}</span>
                            <div class="price">${l.price}</div>
                        </div>
                    </div>
                `).join('')}
            </div>
        `;
    }
}

async function renderBreeding() {
    const content = document.getElementById('content');
    content.innerHTML = '<div class="loader">Finding breeding matches...</div>';

    const res = await apiPost('breeding/get_breeding_listings', {});
    if (res.status === 'success') {
        const list = res.data.listings;
        content.innerHTML = `
            <h1 style="margin-bottom:2rem">Breeding Feed</h1>
            <div class="feed-grid">
                ${list.map(l => `
                    <div class="listing-card">
                        <img src="${l.image_url ? l.image_url : '/logo.png'}" onerror="this.src='/logo.png'">
                        <div class="listing-info">
                            <h3>${l.common_name}</h3>
                            <span class="scientific-name">${l.scientific_name}</span>
                            <div class="price">${l.price}</div>
                            <div style="font-size:0.8rem; color:#888; margin-top:0.5rem">${l.sex} • ${l.breeding_type}</div>
                        </div>
                    </div>
                `).join('')}
            </div>
        `;
    }
}

async function renderProfile() {
    const content = document.getElementById('content');
    content.innerHTML = `
        <div class="profile-header">
            <img src="/logo.png" class="profile-pic">
            <div>
                <h1>${state.user.username}</h1>
                <p style="color:#888">ExoTrade Member</p>
            </div>
        </div>
        <p style="text-align:center; color:#666">Full profile management and chat coming soon to Desktop. Use the Mobile app for E2EE messaging.</p>
    `;
}

// --- Initialization ---
window.onhashchange = router;
document.getElementById('btn-logout').onclick = logout;
router();
