/**
 * Centralized API Client with JWT authorization & Toast notifications.
 */
const API = {
  baseUrl: '',

  getToken() {
    return localStorage.getItem('lms_token');
  },

  setToken(token) {
    if (token) {
      localStorage.setItem('lms_token', token);
    } else {
      localStorage.removeItem('lms_token');
    }
  },

  escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  },

  async request(endpoint, options = {}) {
    const url = this.baseUrl + endpoint;
    const headers = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ...options.headers
    };

    const token = this.getToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const controller = new AbortController();
    const timeout = options.timeout || 8000;
    const timer = setTimeout(() => controller.abort(), timeout);

    try {
      const response = await fetch(url, {
        ...options,
        headers,
        signal: options.signal || controller.signal
      });
      clearTimeout(timer);

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        const errorMsg = data && data.message ? data.message : `HTTP ${response.status}: ${response.statusText}`;
        if (response.status === 401) {
          // Token expired or invalid
          Auth.handleUnauthorized();
        }
        throw new Error(errorMsg);
      }

      return data;
    } catch (err) {
      clearTimeout(timer);
      if (err.name === 'AbortError') {
        const timeoutErr = new Error(`Request timed out after ${timeout / 1000}s. The backend server might be offline or still starting up.`);
        console.error(`API Timeout [${endpoint}]:`, timeoutErr);
        throw timeoutErr;
      }
      console.error(`API Error [${endpoint}]:`, err);
      throw err;
    }
  },

  get(endpoint) {
    return this.request(endpoint, { method: 'GET' });
  },

  post(endpoint, body) {
    return this.request(endpoint, {
      method: 'POST',
      body: JSON.stringify(body)
    });
  },

  put(endpoint, body) {
    return this.request(endpoint, {
      method: 'PUT',
      body: JSON.stringify(body)
    });
  },

  delete(endpoint) {
    return this.request(endpoint, { method: 'DELETE' });
  },

  toast(message, type = 'info') {
    let container = document.getElementById('toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    
    let iconSvg = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>';
    if (type === 'success') {
      iconSvg = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>';
    } else if (type === 'error') {
      iconSvg = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>';
    }

    toast.innerHTML = `<span style="display:inline-flex;align-items:center;color:inherit;">${iconSvg}</span> <span>${message}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      toast.style.transition = 'all 0.3s ease';
      setTimeout(() => toast.remove(), 300);
    }, 4000);
  }
};
