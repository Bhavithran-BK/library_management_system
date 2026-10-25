/**
 * Core Application Manager, Router, Sidebar & UI Event Handlers.
 */
const BREADCRUMB_LABELS = {
  catalogue: 'Catalogue',
  discovery: 'Search',
  dashboard: 'Dashboard',
  member: 'My Library',
  admin: 'Administration',
  shortlinks: 'Share Links',
  login: 'Sign In',
  register: 'Register',
  distribution: 'Storage',
  benchmark: 'ID Registry'
};

const Router = {
  currentView: 'catalogue',

  navigate(viewName) {
    this.currentView = viewName;
    document.querySelectorAll('.view-section').forEach(sec => sec.classList.remove('active'));
    document.querySelectorAll('.nav-item').forEach(item => {
      item.classList.toggle('active', item.dataset.view === viewName);
    });

    const target = document.getElementById(`view-${viewName}`);
    if (target) {
      target.classList.add('active');
    }

    App.updateBreadcrumb(viewName);

    if (viewName === 'dashboard') {
      App.loadDashboardData();
    } else if (viewName === 'catalogue') {
      Books.loadCatalogue();
    } else if (viewName === 'member') {
      MemberDashboard.loadAll();
    } else if (viewName === 'admin') {
      Admin.switchTab('books');
    } else if (viewName === 'discovery') {
      Discovery.init();
    } else if (viewName === 'distribution') {
      this.navigate('admin');
      Admin.switchTab('storage');
      return;
    } else if (viewName === 'benchmark') {
      this.navigate('admin');
      Admin.switchTab('idgen');
      return;
    } else if (viewName === 'shortlinks') {
      ShortLinksUI.init();
    }

    App.closeMobileSidebar();
    document.getElementById('main-nav-menu')?.classList.remove('mobile-open');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
};

const App = {
  init() {
    Auth.init();
    this.initSidebar();

    document.querySelectorAll('.nav-item').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const href = btn.getAttribute('href');
        if (href && !btn.dataset.view) {
          return;
        }
        e.preventDefault();
        const view = btn.dataset.view;
        if (view === 'simulation') {
          window.location.href = '/simulation.html';
          return;
        }
        if (view) Router.navigate(view);
      });
    });

    document.querySelectorAll('.modal-overlay').forEach(modal => {
      modal.addEventListener('click', (e) => {
        if (e.target === modal) this.closeAllModals();
      });
    });
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') this.closeAllModals();
    });

    // Check if URL has book ID parameter in hash (legacy catalogue links)
    const hash = window.location.hash || '';
    if (hash.includes('?id=')) {
      const parts = hash.split('?id=');
      if (parts[1]) {
        const bookId = parts[1].split('&')[0];
        if (bookId) {
          window.location.replace(`/book.html?id=${encodeURIComponent(bookId)}`);
          return;
        }
      }
    }

    this.setDashboardGreeting();
    Router.navigate('catalogue');
  },

  initSidebar() {
    const sidebar = document.getElementById('app-sidebar');
    const backdrop = document.getElementById('sidebar-backdrop');
    const menuBtn = document.getElementById('mobile-menu-btn');

    menuBtn?.addEventListener('click', (e) => {
      e.stopPropagation();
      sidebar?.classList.toggle('mobile-open');
      backdrop?.classList.toggle('visible', sidebar?.classList.contains('mobile-open'));
    });

    backdrop?.addEventListener('click', () => this.closeMobileSidebar());
  },

  closeMobileSidebar() {
    document.getElementById('app-sidebar')?.classList.remove('mobile-open');
    document.getElementById('sidebar-backdrop')?.classList.remove('visible');
  },

  updateBreadcrumb(viewName) {
    const el = document.getElementById('page-breadcrumb');
    if (!el) return;
    const label = BREADCRUMB_LABELS[viewName] || viewName;
    el.innerHTML = `Library / <strong>${label}</strong>`;
  },

  setDashboardGreeting() {
    const el = document.getElementById('dashboard-greeting');
    if (!el) return;
    const hour = new Date().getHours();
    let greeting = 'Good evening';
    if (hour < 12) greeting = 'Good morning';
    else if (hour < 17) greeting = 'Good afternoon';
    el.textContent = greeting;
  },

  async loadDashboardData() {
    this.setDashboardGreeting();
    try {
      const res = await API.get('/api/stats/dashboard');
      if (res && res.success && res.data) {
        const d = res.data;
        const set = (id, val) => {
          const node = document.getElementById(id);
          if (node) node.innerText = val;
        };
        set('stat-total-books', d.totalBooks);
        set('stat-available-books', d.availableBooks);
        set('stat-active-loans', d.activeLoans);
        if (document.getElementById('stat-total-reservations')) {
          document.getElementById('stat-total-reservations').innerText = d.totalReservations;
        }
        if (document.getElementById('stat-pending-fines')) {
          document.getElementById('stat-pending-fines').innerText = `$${parseFloat(d.pendingFinesAmount || 0).toFixed(2)}`;
        }
      }
    } catch (err) {
      console.warn('Dashboard stats error:', err);
    }

    await this.loadOverdueCount();
    await this.loadRecentBooks();
    await this.loadDueSoonActivity();
    this.renderQuickActions();
  },

  async loadOverdueCount() {
    const el = document.getElementById('stat-overdue-loans');
    if (!el) return;
    if (!Auth.isLoggedIn() || !Auth.isAdmin()) {
      el.textContent = '—';
      el.title = 'Sign in as staff to view library-wide overdue count';
      return;
    }
    try {
      const res = await API.get('/api/loans');
      if (res && res.success && Array.isArray(res.data)) {
        const overdue = res.data.filter(l => l.status === 'OVERDUE').length;
        el.textContent = overdue;
        el.title = '';
      }
    } catch (err) {
      el.textContent = '—';
    }
  },

  async loadRecentBooks() {
    const container = document.getElementById('dashboard-recent-books');
    if (!container) return;
    container.innerHTML = '<div class="loading-spinner"></div>';
    try {
      const res = await API.get('/api/books?page=0&size=50');
      const booksList = res?.data?.content || [];
      if (!res || !res.success || booksList.length === 0) {
        container.innerHTML = '<p class="empty-state-inline">No catalogue data available.</p>';
        return;
      }
      const books = [...booksList].sort((a, b) => {
        const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        return tb - ta;
      }).slice(0, 5);

      if (books.length === 0) {
        container.innerHTML = '<p class="empty-state-inline">No books in the catalogue yet.</p>';
        return;
      }

      container.innerHTML = books.map(book => {
        const authors = Array.isArray(book.authors) ? book.authors.join(', ') : (book.authors || '');
        const coverHtml = BookCovers.coverMarkup(book.isbn, book.title, authors, 'M');
        const safeId = (book.bookId || '').replace(/'/g, "\\'");
        return `
          <div class="recent-book-row" role="button" tabindex="0" onclick="Books.openBookPage('${safeId}')" onkeydown="if(event.key==='Enter')Books.openBookPage('${safeId}')">
            <div class="recent-book-thumb">${coverHtml}</div>
            <div class="recent-book-meta">
              <h4>${BookCovers.escapeHtml(book.title)}</h4>
              <p>${BookCovers.escapeHtml(authors)}${book.category ? ` · ${BookCovers.escapeHtml(book.category)}` : ''}</p>
            </div>
          </div>
        `;
      }).join('');
    } catch (err) {
      container.innerHTML = '<p class="empty-state-inline">Could not load recent titles.</p>';
    }
  },

  async loadDueSoonActivity() {
    const container = document.getElementById('dashboard-recent-activity');
    if (!container) return;

    if (!Auth.isLoggedIn()) {
      container.innerHTML = '<p class="empty-state-inline">Sign in to see loans due soon, or use staff access for all active loans.</p>';
      return;
    }

    container.innerHTML = '<div class="loading-spinner"></div>';
    try {
      let loans = [];
      if (Auth.isAdmin()) {
        const res = await API.get('/api/loans/active');
        if (res?.success && Array.isArray(res.data)) loans = res.data;
      } else {
        const res = await API.get(`/api/loans?memberId=${encodeURIComponent(Auth.getMemberId())}`);
        if (res?.success && Array.isArray(res.data)) {
          loans = res.data.filter(l => l.status === 'ACTIVE' || l.status === 'OVERDUE');
        }
      }

      if (loans.length === 0) {
        container.innerHTML = '<p class="empty-state-inline">No active loans to display.</p>';
        return;
      }

      const sorted = loans.sort((a, b) => new Date(a.dueAt) - new Date(b.dueAt)).slice(0, 6);
      const bookCache = {};

      const rows = await Promise.all(sorted.map(async loan => {
        if (!bookCache[loan.bookId]) {
          try {
            const br = await API.get(`/api/books/${loan.bookId}`);
            bookCache[loan.bookId] = br?.data?.title || loan.bookId;
          } catch (e) {
            bookCache[loan.bookId] = loan.bookId;
          }
        }
        const due = loan.dueAt ? new Date(loan.dueAt).toLocaleDateString() : '—';
        const isOverdue = loan.status === 'OVERDUE';
        const statusClass = isOverdue ? 'overdue' : 'active';
        const statusLabel = isOverdue ? 'Overdue' : 'Due';
        return `
          <div class="activity-row">
            <div>
              <strong>${bookCache[loan.bookId]}</strong>
              <div class="activity-meta">${statusLabel} ${due}${Auth.isAdmin() ? ` · ${loan.memberId}` : ''}</div>
            </div>
            <span class="status-pill ${statusClass}">${loan.status}</span>
          </div>
        `;
      }));

      container.innerHTML = rows.join('');
    } catch (err) {
      container.innerHTML = '<p class="empty-state-inline">Unable to load loan activity.</p>';
    }
  },

  renderQuickActions() {
    const bar = document.getElementById('dashboard-quick-actions');
    if (!bar) return;
    let html = '<span class="quick-actions-label">Quick actions</span>';
    if (Auth.isAdmin()) {
      html += `<button type="button" class="btn btn-outline btn-sm" onclick="Admin.openAddBookModal()">Add book</button>`;
    }
    html += `<button type="button" class="btn btn-outline btn-sm" onclick="Router.navigate('catalogue')">Issue from catalogue</button>`;
    if (!Auth.isLoggedIn()) {
      html += `<button type="button" class="btn btn-primary btn-sm" onclick="Router.navigate('register')">Register member</button>`;
    } else if (!Auth.isAdmin()) {
      html += `<button type="button" class="btn btn-outline btn-sm" onclick="Router.navigate('member')">My loans</button>`;
    }
    bar.innerHTML = html;
  },

  closeAllModals() {
    document.querySelectorAll('.modal-overlay').forEach(m => m.classList.remove('active'));
    document.getElementById('book-detail-modal')?.querySelector('.modal-dialog')?.classList.remove('modal-book-detail');
  }
};

document.addEventListener('DOMContentLoaded', () => {
  App.init();
});
