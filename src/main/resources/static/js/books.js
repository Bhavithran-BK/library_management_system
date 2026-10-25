/**
 * Book Catalogue, Search, Filtering, and Book Details Modal.
 * Editorial university library design with genuine book jackets and responsive grid.
 */
const Books = {
  currentCategory: 'ALL',
  currentSearch: '',
  currentAvailability: '',
  currentPage: 0,
  pageSize: 12,
  totalPages: 1,
  allLoadedBooks: [],

  async loadCatalogue() {
    const grid = document.getElementById('books-catalogue-grid');
    if (!grid) return;

    grid.innerHTML = `
      <div class="loading-block">
        <div class="loading-spinner"></div>
        <p>Loading catalogue collection…</p>
      </div>
    `;

    try {
      let queryUrl;
      if (this.currentSearch && this.currentSearch.trim() !== '') {
        queryUrl = `/api/search?q=${encodeURIComponent(this.currentSearch.trim())}&page=${this.currentPage}&size=${this.pageSize}`;
      } else {
        queryUrl = `/api/books?page=${this.currentPage}&size=${this.pageSize}`;
        if (this.currentCategory && this.currentCategory !== 'ALL') {
          queryUrl += `&category=${encodeURIComponent(this.currentCategory)}`;
        }
      }

      const res = await API.get(queryUrl);
      if (res && res.success && res.data) {
        this.allLoadedBooks = res.data.content || [];
        this.totalPages = res.data.totalPages || 1;
        this.applyFiltersAndRender();
        this.renderPagination();
      }
    } catch (err) {
      grid.innerHTML = `
        <div class="empty-state-block">
          <p class="empty-state-title">Unable to load catalogue</p>
          <p class="empty-state-subtitle">${API.escapeHtml ? API.escapeHtml(err.message) : err.message}</p>
          <button class="btn btn-outline btn-sm" onclick="Books.loadCatalogue()">Try Again</button>
        </div>
      `;
    }
  },

  setAvailabilityFilter(val) {
    this.currentAvailability = val;
    this.applyFiltersAndRender();
  },

  applyFiltersAndRender() {
    let filtered = [...this.allLoadedBooks];

    if (this.currentAvailability === 'true') {
      filtered = filtered.filter(b => b.availableCopies > 0);
    } else if (this.currentAvailability === 'false') {
      filtered = filtered.filter(b => b.availableCopies <= 0);
    }

    this.renderBooks(filtered);
  },

  renderBooks(books) {
    const grid = document.getElementById('books-catalogue-grid');
    if (!grid) return;

    if (!books || books.length === 0) {
      grid.innerHTML = `
        <div class="empty-state-block">
          <p class="empty-state-title">No books match your criteria</p>
          <p class="empty-state-subtitle">Try adjusting your search terms or availability filter.</p>
          <button class="btn btn-outline btn-sm" onclick="Books.resetFilters()">Clear Filters</button>
        </div>
      `;
      return;
    }

    grid.innerHTML = books.map(book => {
      const isAvailable = book.availableCopies > 0;
      const statusClass = isAvailable ? 'available' : 'unavailable';
      const statusLabel = isAvailable 
        ? `${book.availableCopies} available`
        : 'Checked out';

      const authorsStr = Array.isArray(book.authors) 
        ? book.authors.join(', ') 
        : (book.authors || 'Unknown');

      const coverHtml = BookCovers.coverMarkup(book.isbn, book.title, authorsStr, 'M');
      const safeId = (book.bookId || '').replace(/'/g, "\\'");

      return `
        <article class="book-card" tabindex="0" role="button" aria-label="View details for ${BookCovers.escapeHtml(book.title)}" onclick="Books.openBookPage('${safeId}')" onkeydown="if(event.key==='Enter'||event.key===' ') { event.preventDefault(); Books.openBookPage('${safeId}'); }">
          <div class="book-cover-wrap">
            ${coverHtml}
          </div>
          <div class="book-card-info">
            <span class="book-card-category">${BookCovers.escapeHtml(book.category || 'General')}</span>
            <h3 class="book-card-title">${BookCovers.escapeHtml(book.title)}</h3>
            <p class="book-card-authors">${BookCovers.escapeHtml(authorsStr)}</p>
            <div class="book-card-footer">
              <span class="status-indicator ${statusClass}">
                <span class="status-dot"></span>
                <span>${statusLabel}</span>
              </span>
              <span class="book-card-action">Details →</span>
            </div>
          </div>
        </article>
      `;
    }).join('');
  },

  resetFilters() {
    this.currentCategory = 'ALL';
    this.currentSearch = '';
    this.currentAvailability = '';
    this.currentPage = 0;

    const searchInput = document.getElementById('catalogue-search-input');
    if (searchInput) searchInput.value = '';

    const availFilter = document.getElementById('catalogue-avail-filter');
    if (availFilter) availFilter.value = '';

    document.querySelectorAll('.category-chips .chip').forEach(c => {
      c.classList.toggle('active', c.dataset.category === 'ALL');
    });

    this.loadCatalogue();
  },

  renderPagination() {
    const container = document.getElementById('books-pagination');
    if (!container) return;

    if (this.totalPages <= 1) {
      container.innerHTML = '';
      return;
    }

    container.innerHTML = `
      <div class="pagination-bar">
        <button class="btn btn-outline btn-sm" ${this.currentPage === 0 ? 'disabled' : ''} onclick="Books.changePage(${this.currentPage - 1})">
          ← Previous
        </button>
        <span class="pagination-status">
          Page ${this.currentPage + 1} of ${this.totalPages}
        </span>
        <button class="btn btn-outline btn-sm" ${this.currentPage >= this.totalPages - 1 ? 'disabled' : ''} onclick="Books.changePage(${this.currentPage + 1})">
          Next →
        </button>
      </div>
    `;
  },

  changePage(newPage) {
    if (newPage >= 0 && newPage < this.totalPages) {
      this.currentPage = newPage;
      this.loadCatalogue();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  },

  setCategory(category) {
    this.currentCategory = category;
    this.currentPage = 0;
    document.querySelectorAll('.category-chips .chip').forEach(c => {
      c.classList.toggle('active', c.dataset.category === category);
    });
    this.loadCatalogue();
  },

  handleSearch(query) {
    this.currentSearch = query;
    this.currentPage = 0;
    this.loadCatalogue();
    this.fetchSuggestions(query);
  },

  async fetchSuggestions(query) {
    const dropdown = document.getElementById('catalogue-suggest-dropdown');
    if (!dropdown) return;

    if (!query || query.trim().length < 1) {
      dropdown.style.display = 'none';
      dropdown.innerHTML = '';
      return;
    }

    try {
      const res = await API.get(`/api/search/suggest?prefix=${encodeURIComponent(query.trim())}&limit=6`);
      if (res && res.success && res.data && res.data.suggestions && res.data.suggestions.length > 0) {
        dropdown.innerHTML = res.data.suggestions.map(s => `
          <div class="suggest-item" onclick="Books.applySuggestion('${s.replace(/'/g, "\\'")}')">
            <span class="suggest-icon">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.75">
                <circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>
              </svg>
            </span>
            <span class="suggest-text">${BookCovers.escapeHtml(s)}</span>
          </div>
        `).join('');
        dropdown.style.display = 'block';
      } else {
        dropdown.style.display = 'none';
      }
    } catch (e) {
      dropdown.style.display = 'none';
    }
  },

  applySuggestion(term) {
    const input = document.getElementById('catalogue-search-input');
    if (input) input.value = term;
    const dropdown = document.getElementById('catalogue-suggest-dropdown');
    if (dropdown) dropdown.style.display = 'none';
    this.handleSearch(term);
  },

  openBookPage(bookId) {
    if (bookId) {
      window.location.href = `/book.html?id=${encodeURIComponent(bookId)}`;
    }
  },

  async openBookModal(bookId) {
    try {
      const res = await API.get(`/api/books/${bookId}`);
      if (!res || !res.success || !res.data) return;
      const b = res.data;

      const modal = document.getElementById('book-detail-modal');
      const dialog = modal.querySelector('.modal-dialog');
      if (dialog) dialog.classList.add('modal-book-detail');

      const content = document.getElementById('book-detail-modal-content');
      
      const authorsStr = Array.isArray(b.authors) ? b.authors.join(', ') : (b.authors || 'Unknown');
      const isAvailable = b.availableCopies > 0;
      const isLoggedIn = Auth.isLoggedIn();

      const coverHtml = BookCovers.coverMarkup(b.isbn, b.title, authorsStr, 'L');
      const statusClass = isAvailable ? 'available' : 'unavailable';
      const statusText = isAvailable 
        ? `${b.availableCopies} of ${b.totalCopies} copies available`
        : `All ${b.totalCopies} copies currently checked out`;

      let actionsHtml = '';
      if (!isLoggedIn) {
        actionsHtml = `
          <div class="modal-auth-prompt">
            <p>Sign in to borrow this book or place a reservation queue request.</p>
            <div class="modal-auth-actions">
              <button class="btn btn-primary btn-sm" onclick="App.closeAllModals(); Router.navigate('login');">Sign In</button>
              <button class="btn btn-outline btn-sm" onclick="App.closeAllModals(); Router.navigate('register');">Register</button>
            </div>
          </div>
        `;
      } else {
        actionsHtml = `
          <div class="book-detail-actions">
            ${isAvailable 
              ? `<button class="btn btn-primary" onclick="Books.borrowBook('${b.bookId}')">
                  Borrow Book
                </button>`
              : `<button class="btn btn-warning" onclick="Books.reserveBook('${b.bookId}')">
                  Reserve Book (Join Queue)
                </button>`
            }
            <button class="btn btn-outline" onclick="Books.shareBook('${b.bookId}', '${b.title.replace(/'/g, "\\'")}')">
              Share Link
            </button>
          </div>
        `;
      }

      content.innerHTML = `
        <div class="book-detail-grid">
          <div class="book-detail-cover">
            ${coverHtml}
          </div>
          <div class="book-detail-info">
            <span class="detail-category-label">${BookCovers.escapeHtml(b.category || 'General')}</span>
            <h2 class="detail-book-title">${BookCovers.escapeHtml(b.title)}</h2>
            <p class="detail-book-author">By <strong>${BookCovers.escapeHtml(authorsStr)}</strong></p>
            
            <div class="detail-availability-bar">
              <span class="status-indicator ${statusClass}">
                <span class="status-dot"></span>
                <strong>${statusText}</strong>
              </span>
            </div>

            <div class="detail-meta-table">
              <div class="meta-row">
                <span class="meta-label">Book ID</span>
                <span class="meta-val"><code>${BookCovers.escapeHtml(b.bookId)}</code></span>
              </div>
              <div class="meta-row">
                <span class="meta-label">ISBN</span>
                <span class="meta-val"><code>${BookCovers.escapeHtml(b.isbn || '—')}</code></span>
              </div>
              <div class="meta-row">
                <span class="meta-label">Shelf Location</span>
                <span class="meta-val">${BookCovers.escapeHtml(b.location || 'Main Stacks')}</span>
              </div>
            </div>

            ${b.description ? `
              <div class="detail-description">
                <h4 class="detail-section-title">Overview</h4>
                <p>${BookCovers.escapeHtml(b.description)}</p>
              </div>
            ` : ''}

            <div id="book-share-result" style="display: none; margin-top: 14px;"></div>

            ${actionsHtml}
          </div>
        </div>
      `;

      modal.classList.add('active');
    } catch (err) {
      API.toast(err.message, 'error');
    }
  },

  async borrowBook(bookId) {
    if (!Auth.isLoggedIn()) {
      API.toast('Please log in first', 'error');
      Router.navigate('login');
      return;
    }

    try {
      const memberId = Auth.getMemberId();
      const res = await API.post('/api/loans', { memberId, bookId });
      if (res && res.success) {
        API.toast(`Book borrowed successfully. Due date: ${new Date(res.data.dueAt).toLocaleDateString()}`, 'success');
        App.closeAllModals();
        this.loadCatalogue();
        App.loadDashboardData();
        if (Router.currentView === 'member') {
          MemberDashboard.loadAll();
        }
      }
    } catch (err) {
      API.toast(err.message, 'error');
    }
  },

  async reserveBook(bookId) {
    if (!Auth.isLoggedIn()) {
      API.toast('Please log in first', 'error');
      Router.navigate('login');
      return;
    }

    try {
      const memberId = Auth.getMemberId();
      const res = await API.post('/api/reservations', { memberId, bookId });
      if (res && res.success) {
        API.toast(`Reservation confirmed. Queue position: #${res.data.queuePosition}`, 'success');
        App.closeAllModals();
        this.loadCatalogue();
        App.loadDashboardData();
        if (Router.currentView === 'member') {
          MemberDashboard.loadAll();
        }
      }
    } catch (err) {
      API.toast(err.message, 'error');
    }
  },

  async shareBook(bookId, title) {
    try {
      const targetUrl = `/book.html?id=${encodeURIComponent(bookId)}`;
      const res = await API.post('/api/short-links', { targetUrl, bookId, title });
      const shareResult = document.getElementById('book-share-result');
      if (shareResult && res && res.success) {
        const fullShortUrl = window.location.origin + res.data.shortUrl;
        shareResult.style.display = 'block';
        shareResult.innerHTML = `
          <div class="share-link-box">
            <span class="share-link-label">Shareable Link:</span>
            <div class="share-link-row">
              <input type="text" class="form-control" readonly value="${fullShortUrl}" id="share-link-input" />
              <button class="btn btn-outline btn-sm" onclick="navigator.clipboard.writeText('${fullShortUrl}'); API.toast('Link copied to clipboard', 'success');">Copy</button>
            </div>
          </div>
        `;
      }
    } catch (err) {
      API.toast('Failed to generate short link: ' + err.message, 'error');
    }
  }
};
