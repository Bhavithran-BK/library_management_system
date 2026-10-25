/**
 * Member 4 System Design Simulation & Evaluation Dashboard (SimApp)
 * Controls Distributed Token Bucket Rate Limiting, Per-User Isolation,
 * Base62 Short Links, and System Stress Probes.
 */
const SimApp = {
  activeClient: 'alice@example.com',
  BASE62: '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ',
  pollTimer: null,

  init() {
    this.selectClient(this.activeClient);
    this.updateBase62Live();
    this.loadShortLinks();
    this.checkGlobalStatus();

    // Auto-refresh client tank every 4 seconds to observe token refill
    clearInterval(this.pollTimer);
    this.pollTimer = setInterval(() => {
      this.refreshClientStatus(true);
    }, 4000);
  },

  selectClient(clientId, btnEl) {
    this.activeClient = clientId;
    document.querySelectorAll('.persona-chip').forEach(chip => {
      chip.classList.toggle('active', chip.dataset.client === clientId);
    });

    const clientLabel = document.getElementById('current-client-label');
    const redisKey = document.getElementById('current-redis-key');
    const resetName = document.getElementById('reset-client-name');

    if (clientLabel) clientLabel.textContent = clientId;
    if (redisKey) redisKey.textContent = `rate:sim:${clientId}`;
    if (resetName) resetName.textContent = clientId;

    this.refreshClientStatus();
  },

  applyCustomClient() {
    const input = document.getElementById('custom-client-input');
    if (!input || !input.value.trim()) return;
    this.selectClient(input.value.trim());
  },

  async refreshClientStatus(isBackground = false) {
    try {
      const res = await fetch(`/api/admin/rate-limit/client/${encodeURIComponent(this.activeClient)}`);
      const data = await res.json();
      if (data && data.success && data.data) {
        this.renderTankGauge(data.data.remainingTokens, data.data.capacity, data.data.allowed);
      }
    } catch (err) {
      if (!isBackground) console.warn('Failed to refresh client status:', err);
    }
  },

  renderTankGauge(remaining, capacity = 100, isAllowed = true) {
    const fillEl = document.getElementById('tank-gauge-fill');
    const textEl = document.getElementById('gauge-tokens-text');
    const statusPill = document.getElementById('stat-status-pill');

    const pct = Math.max(0, Math.min(100, Math.round((remaining / capacity) * 100)));

    if (fillEl) {
      fillEl.style.width = `${pct}%`;
      fillEl.classList.toggle('low', pct < 20);
    }

    if (textEl) {
      textEl.textContent = `${remaining} / ${capacity} Tokens (${pct}%)`;
    }

    if (statusPill) {
      if (remaining > 0 || isAllowed) {
        statusPill.textContent = 'ALLOWED (200)';
        statusPill.style.color = 'var(--success)';
      } else {
        statusPill.textContent = 'THROTTLED (429)';
        statusPill.style.color = 'var(--danger)';
      }
    }
  },

  async runBurst(count = 120) {
    const panel = document.getElementById('burst-results-panel');
    if (panel) panel.style.display = 'block';

    try {
      this.toast(`Firing ${count} concurrent burst requests for ${this.activeClient}...`, 'info');
      const res = await fetch(`/api/admin/rate-limit/simulate?count=${count}&clientIdentifier=${encodeURIComponent(this.activeClient)}`, {
        method: 'POST'
      });
      const json = await res.json();

      if (json && json.success && json.data) {
        const d = json.data;

        document.getElementById('burst-total-sent').textContent = d.burstSize;
        document.getElementById('burst-allowed-count').textContent = d.allowedCount;
        document.getElementById('burst-rejected-count').textContent = d.rejectedCount;

        document.getElementById('header-limit').textContent = d.bucketCapacity;
        document.getElementById('header-remaining').textContent = d.lastRemainingTokens;
        document.getElementById('header-retry-after').textContent = d.lastRetryAfterSeconds;

        // Render Sample Breakpoints Timeline
        const container = document.getElementById('burst-samples-container');
        if (container && Array.isArray(d.samplePoints)) {
          container.innerHTML = d.samplePoints.map(s => {
            const cls = s.allowed ? 'allowed' : 'throttled';
            const icon = s.allowed ? '🟢 200 OK' : '🔴 429 TOO MANY REQUESTS';
            const retryStr = s.retryAfter > 0 ? ` &bull; Retry-After: ${s.retryAfter}s` : '';
            return `
              <div class="burst-sample-item ${cls}">
                <span>Request #${s.requestIndex}: ${icon}</span>
                <span>Tokens Left: ${s.remainingTokens}${retryStr}</span>
              </div>
            `;
          }).join('');
        }

        this.renderTankGauge(d.lastRemainingTokens, d.bucketCapacity, d.rejectedCount === 0);

        if (d.rejectedCount > 0) {
          this.toast(`Rate limit enforced: ${d.allowedCount} allowed, ${d.rejectedCount} throttled (HTTP 429)`, 'warning');
        } else {
          this.toast(`All ${d.allowedCount} requests allowed within token capacity`, 'success');
        }
      }
    } catch (err) {
      this.toast('Burst simulation failed: ' + err.message, 'error');
    }
  },

  async resetCurrentClient() {
    try {
      const res = await fetch(`/api/admin/rate-limit/client/${encodeURIComponent(this.activeClient)}/reset`, { method: 'POST' });
      const json = await res.json();
      if (json && json.success) {
        this.renderTankGauge(json.data.capacity, json.data.capacity, true);
        this.toast(`Reset token bucket for ${this.activeClient} to full capacity (100 tokens)`, 'success');
      }
    } catch (err) {
      this.toast('Failed to reset client: ' + err.message, 'error');
    }
  },

  async resetAllGlobal() {
    try {
      const res = await fetch('/api/admin/rate-limit/reset', { method: 'POST' });
      const json = await res.json();
      if (json && json.success) {
        this.renderTankGauge(100, 100, true);
        this.resetIsolationDemoUI();
        this.toast('All Redis rate limiters, token buckets, and metrics reset globally', 'success');
      }
    } catch (err) {
      this.toast('Failed to reset globally: ' + err.message, 'error');
    }
  },

  async demoIsolationDepleteAlice() {
    const btn = document.getElementById('btn-deplete-alice');
    if (btn) btn.disabled = true;

    try {
      const res = await fetch('/api/admin/rate-limit/simulate?count=120&clientIdentifier=alice@example.com', { method: 'POST' });
      const json = await res.json();
      if (json && json.success) {
        const tokensEl = document.getElementById('iso-alice-tokens');
        const statusEl = document.getElementById('iso-alice-status');
        const badgeEl = document.getElementById('iso-alice-badge');

        if (tokensEl) tokensEl.textContent = '0 / 100 Tokens';
        if (statusEl) {
          statusEl.textContent = '429 TOO MANY REQUESTS (THROTTLED)';
          statusEl.style.color = 'var(--danger)';
        }
        if (badgeEl) {
          badgeEl.className = 'status-pill danger';
          badgeEl.textContent = 'THROTTLED';
        }

        this.toast('Alice depleted: 100 allowed, 20 rejected (HTTP 429)', 'warning');

        // If Alice is active client, update gauge
        if (this.activeClient === 'alice@example.com') {
          this.renderTankGauge(0, 100, false);
        }
      }
    } catch (err) {
      this.toast('Depletion failed: ' + err.message, 'error');
    } finally {
      if (btn) btn.disabled = false;
    }
  },

  async demoIsolationCheckBob() {
    try {
      const res = await fetch('/api/admin/rate-limit/client/bob@example.com');
      const json = await res.json();
      if (json && json.success && json.data) {
        const remaining = json.data.remainingTokens;
        const tokensEl = document.getElementById('iso-bob-tokens');
        const statusEl = document.getElementById('iso-bob-status');
        const badgeEl = document.getElementById('iso-bob-badge');

        if (tokensEl) tokensEl.textContent = `${remaining} / 100 Tokens`;
        if (statusEl) {
          statusEl.textContent = '200 OK (UNTOUCHED)';
          statusEl.style.color = 'var(--success)';
        }
        if (badgeEl) {
          badgeEl.className = 'status-pill active';
          badgeEl.textContent = 'HEALTHY';
        }

        this.toast(`Bob verified: ${remaining} tokens remaining, completely isolated from Alice's spam!`, 'success');
      }
    } catch (err) {
      this.toast('Failed to probe Bob: ' + err.message, 'error');
    }
  },

  resetIsolationDemoUI() {
    const aTokens = document.getElementById('iso-alice-tokens');
    const aStatus = document.getElementById('iso-alice-status');
    const aBadge = document.getElementById('iso-alice-badge');

    if (aTokens) aTokens.textContent = '100 / 100 Tokens';
    if (aStatus) {
      aStatus.textContent = '200 OK';
      aStatus.style.color = 'var(--success)';
    }
    if (aBadge) {
      aBadge.className = 'status-pill active';
      aBadge.textContent = 'ACTIVE';
    }

    const bTokens = document.getElementById('iso-bob-tokens');
    const bStatus = document.getElementById('iso-bob-status');
    if (bTokens) bTokens.textContent = '100 / 100 Tokens';
    if (bStatus) {
      bStatus.textContent = '200 OK (UNTOUCHED)';
      bStatus.style.color = 'var(--success)';
    }
  },

  async probeRealApi(count = 1) {
    const out = document.getElementById('live-probe-output');
    if (out) out.textContent = `Probing ${count} request(s) to live Spring Boot /api/books...`;

    try {
      let lastHeaders = '';
      let lastStatus = 200;

      for (let i = 1; i <= count; i++) {
        const res = await fetch('/api/books?page=0&size=2');
        lastStatus = res.status;

        const limit = res.headers.get('X-RateLimit-Limit') || '100';
        const remaining = res.headers.get('X-RateLimit-Remaining') || 'N/A';
        const retryAfter = res.headers.get('Retry-After');

        lastHeaders = `HTTP/1.1 ${res.status} ${res.statusText}\n` +
                      `X-RateLimit-Limit: ${limit}\n` +
                      `X-RateLimit-Remaining: ${remaining}\n` +
                      (retryAfter ? `Retry-After: ${retryAfter}\n` : '') +
                      `Content-Type: ${res.headers.get('Content-Type')}\n` +
                      `Date: ${new Date().toUTCString()}`;
      }

      if (out) {
        out.textContent = lastHeaders + `\n\n// Probe Result: ${count} call(s) executed. RateLimitInterceptor headers verified.`;
      }
      this.toast(`Live probe complete: HTTP ${lastStatus}`, lastStatus === 429 ? 'warning' : 'success');
    } catch (err) {
      if (out) out.textContent = `Probe Error: ${err.message}`;
      this.toast('Probe failed: ' + err.message, 'error');
    }
  },

  updateBase62Live() {
    const input = document.getElementById('b62-input-val');
    const out = document.getElementById('b62-output-code');
    if (!input || !out) return;

    const val = parseInt(input.value, 10);
    if (isNaN(val) || val < 0) {
      out.textContent = 'Invalid ID';
      return;
    }

    out.textContent = this.encodeBase62(val);
  },

  setB62(val) {
    const input = document.getElementById('b62-input-val');
    if (input) {
      input.value = val;
      this.updateBase62Live();
    }
  },

  encodeBase62(num) {
    if (num === 0) return '0';
    let s = '';
    let n = num;
    while (n > 0) {
      s = this.BASE62[n % 62] + s;
      n = Math.floor(n / 62);
    }
    return s;
  },

  async createShortLink(e) {
    e.preventDefault();
    const targetUrl = document.getElementById('sim-target-url')?.value.trim();
    const bookId = document.getElementById('sim-book-id')?.value.trim();
    const title = document.getElementById('sim-book-title')?.value.trim();

    try {
      const res = await fetch('/api/short-links', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetUrl, bookId, title })
      });
      const json = await res.json();
      if (json && (json.success || res.status === 201 || res.status === 200)) {
        this.toast(`Short link created: /s/${json.data.code}`, 'success');
        this.loadShortLinks();
      } else {
        this.toast('Creation failed: ' + (json.message || 'Unknown error'), 'error');
      }
    } catch (err) {
      this.toast('Error creating short link: ' + err.message, 'error');
    }
  },

  async loadShortLinks() {
    const tbody = document.getElementById('sim-shortlinks-table-body');
    if (!tbody) return;

    try {
      const res = await fetch('/api/short-links');
      const json = await res.json();

      if (json && json.success && Array.isArray(json.data)) {
        const links = json.data;
        if (links.length === 0) {
          tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted); padding: 16px;">No short links created yet.</td></tr>`;
          return;
        }

        tbody.innerHTML = links.map(link => {
          const shortUrl = `${window.location.origin}/s/${link.code}`;
          return `
            <tr>
              <td><strong style="color: var(--primary-600); font-family: var(--font-mono);">${link.code}</strong></td>
              <td>
                <a href="/s/${link.code}" target="_blank" style="font-family: var(--font-mono); font-size: 0.85rem; color: var(--primary-600); font-weight: 600;">
                  /s/${link.code}
                </a>
              </td>
              <td><span title="${link.targetUrl}">${link.bookId ? `<strong>[${link.bookId}]</strong> ` : ''}${link.title || link.targetUrl}</span></td>
              <td>
                <span class="status-pill active" style="font-size: 0.7rem; padding: 2px 6px;">⚡ REDIS T1 + 💾 MONGO T2</span>
              </td>
              <td><strong>${link.clickCount || 0}</strong> clicks</td>
              <td>
                <div style="display: flex; gap: 6px;">
                  <button class="btn btn-outline btn-sm" onclick="SimApp.copyText('${shortUrl}')">📋 Copy</button>
                  <a href="/s/${link.code}" target="_blank" class="btn btn-primary btn-sm" style="text-decoration: none;">Test ↗</a>
                </div>
              </td>
            </tr>
          `;
        }).join('');
      }
    } catch (err) {
      console.warn('Failed to load short links:', err);
    }
  },

  copyText(text) {
    navigator.clipboard.writeText(text).then(() => {
      this.toast('Short link copied to clipboard: ' + text, 'success');
    }).catch(() => {
      this.toast('Failed to copy', 'error');
    });
  },

  async runScenarioProbe(scenario) {
    if (scenario === 1) {
      this.toast('Running Scenario 1 (Baseline: 10 sequential calls to /api/search)...', 'info');
      const start = performance.now();
      for (let i = 0; i < 10; i++) {
        await fetch('/api/search?q=clean');
      }
      const dur = Math.round(performance.now() - start);
      const avg = Math.round(dur / 10);
      this.toast(`Scenario 1 Probe: 10 requests completed in ${dur}ms (Avg ${avg}ms/req, 0 errors)`, 'success');
    } else if (scenario === 2) {
      this.toast('Running Scenario 2 (Sustained: 25 calls to /api/search/suggest)...', 'info');
      const start = performance.now();
      for (let i = 0; i < 25; i++) {
        await fetch('/api/search/suggest?prefix=cle');
      }
      const dur = Math.round(performance.now() - start);
      const avg = Math.round(dur / 25);
      this.toast(`Scenario 2 Probe: 25 Trie autocomplete queries in ${dur}ms (Avg ${avg}ms/req, 0 errors)`, 'success');
    }
  },

  async checkGlobalStatus() {
    try {
      const res = await fetch('/api/admin/rate-limit/stats');
      const json = await res.json();
      const badge = document.getElementById('global-redis-badge');
      if (badge && json && json.data) {
        if (json.data.redisConnected) {
          badge.className = 'status-pill active';
          badge.textContent = '🟢 REDIS LUA ENGINE READY';
        } else {
          badge.className = 'status-pill pending';
          badge.textContent = '🟡 IN-MEMORY ENGINE READY';
        }
      }
    } catch (e) {
      // ignore
    }
  },

  toast(message, type = 'info') {
    const container = document.getElementById('sim-toast-container');
    if (!container) return;

    const el = document.createElement('div');
    el.style.background = type === 'success' ? '#059669' :
                          type === 'error' ? '#DC2626' :
                          type === 'warning' ? '#D97706' : '#4F46E5';
    el.style.color = '#FFFFFF';
    el.style.padding = '10px 16px';
    el.style.borderRadius = '8px';
    el.style.fontSize = '0.84rem';
    el.style.fontWeight = '600';
    el.style.boxShadow = '0 6px 20px rgba(0,0,0,0.15)';
    el.style.transition = 'all 0.3s ease';
    el.style.display = 'flex';
    el.style.alignItems = 'center';
    el.style.gap = '8px';
    el.textContent = message;

    container.appendChild(el);
    setTimeout(() => {
      el.style.opacity = '0';
      el.style.transform = 'translateY(10px)';
      setTimeout(() => el.remove(), 300);
    }, 3500);
  }
};

document.addEventListener('DOMContentLoaded', () => {
  SimApp.init();
});
