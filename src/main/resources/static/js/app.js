const state = {
    token: localStorage.getItem('sk_token') || '',
    role: localStorage.getItem('sk_role') || '',
    nickname: localStorage.getItem('sk_nickname') || ''
};

let activities = [];
let timer = null;

const $ = (s) => document.querySelector(s);

function showToast(message, type = '') {
    const box = $('#toastBox');
    const t = document.createElement('div');
    t.className = 'toast ' + type;
    t.textContent = message;
    box.appendChild(t);
    setTimeout(() => t.remove(), 2600);
}

async function api(path, method = 'GET', body = null) {
    const headers = { 'Content-Type': 'application/json' };
    if (state.token) headers['Authorization'] = 'Bearer ' + state.token;
    const opts = { method, headers };
    if (body) opts.body = JSON.stringify(body);
    let res, data = {};
    try {
        res = await fetch(path, opts);
    } catch (e) {
        return { ok: false, code: 0, message: '网络错误：请确认后端已启动（mvn spring-boot:run）', data: null };
    }
    try { data = await res.json(); } catch (e) {}
    return { ok: res.ok, code: data.code, message: data.message, data: data.data };
}

// ---------- 鉴权 UI ----------
function updateAuthUI() {
    const logged = !!state.token;
    $('#loginBtn').classList.toggle('hidden', logged);
    $('#registerBtn').classList.toggle('hidden', logged);
    $('#logoutBtn').classList.toggle('hidden', !logged);
    $('#userBadge').classList.toggle('hidden', !logged);
    if (logged) {
        $('#userBadge').textContent = state.nickname || state.role;
    }
    $('#adminTab').classList.toggle('hidden', state.role !== 'ADMIN');
}

function setSession(res) {
    state.token = res.data.token;
    state.role = res.data.role;
    state.nickname = res.data.nickname || state.role;
    localStorage.setItem('sk_token', state.token);
    localStorage.setItem('sk_role', state.role);
    localStorage.setItem('sk_nickname', state.nickname);
    updateAuthUI();
}

function showModal(mode) {
    $('#modal').classList.remove('hidden');
    $('#modalTitle').textContent = mode === 'login' ? '登录' : '注册';
    $('#nicknameField').classList.toggle('hidden', mode !== 'register');
    $('#authSubmit').textContent = mode === 'login' ? '登录' : '注册';
    $('#modal').dataset.mode = mode;
}

function closeModal() { $('#modal').classList.add('hidden'); }

async function handleAuthSubmit(e) {
    e.preventDefault();
    const mode = $('#modal').dataset.mode;
    const body = {
        username: $('#authUsername').value.trim(),
        password: $('#authPassword').value
    };
    if (mode === 'register') body.nickname = $('#authNickname').value.trim() || body.username;
    const path = mode === 'login' ? '/api/auth/login' : '/api/auth/register';
    const r = await api(path, 'POST', body);
    if (r.code === 200) {
        setSession(r);
        closeModal();
        showToast(mode === 'login' ? '登录成功' : '注册成功', 'success');
        $('#authForm').reset();
        loadActivities();
        loadOrders();
        if (state.role === 'ADMIN') loadAdmin();
    } else {
        showToast(r.message || '操作失败', 'error');
    }
}

async function logout() {
    await api('/api/auth/logout', 'POST');
    state.token = state.role = state.nickname = '';
    localStorage.removeItem('sk_token');
    localStorage.removeItem('sk_role');
    localStorage.removeItem('sk_nickname');
    updateAuthUI();
    switchTab('home');
    loadActivities();
    showToast('已退出登录');
}

// ---------- Tab ----------
function switchTab(name) {
    document.querySelectorAll('.tab').forEach(t => t.classList.toggle('active', t.dataset.tab === name));
    document.querySelectorAll('.view').forEach(v => v.classList.toggle('active', v.id === name));
    if (name === 'orders') loadOrders();
    if (name === 'admin' && state.role === 'ADMIN') loadAdmin();
}

