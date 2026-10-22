/**
 * Scalable Library Management Platform
 * Core Application Manager, Router, Top Navbar Controller & UI Event Handlers.
 */
const Router = {
  currentView: 'dashboard',

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

    // View specific actions
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
      Distribution.init();
    }

    // If navbar is not pinned, auto-hide after navigation
    if (!App.isNavPinned) {
      App.hideNavbarImmediate();
    }

    // Close mobile menu if open
    document.getElementById('main-nav-menu')?.classList.remove('mobile-open');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
};

const App = {
  isNavPinned: false,
  navLeaveTimer: null,

  init() {
    Auth.init();
    this.initNavbar();

    // Setup navigation items
    document.querySelectorAll('.nav-item').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        const view = btn.dataset.view;
        if (view) Router.navigate(view);
      });
    });

    // Mobile menu toggle
    document.getElementById('mobile-menu-btn')?.addEventListener('click', (e) => {
      e.stopPropagation();
      document.getElementById('main-nav-menu')?.classList.toggle('mobile-open');
    });

    // Close modals on outside click or ESC
    document.querySelectorAll('.modal-overlay').forEach(modal => {
      modal.addEventListener('click', (e) => {
        if (e.target === modal) this.closeAllModals();
      });
    });
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') this.closeAllModals();
    });

    // Initial View
    Router.navigate('dashboard');
  },

  initNavbar() {
    const topNav = document.getElementById('top-navbar');
    const trigger = document.getElementById('top-nav-trigger');
    const peekPill = document.getElementById('nav-peek-indicator');
    const pinBtn = document.getElementById('btn-pin-nav');

    // Load persisted pin preference (default to unpinned / auto-hide)
    const storedPin = localStorage.getItem('lms_nav_pinned');
    if (storedPin === 'true') {
      this.setNavPinned(true);
    } else {
      this.setNavPinned(false);
    }

    // Hover-to-reveal event handlers
    const showNav = () => {
      if (this.navLeaveTimer) {
        clearTimeout(this.navLeaveTimer);
        this.navLeaveTimer = null;
      }
      topNav?.classList.add('nav-revealed');
    };

    const scheduleHideNav = () => {
      if (this.isNavPinned) return;
      if (this.navLeaveTimer) clearTimeout(this.navLeaveTimer);
      this.navLeaveTimer = setTimeout(() => {
        topNav?.classList.remove('nav-revealed');
      }, 250); // smooth 250ms debounce
    };

    trigger?.addEventListener('mouseenter', showNav);
    peekPill?.addEventListener('mouseenter', showNav);
    topNav?.addEventListener('mouseenter', showNav);

    // Click on peek pill also opens and toggles
    peekPill?.addEventListener('click', showNav);

    topNav?.addEventListener('mouseleave', scheduleHideNav);
    trigger?.addEventListener('mouseleave', scheduleHideNav);
  },

  toggleNavPin() {
    this.setNavPinned(!this.isNavPinned);
  },

  setNavPinned(pinned) {
    this.isNavPinned = pinned;
    localStorage.setItem('lms_nav_pinned', pinned ? 'true' : 'false');

    const topNav = document.getElementById('top-navbar');
    const pinBtn = document.getElementById('btn-pin-nav');
    const pinText = pinBtn?.querySelector('.pin-text');

    if (pinned) {
      topNav?.classList.add('pinned');
      document.body.classList.add('nav-is-pinned');
      pinBtn?.classList.add('pinned');
      if (pinText) pinText.innerText = 'Pinned';
      pinBtn?.setAttribute('title', 'Navbar is pinned. Click to enable auto-hide.');
    } else {
      topNav?.classList.remove('pinned');
      document.body.classList.remove('nav-is-pinned');
      pinBtn?.classList.remove('pinned');
      if (pinText) pinText.innerText = 'Pin Bar';
      pinBtn?.setAttribute('title', 'Click to pin navbar permanently.');
    }
  },

  hideNavbarImmediate() {
    if (this.navLeaveTimer) clearTimeout(this.navLeaveTimer);
    document.getElementById('top-navbar')?.classList.remove('nav-revealed');
  },

  async loadDashboardData() {
    try {
      const res = await API.get('/api/stats/dashboard');
      if (res && res.success && res.data) {
        const d = res.data;
        document.getElementById('stat-total-books').innerText = d.totalBooks;
        document.getElementById('stat-available-books').innerText = d.availableBooks;
        document.getElementById('stat-active-loans').innerText = d.activeLoans;
        document.getElementById('stat-total-reservations').innerText = d.totalReservations;
        document.getElementById('stat-pending-fines').innerText = `$${parseFloat(d.pendingFinesAmount || 0).toFixed(2)}`;
      }
    } catch (err) {
      console.warn('Dashboard stats error:', err);
    }
  },

  closeAllModals() {
    document.querySelectorAll('.modal-overlay').forEach(m => m.classList.remove('active'));
  }
};

document.addEventListener('DOMContentLoaded', () => {
  App.init();
});
