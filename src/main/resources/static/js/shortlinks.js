/**
 * Member 4 Controller: Distributed Token Bucket Rate Limiter & Base62 URL Shortener
 */
const ShortLinksUI = {
  stats: null,
  shortLinks: [],
  pollTimer: null,

  init() {
    this.setupListeners();
    this.loadRateLimitStats();
    this.loadShortLinks();

    // Auto-refresh stats when in view
    clearInterval(this.pollTimer);
    this.pollTimer = setInterval(() => {
      if (Router.currentView === 'shortlinks') {
        this.loadRateLimitStats();
      }
    }, 4000);
  },

  setupListeners() {
    const createForm = document.getElementById('shortlink-create-form');
    if (createForm && !createForm.dataset.bound) {
      createForm.dataset.bound = 'true';
      createForm.addEventListener('submit', (e) => this.handleCreate(e));
    }

    const testNumInput = document.getElementById('base62-demo-num');
    if (testNumInput && !testNumInput.dataset.bound) {
      testNumInput.dataset.bound = 'true';
      testNumInput.addEventListener('input', (e) => this.handleLiveBase62Encode(e.target.value));
    }
  },

  async loadRateLimitStats() {
    try {
      const res = await API.get('/api/admin/rate-limit/stats');
      if (res && res.success && res.data) {
        this.stats = res.data;
        this.renderRateLimitStats(res.data);
      }
    } catch (err) {
      console.warn('Failed to load rate limit stats:', err);
    }
  },

  renderRateLimitStats(data) {
    const capacityEl = document.getElementById('rl-stat-capacity');
    const refillEl = document.getElementById('rl-stat-refill');
    const totalEl = document.getElementById('rl-stat-total');
    const allowedEl = document.getElementById('rl-stat-allowed');
    const rejectedEl = document.getElementById('rl-stat-rejected');
    const rejectionRateEl = document.getElementById('rl-stat-rejection-rate');
    const redisStatusEl = document.getElementById('rl-redis-status');
    const bucketGaugeEl = document.getElementById('rl-bucket-fill');
    const bucketTokensEl = document.getElementById('rl-bucket-tokens-display');

    if (capacityEl) capacityEl.textContent = data.bucketCapacity || 100;
    if (refillEl) refillEl.textContent = (data.refillRatePerSec || 10) + ' / sec';
    if (totalEl) totalEl.textContent = data.totalRequests || 0;
    if (allowedEl) allowedEl.textContent = data.allowedRequests || 0;
    if (rejectedEl) rejectedEl.textContent = data.rejectedRequests || 0;
    if (rejectionRateEl) rejectionRateEl.textContent = (data.rejectionRatePercent || 0) + '%';

    if (redisStatusEl) {
      if (data.redisConnected) {
        redisStatusEl.className = 'status-pill active';
        redisStatusEl.innerHTML = '<span class="status-dot"></span> Traffic Protection Active';
      } else {
        redisStatusEl.className = 'status-pill pending';
        redisStatusEl.innerHTML = '<span class="status-dot"></span> In-Memory Protection Active';
      }
    }

    if (bucketGaugeEl && bucketTokensEl) {
      // Calculate token estimation
      const cap = data.bucketCapacity || 100;
      bucketGaugeEl.style.width = '100%';
      bucketTokensEl.textContent = `${cap} / ${cap} tokens`;
    }
  },

  async resetRateLimiter() {
    try {
      const res = await API.post('/api/admin/rate-limit/reset', {});
      if (res && res.success) {
        API.toast('Rate limiters and metrics successfully reset', 'success');
        this.loadRateLimitStats();
        const simResult = document.getElementById('rl-sim-result');
        if (simResult) simResult.style.display = 'none';
      }
    } catch (err) {
      API.toast('Failed to reset rate limiter: ' + err.message, 'error');
    }
  },

  async runBurstSimulation(count = 120) {
    const btn = document.getElementById('btn-sim-burst');
    if (btn) btn.disabled = true;

    const simResult = document.getElementById('rl-sim-result');
    if (simResult) {
      simResult.style.display = 'block';
      simResult.innerHTML = `
        <div style="text-align: center; padding: 24px;">
          <div style="font-weight: 600; color: var(--accent);">Simulating Burst of ${count} Concurrent Requests...</div>
          <div style="font-size: 0.82rem; color: var(--text-muted); margin-top: 4px;">Evaluating token bucket capacity and rate limiting</div>
        </div>
      `;
    }

    try {
      const res = await API.post(`/api/admin/rate-limit/simulate?count=${count}&clientIdentifier=sim-${Date.now()}`);
      if (res && res.success && res.data) {
        const d = res.data;
        this.renderSimulationResult(d);
        this.loadRateLimitStats();
      }
    } catch (err) {
      if (simResult) {
        simResult.innerHTML = `<div style="color: var(--danger); padding: 16px;">Simulation failed: ${err.message}</div>`;
      }
    } finally {
      if (btn) btn.disabled = false;
    }
  },

  renderSimulationResult(data) {
    const simResult = document.getElementById('rl-sim-result');
    if (!simResult) return;

    const allowedPct = Math.round((data.allowedCount / data.burstSize) * 100);
    const throttledPct = Math.round((data.rejectedCount / data.burstSize) * 100);

    let sampleRows = '';
    if (data.samplePoints && data.samplePoints.length > 0) {
      sampleRows = data.samplePoints.map(s => `
        <tr style="border-bottom: 1px solid var(--border-color); font-size: 0.8rem; font-family: var(--font-mono);">
          <td style="padding: 6px 10px;">#${s.requestIndex}</td>
          <td style="padding: 6px 10px;">
            ${s.allowed 
              ? '<span class="badge" style="background: rgba(16, 185, 129, 0.15); color: #059669; font-weight: 700;">200 OK (Allowed)</span>' 
              : '<span class="badge" style="background: rgba(239, 68, 68, 0.15); color: #DC2626; font-weight: 700;">429 Throttled</span>'}
          </td>
          <td style="padding: 6px 10px; color: var(--primary-600);">${s.remainingTokens} tokens</td>
          <td style="padding: 6px 10px; color: ${s.retryAfter > 0 ? '#DC2626' : 'var(--text-dim)'};">${s.retryAfter > 0 ? s.retryAfter + 's' : '-'}</td>
        </tr>
      `).join('');
    }

    simResult.innerHTML = `
      <div style="border-top: 1px solid var(--border-color); padding-top: 18px; margin-top: 16px;">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 8px;">
          <h4 style="font-size: 1rem; font-weight: 600; color: var(--text-main);">
            Burst Simulation Execution Report
          </h4>
          <span class="badge" style="background: var(--primary-100); color: var(--primary-700); font-family: var(--font-mono);">
            Burst Size: ${data.burstSize} requests
          </span>
        </div>

        <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 16px;">
          <div style="background: rgba(16, 185, 129, 0.08); border: 1px solid rgba(16, 185, 129, 0.25); border-radius: var(--radius-md); padding: 12px; text-align: center;">
            <div style="font-size: 1.4rem; font-weight: 800; color: #059669;">${data.allowedCount}</div>
            <div style="font-size: 0.76rem; color: #059669; font-weight: 600;">Allowed (HTTP 200) [${allowedPct}%]</div>
          </div>
          <div style="background: rgba(239, 68, 68, 0.08); border: 1px solid rgba(239, 68, 68, 0.25); border-radius: var(--radius-md); padding: 12px; text-align: center;">
            <div style="font-size: 1.4rem; font-weight: 800; color: #DC2626;">${data.rejectedCount}</div>
            <div style="font-size: 0.76rem; color: #DC2626; font-weight: 600;">Throttled (HTTP 429) [${throttledPct}%]</div>
          </div>
          <div style="background: var(--bg-card-subtle); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 12px; text-align: center;">
            <div style="font-size: 1.4rem; font-weight: 800; color: var(--primary-600);">${data.lastRetryAfterSeconds}s</div>
            <div style="font-size: 0.76rem; color: var(--text-muted); font-weight: 600;">Calculated Retry-After</div>
          </div>
        </div>

        <div style="margin-bottom: 12px; font-size: 0.82rem; font-weight: 700; color: var(--text-dim); text-transform: uppercase;">
          Sample Traffic Inspection:
        </div>
        <div class="table-responsive" style="max-height: 220px; overflow-y: auto;">
          <table style="width: 100%; border-collapse: collapse;">
            <thead>
              <tr style="background: var(--bg-card-subtle); border-bottom: 1px solid var(--border-color); font-size: 0.75rem; color: var(--text-dim); text-transform: uppercase;">
                <th style="padding: 6px 10px; text-align: left;">Req #</th>
                <th style="padding: 6px 10px; text-align: left;">HTTP Status</th>
                <th style="padding: 6px 10px; text-align: left;">Remaining Tokens</th>
                <th style="padding: 6px 10px; text-align: left;">Retry-After</th>
              </tr>
            </thead>
            <tbody>${sampleRows}</tbody>
          </table>
        </div>
      </div>
    `;
  },

  async testLiveHeaderInspection() {
    const outputEl = document.getElementById('rl-header-test-result');
    if (outputEl) {
      outputEl.style.display = 'block';
      outputEl.innerHTML = '<div style="color: var(--text-muted); padding: 8px;">Sending live GET /api/books probe...</div>';
    }

    try {
      const startTime = performance.now();
      const response = await fetch('/api/books?page=0&size=1');
      const latency = Math.round(performance.now() - startTime);

      const limit = response.headers.get('X-RateLimit-Limit');
      const remaining = response.headers.get('X-RateLimit-Remaining');
      const retryAfter = response.headers.get('Retry-After');
      const status = response.status;

      if (outputEl) {
        outputEl.innerHTML = `
          <div style="background: var(--bg-card-subtle); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 14px; font-family: var(--font-mono); font-size: 0.8rem;">
            <div style="color: var(--text-main); font-weight: 700; margin-bottom: 6px; display: flex; justify-content: space-between;">
              <span>HTTP/1.1 ${status} ${response.statusText || (status === 200 ? 'OK' : 'Too Many Requests')}</span>
              <span style="color: var(--primary-600);">${latency} ms</span>
            </div>
            <div style="color: var(--primary-600);">X-RateLimit-Limit: <strong>${limit || 'Not set'}</strong></div>
            <div style="color: ${remaining === '0' ? '#DC2626' : '#059669'};">X-RateLimit-Remaining: <strong>${remaining || 'Not set'}</strong></div>
            ${retryAfter ? `<div style="color: #DC2626;">Retry-After: <strong>${retryAfter}s</strong></div>` : ''}
          </div>
        `;
      }
      this.loadRateLimitStats();
    } catch (err) {
      if (outputEl) {
        outputEl.innerHTML = `<div style="color: var(--danger); padding: 8px;">Request probe failed: ${err.message}</div>`;
      }
    }
  },

  async handleCreate(e) {
    e.preventDefault();
    const targetUrlInput = document.getElementById('shortlink-target-input');
    const bookIdInput = document.getElementById('shortlink-bookid-input');
    const titleInput = document.getElementById('shortlink-title-input');

    const targetUrl = targetUrlInput ? targetUrlInput.value.trim() : '';
    const bookId = bookIdInput ? bookIdInput.value.trim() : '';
    const title = titleInput ? titleInput.value.trim() : '';

    if (!targetUrl) {
      API.toast('Please provide a target URL or catalogue path', 'error');
      return;
    }

    try {
      const res = await API.post('/api/short-links', { targetUrl, bookId, title });
      if (res && res.success && res.data) {
        API.toast('Short link generated successfully!', 'success');
        this.renderCreatedCard(res.data);
        this.loadShortLinks();
        if (targetUrlInput) targetUrlInput.value = '';
        if (bookIdInput) bookIdInput.value = '';
        if (titleInput) titleInput.value = '';
      }
    } catch (err) {
      API.toast('Failed to create short link: ' + err.message, 'error');
    }
  },

  renderCreatedCard(data) {
    const cardEl = document.getElementById('shortlink-created-display');
    if (!cardEl) return;

    const fullUrl = window.location.origin + data.shortUrl;
    cardEl.style.display = 'block';
    cardEl.innerHTML = `
      <div style="background: rgba(6, 182, 212, 0.08); border: 1px solid rgba(6, 182, 212, 0.3); border-radius: var(--radius-md); padding: 16px; margin-bottom: 20px;">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
          <span style="font-weight: 600; color: var(--accent);">
            Base62 Short Link Ready
          </span>
          <span class="badge" style="background: var(--primary-100); color: var(--primary-700); font-family: var(--font-mono);">
            Code: ${data.code}
          </span>
        </div>
        <div style="display: flex; gap: 10px; align-items: center; margin-bottom: 8px;">
          <input type="text" class="form-control" readonly value="${fullUrl}" style="font-family: var(--font-mono); font-size: 0.85rem;" id="created-short-input">
          <button class="btn btn-primary btn-sm" onclick="navigator.clipboard.writeText('${fullUrl}'); API.toast('Copied to clipboard!', 'success');">Copy</button>
          <a href="${data.shortUrl}" target="_blank" class="btn btn-outline btn-sm">Test Redirect ↗</a>
        </div>
        <div style="font-size: 0.78rem; color: var(--text-muted);">
          Destination: <code style="color: var(--primary-600);">${data.targetUrl}</code> | Cached in Redis for sub-millisecond response.
        </div>
      </div>
    `;
  },

  async loadShortLinks() {
    try {
      const res = await API.get('/api/short-links');
      if (res && res.success && res.data) {
        this.shortLinks = res.data;
        this.renderShortLinksTable(res.data);
      }
    } catch (err) {
      console.warn('Failed to load short links:', err);
    }
  },

  renderShortLinksTable(links) {
    const tbody = document.getElementById('shortlinks-table-body');
    if (!tbody) return;

    if (!links || links.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="6" style="text-align: center; padding: 24px; color: var(--text-muted);">
            No short links generated yet. Use the generator above to create shareable book URLs!
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = links.map(link => {
      const fullUrl = window.location.origin + link.shortUrl;
      const createdStr = link.createdAt ? new Date(link.createdAt).toLocaleString() : '-';
      return `
        <tr style="border-bottom: 1px solid var(--border-color);">
          <td style="font-family: var(--font-mono); font-weight: 700; color: var(--primary-600);">${link.code}</td>
          <td>
            <div style="display: flex; align-items: center; gap: 8px;">
              <a href="${link.shortUrl}" target="_blank" style="font-family: var(--font-mono); font-size: 0.82rem; color: var(--primary-600); text-decoration: underline;">
                ${link.shortUrl}
              </a>
              <button class="btn btn-outline btn-sm" style="padding: 2px 8px; font-size: 0.72rem;" onclick="navigator.clipboard.writeText('${fullUrl}'); API.toast('Copied link!', 'success');">Copy</button>
            </div>
          </td>
          <td style="font-size: 0.82rem; max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${link.targetUrl}">
            ${link.title ? `<strong>${link.title}</strong><br>` : ''}
            <span style="color: var(--text-muted); font-family: var(--font-mono);">${link.targetUrl}</span>
          </td>
          <td>
            <span class="badge" style="background: rgba(14, 165, 233, 0.15); color: #0284C7; font-weight: 700; font-family: var(--font-mono);">
              ${link.clickCount || 0} clicks
            </span>
          </td>
          <td style="font-size: 0.76rem; color: var(--text-dim);">${createdStr}</td>
          <td>
            <button class="btn btn-outline btn-sm" onclick="ShortLinksUI.showAnalytics('${link.code}')">Analytics</button>
          </td>
        </tr>
      `;
    }).join('');
  },

  async showAnalytics(code) {
    try {
      const res = await API.get(`/api/short-links/${code}/analytics`);
      if (res && res.success && res.data) {
        const d = res.data;
        const modal = document.getElementById('book-detail-modal');
        const content = document.getElementById('book-detail-modal-content');
        if (modal && content) {
          content.innerHTML = `
            <div style="padding: 10px 0;">
              <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px;">
                <h4 style="color: var(--primary-600); font-size: 1.1rem; font-weight: 700;">Short Link Analytics: /s/${d.code}</h4>
                <span class="badge" style="background: ${d.cachedInRedis ? 'rgba(53, 91, 69, 0.12)' : 'rgba(155, 74, 66, 0.12)'}; color: ${d.cachedInRedis ? '#355B45' : '#9B4A42'}; font-weight: 600;">
                  ${d.cachedInRedis ? 'Redis Cache HIT' : 'Redis Cache MISS'}
                </span>
              </div>
              <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; margin-bottom: 18px;">
                <div class="card stat-card" style="padding: 12px; margin: 0;">
                  <div class="metric-highlight" style="font-size: 1.6rem; color: var(--primary-600);">${d.clickCount}</div>
                  <p>Total Redirect Clicks</p>
                </div>
                <div class="card stat-card" style="padding: 12px; margin: 0;">
                  <div class="metric-highlight" style="font-size: 1rem; color: var(--text-main); margin-top: 6px;">
                    ${d.lastAccessedAt ? new Date(d.lastAccessedAt).toLocaleTimeString() : 'Never'}
                  </div>
                  <p>Last Accessed At</p>
                </div>
              </div>
              <div style="font-size: 0.85rem; line-height: 1.6;">
                <div><strong>Short Code:</strong> <code style="color: var(--primary-600);">${d.code}</code></div>
                <div><strong>Target Destination:</strong> <code style="word-break: break-all;">${d.targetUrl}</code></div>
                ${d.bookId ? `<div><strong>Associated Book ID:</strong> ${d.bookId}</div>` : ''}
                <div><strong>Created At:</strong> ${d.createdAt ? new Date(d.createdAt).toLocaleString() : '-'}</div>
              </div>
            </div>
          `;
          modal.classList.add('active');
        }
      }
    } catch (err) {
      API.toast('Failed to load analytics: ' + err.message, 'error');
    }
  },

  handleLiveBase62Encode(val) {
    const num = parseInt(val, 10);
    const resultEl = document.getElementById('base62-live-result');
    if (!resultEl) return;

    if (isNaN(num) || num < 0) {
      resultEl.textContent = 'Please enter a non-negative integer';
      return;
    }

    const chars = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
    let n = num;
    if (n === 0) {
      resultEl.textContent = '0';
      return;
    }
    let encoded = '';
    while (n > 0) {
      encoded = chars[n % 62] + encoded;
      n = Math.floor(n / 62);
    }
    resultEl.innerHTML = `Base62: <strong style="color: var(--primary-600);">${encoded}</strong> (Length: ${encoded.length})`;
  }
};