// ---------- 时间/倒计时 ----------
function parseTime(s) { return new Date(String(s).replace(' ', 'T')).getTime(); }
function fmtCountdown(ms) {
    if (ms <= 0) return '00:00:00';
    const s = Math.floor(ms / 1000);
    const h = String(Math.floor(s / 3600)).padStart(2, '0');
    const m = String(Math.floor((s % 3600) / 60)).padStart(2, '0');
    const ss = String(s % 60).padStart(2, '0');
    return h + ':' + m + ':' + ss;
}
const STATUS = { 0: ['未开始', 'gray'], 1: ['进行中', 'green'], 2: ['已结束', 'red'] };

function updateCountdown() {
    const now = Date.now();
    document.querySelectorAll('[data-cd]').forEach(el => {
        const start = Number(el.dataset.start);
        const end = Number(el.dataset.end);
        if (now < start) el.textContent = '距开始 ' + fmtCountdown(start - now);
        else if (now < end) el.textContent = '进行中 · 距结束 ' + fmtCountdown(end - now);
        else el.textContent = '已结束';
    });
}

// ---------- 活动 ----------
async function loadActivities() {
    const r = await api('/api/seckill/activities?page=1&size=50');
    activities = (r.code === 200 && r.data) ? r.data.list : [];
    renderActivities();
}

function renderActivities() {
    const grid = $('#activityGrid');
    grid.innerHTML = '';
    $('#activityEmpty').classList.toggle('hidden', activities.length > 0);
    activities.forEach(a => {
        const [st, cls] = STATUS[a.status] || ['未知', 'gray'];
        const card = document.createElement('div');
        card.className = 'card';
        const sold = a.soldOut || a.status !== 1;
        card.innerHTML = `
            <div class="activity-name">${a.goodsName || '商品'}</div>
            <div class="price-row"><span class="seckill-price">${a.seckillPrice}</span>
                <span class="badge ${cls}">${st}</span></div>
            <div class="meta">
                <span data-cd data-start="${parseTime(a.startTime)}" data-end="${parseTime(a.endTime)}"></span>
                <span>开始 ${a.startTime} · 结束 ${a.endTime}</span>
                <span>${a.soldOut ? '已抢完' : '有货'}</span>
            </div>
            <button class="btn btn-primary btn-block buy-btn" data-id="${a.id}" ${sold ? 'disabled' : ''}>${a.soldOut ? '已抢完' : '立即抢购'}</button>`;
        grid.appendChild(card);
    });
    updateCountdown();
}

async function buy(activityId) {
    if (!state.token) { showToast('请先登录', 'error'); showModal('login'); return; }
    const r = await api('/api/seckill/' + activityId + '/orders', 'POST');
    if (r.code === 200) {
        showToast('抢购成功！订单号 ' + r.data.orderNo, 'success');
        loadActivities();
        loadOrders();
    } else {
        showToast(r.message || '抢购失败', 'error');
    }
}

// ---------- 订单 ----------
async function loadOrders() {
    if (!state.token) { $('#orderList').innerHTML = ''; $('#orderEmpty').classList.remove('hidden'); return; }
    const r = await api('/api/orders/mine?page=1&size=100');
    const list = (r.code === 200 && r.data) ? r.data.list : [];
    $('#orderEmpty').classList.toggle('hidden', list.length > 0);
    const box = $('#orderList');
    box.innerHTML = '';
    const orderStatus = { 0: ['待支付', 'orange'], 1: ['已支付', 'green'], 2: ['已取消', 'red'] };
    list.forEach(o => {
        const [st, cls] = orderStatus[o.status] || ['未知', 'gray'];
        const item = document.createElement('div');
        item.className = 'order-item';
        item.innerHTML = `
            <div class="grow">
                <div style="font-weight:700">${o.goodsName}</div>
                <div class="order-no">单号：${o.orderNo}</div>
                <div style="color:#7a8194;font-size:13px">￥${o.seckillPrice} · 下单 ${o.createTime}</div>
            </div>
            <span class="badge ${cls}">${st}</span>
            <div class="order-actions">
                <button class="btn btn-primary btn-sm pay-btn" data-no="${o.orderNo}" ${o.status !== 0 ? 'disabled' : ''}>支付</button>
                <button class="btn btn-danger btn-sm cancel-btn" data-no="${o.orderNo}" ${o.status !== 0 ? 'disabled' : ''}>取消</button>
            </div>`;
        box.appendChild(item);
    });
}

