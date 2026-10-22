/**
 * Authentication and User State Management.
 */
const Auth = {
  currentUser: null,

  init() {
    const storedUser = localStorage.getItem('lms_user');
    if (storedUser) {
      try {
        this.currentUser = JSON.parse(storedUser);
      } catch (e) {
        this.currentUser = null;
      }
    }
    this.updateUI();
  },

  isLoggedIn() {
    return !!this.currentUser && !!API.getToken();
  },

  isAdmin() {
    return this.isLoggedIn() && this.currentUser.role === 'ADMIN';
  },

  getMemberId() {
    return this.currentUser ? this.currentUser.memberId : null;
  },

  async login(email, password) {
    try {
      const res = await API.post('/api/auth/login', { email, password });
      if (res && res.success && res.data) {
        API.setToken(res.data.token);
        this.currentUser = {
          memberId: res.data.memberId,
          name: res.data.name,
          email: res.data.email,
          role: res.data.role
        };
        localStorage.setItem('lms_user', JSON.stringify(this.currentUser));
        this.updateUI();
        API.toast(`Welcome back, ${this.currentUser.name}!`, 'success');
        Router.navigate('dashboard');
        App.loadDashboardData();
        return true;
      }
    } catch (err) {
      API.toast(err.message, 'error');
      return false;
    }
  },

  async register(name, email, password, phone, role = 'MEMBER') {
    try {
      const res = await API.post('/api/auth/register', { name, email, password, phone, role });
      if (res && res.success && res.data) {
        API.setToken(res.data.token);
        this.currentUser = {
          memberId: res.data.memberId,
          name: res.data.name,
          email: res.data.email,
          role: res.data.role
        };
        localStorage.setItem('lms_user', JSON.stringify(this.currentUser));
        this.updateUI();
        API.toast(`Account registered successfully as ${role}!`, 'success');
        Router.navigate('dashboard');
        App.loadDashboardData();
        return true;
      }
    } catch (err) {
      API.toast(err.message, 'error');
      return false;
    }
  },

  logout() {
    API.setToken(null);
    this.currentUser = null;
    localStorage.removeItem('lms_user');
    this.updateUI();
    API.toast('Logged out successfully', 'info');
    Router.navigate('login');
  },

  handleUnauthorized() {
    this.logout();
    API.toast('Session expired. Please log in again.', 'error');
  },

  updateUI() {
    const userCard = document.getElementById('sidebar-user-card');
    const guestNav = document.getElementById('nav-guest-group');
    const memberNav = document.getElementById('nav-member-group');
    const adminNav = document.getElementById('nav-admin-group');

    if (this.isLoggedIn()) {
      if (userCard) {
        userCard.style.display = 'flex';
        document.getElementById('user-avatar').innerText = (this.currentUser.name || 'U')[0].toUpperCase();
        document.getElementById('user-display-name').innerText = this.currentUser.name;
        
        const roleBadge = document.getElementById('user-role-badge');
        roleBadge.innerText = this.currentUser.role;
        roleBadge.className = `user-role-badge ${this.currentUser.role.toLowerCase()}`;
      }

      if (guestNav) guestNav.style.display = 'none';
      if (memberNav) memberNav.style.display = 'flex';

      if (adminNav) {
        adminNav.style.display = this.isAdmin() ? 'flex' : 'none';
      }
    } else {
      if (userCard) userCard.style.display = 'none';
      if (guestNav) guestNav.style.display = 'flex';
      if (memberNav) memberNav.style.display = 'none';
      if (adminNav) adminNav.style.display = 'none';
    }
  }
};
