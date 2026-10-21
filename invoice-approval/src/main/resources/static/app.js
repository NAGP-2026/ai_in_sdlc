(() => {
  const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
  const $ = (id) => document.getElementById(id);
  let session = JSON.parse(sessionStorage.getItem('session') || 'null');

  async function api(path, options = {}) {
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    if (session) headers.Authorization = `Bearer ${session.token}`;
    const response = await fetch(`/api${path}`, { ...options, headers });
    if (response.status === 401 && session) { signOut(); throw new Error('Session expired.'); }
    const body = response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok) throw new Error((body && body.error) || `Request failed (${response.status})`);
    return body;
  }

  function show(id, visible) { $(id).hidden = !visible; }
  function showError(id, msg) { const p = $(id); p.textContent = msg || ''; p.hidden = !msg; }

  function statusLabel(s) {
    return { DRAFT: 'Draft', PENDING_APPROVAL: 'Pending Approval', APPROVED: 'Approved', REJECTED: 'Rejected' }[s] || s;
  }

  function render() {
    const ok = !!session;
    show('login-view', !ok); show('app-view', ok); show('session-bar', ok);
    if (ok) { $('whoami').textContent = `${session.username} (${session.role})`; refresh(); }
  }

  async function refresh() {
    const invoices = await api('/invoices');
    renderInvoices(invoices);
    renderPending(invoices.filter(i => i.status === 'PENDING_APPROVAL'));
  }

  function renderInvoices(invoices) {
    const tbody = $('invoices-table').querySelector('tbody');
    tbody.innerHTML = '';
    for (const inv of invoices) {
      const tr = document.createElement('tr');
      tr.innerHTML = `<td>${inv.id}</td><td>${inv.customerName}</td><td>${money.format(inv.amount)}</td>
        <td class="status status-${inv.status}">${statusLabel(inv.status)}</td><td></td>`;
      if (inv.status === 'DRAFT') {
        const btn = document.createElement('button');
        btn.type = 'button'; btn.textContent = 'Submit';
        btn.addEventListener('click', async () => { await api(`/invoices/${inv.id}/submit`, { method: 'POST' }); refresh(); });
        tr.lastElementChild.appendChild(btn);
      }
      tbody.appendChild(tr);
    }
  }

  function renderPending(pending) {
    show('pending-empty', pending.length === 0);
    const list = $('pending-list');
    list.innerHTML = '';
    for (const inv of pending) {
      const li = document.createElement('li');
      li.setAttribute('data-invoice-id', inv.id);
      li.innerHTML = `<span>${inv.id} · ${inv.customerName} · ${money.format(inv.amount)}</span>
        <button type="button" class="approve-btn" aria-label="Approve ${inv.id}">Approve</button>
        <input class="reason-input" placeholder="Reason (required to reject)" aria-label="Rejection reason for ${inv.id}">
        <button type="button" class="reject-btn danger" aria-label="Reject ${inv.id}">Reject</button>`;
      li.querySelector('.approve-btn').addEventListener('click', () => decide(`/approvals/${inv.id}/approve`, {}));
      li.querySelector('.reject-btn').addEventListener('click',  () => decide(`/approvals/${inv.id}/reject`, { reason: li.querySelector('.reason-input').value }));
      list.appendChild(li);
    }
  }

  async function decide(path, payload) {
    showError('decision-error', '');
    try {
      await api(path, { method: 'POST', body: JSON.stringify(payload) });
      await refresh();
    } catch (err) {
      showError('decision-error', err.message);
    }
  }

  function signOut() {
    if (session) api('/auth/logout', { method: 'POST' }).catch(() => {});
    session = null; sessionStorage.removeItem('session'); render();
  }

  $('login-form').addEventListener('submit', async (e) => {
    e.preventDefault(); showError('login-error', '');
    try {
      const res = await api('/auth/login', { method: 'POST', body: JSON.stringify({ username: $('username').value, password: $('password').value }) });
      session = { token: res.token, username: res.username, role: res.role };
      sessionStorage.setItem('session', JSON.stringify(session));
      $('password').value = '';
      render();
    } catch (err) { showError('login-error', err.message); }
  });

  $('sign-out').addEventListener('click', signOut);

  for (const tab of ['invoices', 'approvals']) {
    $(`tab-${tab}`).addEventListener('click', () => {
      for (const t of ['invoices', 'approvals']) {
        $(`tab-${t}`).setAttribute('aria-selected', String(t === tab));
        show(`${t}-panel`, t === tab);
      }
    });
  }

  render();
})();