async function pay(orderNo) {
    const r = await api('/api/orders/' + orderNo + '/pay', 'POST');
    if (r.code === 200) { showToast('支付成功', 'success'); loadOrders(); }
    else showToast(r.message || '支付失败', 'error');
}

async function cancelOrder(orderNo) {
    const r = await api('/api/orders/' + orderNo + '/cancel', 'POST');
    if (r.code === 200) { showToast('已取消，库存已回补', 'success'); loadOrders(); loadActivities(); }
    else showToast(r.message || '取消失败', 'error');
}

// ---------- 管理后台 ----------
async function loadAdmin() {
    if (state.role !== 'ADMIN') return;
    const r = await api('/api/admin/activities?page=1&size=100');
    const list = (r.code === 200 && r.data) ? r.data.list : [];
    const box = $('#adminList');
    box.innerHTML = '';
    list.forEach(a => {
        const item = document.createElement('div');
        item.className = 'admin-item';
        item.innerHTML = `
            <div>
                <div style="font-weight:700">#${a.id} ${a.goodsName}</div>
                <div style="color:#7a8194;font-size:13px">￥${a.seckillPrice} · 库存 ${a.stock ?? '-'} · 状态 ${STATUS[a.status] ? STATUS[a.status][0] : '-'}</div>
            </div>
            <button class="btn btn-ghost btn-sm reload-btn" data-id="${a.id}">重置库存</button>`;
        box.appendChild(item);
    });
}

async function createActivity(e) {
    e.preventDefault();
    const body = {
        goodsId: Number($('#f_goodsId').value),
        seckillPrice: Number($('#f_price').value),
        startTime: $('#f_start').value.replace('T', ' ') + ':00',
        endTime: $('#f_end').value.replace('T', ' ') + ':00',
        stock: Number($('#f_stock').value)
    };
    const r = await api('/api/admin/activities', 'POST', body);
    if (r.code === 200) {
        showToast('活动创建成功', 'success');
        loadAdmin();
        loadActivities();
    } else showToast(r.message || '创建失败', 'error');
}

async function reloadStock(activityId) {
    const r = await api('/api/admin/activities/' + activityId + '/stock/reload', 'POST');
    if (r.code === 200) showToast('已按数据库重置 Redis 库存', 'success');
    else showToast(r.message || '重置失败', 'error');
}

// ---------- 事件绑定 ----------
document.querySelectorAll('.tab').forEach(t => t.addEventListener('click', () => switchTab(t.dataset.tab)));
$('#loginBtn').addEventListener('click', () => showModal('login'));
$('#registerBtn').addEventListener('click', () => showModal('register'));
$('#logoutBtn').addEventListener('click', logout);
$('#modalClose').addEventListener('click', closeModal);
$('#modal').addEventListener('click', (e) => { if (e.target === $('#modal')) closeModal(); });
$('#authForm').addEventListener('submit', handleAuthSubmit);
$('#activityForm').addEventListener('submit', createActivity);
$('#activityRefresh').addEventListener('click', loadActivities);
$('#orderRefresh').addEventListener('click', loadOrders);

$('#activityGrid').addEventListener('click', (e) => {
    const btn = e.target.closest('.buy-btn');
    if (btn) buy(Number(btn.dataset.id));
});
$('#orderList').addEventListener('click', (e) => {
    const pay = e.target.closest('.pay-btn');
    const cancel = e.target.closest('.cancel-btn');
    if (pay) pay(pay.dataset.no);
    if (cancel) cancelOrder(cancel.dataset.no);
});
$('#adminList').addEventListener('click', (e) => {
    const btn = e.target.closest('.reload-btn');
    if (btn) reloadStock(Number(btn.dataset.id));
});

// ---------- 启动 ----------
updateAuthUI();
loadActivities();
if (state.token) { loadOrders(); if (state.role === 'ADMIN') loadAdmin(); }
timer = setInterval(updateCountdown, 1000);
