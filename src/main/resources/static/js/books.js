/**
 * Book Catalogue, Search, Filtering, and Book Details Modal.
 */
const Books = {
  currentCategory: 'ALL',
  currentSearch: '',
  currentPage: 0,
  pageSize: 9,
  totalPages: 1,

  async loadCatalogue() {
    const grid = document.getElementById('books-catalogue-grid');
    if (!grid) return;

    grid.innerHTML = `<div style="grid-column: 1/-1; text-align: center; padding: 40px; color: var(--text-muted);">
      <div class="spinner" style="margin-bottom: 12px; font-size: 24px;">⏳</div>
      Loading books catalogue...
    </div>`;

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
        this.renderBooks(res.data.content);
        this.totalPages = res.data.totalPages || 1;
        this.renderPagination();
      }
    } catch (err) {
      grid.innerHTML = `<div style="grid-column: 1/-1; text-align: center; padding: 40px; color: var(--danger);">
        Failed to load books: ${err.message}
      </div>`;
    }
  },

  renderBooks(books) {
    const grid = document.getElementById('books-catalogue-grid');
    if (!books || books.length === 0) {
      grid.innerHTML = `<div style="grid-column: 1/-1; text-align: center; padding: 60px 20px; color: var(--text-muted);">
        <p style="font-size: 1.1rem; font-weight: 600; margin-bottom: 6px;">No books found</p>
        <p style="font-size: 0.85rem;">Try adjusting your search query or category filter.</p>
      </div>`;
      return;
    }

    grid.innerHTML = books.map(book => {
      const isAvailable = book.availableCopies > 0;
      let dotClass = 'available';
      let stockText = `${book.availableCopies} of ${book.totalCopies} available`;

      if (book.availableCopies === 0) {
        dotClass = 'out';
        stockText = `0 of ${book.totalCopies} available (Reserved/Loaned)`;
      } else if (book.availableCopies === 1) {
        dotClass = 'low';
        stockText = `Only 1 copy left!`;
      }

      const authorsStr = Array.isArray(book.authors) ? book.authors.join(', ') : (book.authors || 'Unknown');

      return `
        <div class="card book-card" onclick="Books.openBookModal('${book.bookId}')" style="cursor: pointer;">
          <div class="book-badge-category">${book.category || 'General'}</div>
          <h3 class="book-title">${book.title}</h3>
          <div class="book-authors">By ${authorsStr}</div>
          <p class="book-description">${book.description || 'No description available for this volume.'}</p>
          <div class="book-footer">
            <div class="stock-indicator">
              <span class="stock-dot ${dotClass}"></span>
              <span>${stockText}</span>
            </div>
            <button class="btn btn-outline btn-sm" onclick="event.stopPropagation(); Books.openBookModal('${book.bookId}')">
              View Details →
            </button>
          </div>
        </div>
      `;
    }).join('');
  },

  renderPagination() {
    const container = document.getElementById('books-pagination');
    if (!container) return;

    if (this.totalPages <= 1) {
      container.innerHTML = '';
      return;
    }

    let html = `
      <div style="display: flex; align-items: center; justify-content: center; gap: 12px; margin-top: 32px;">
        <button class="btn btn-outline btn-sm" ${this.currentPage === 0 ? 'disabled' : ''} onclick="Books.changePage(${this.currentPage - 1})">
          ← Previous
        </button>
        <span style="font-size: 0.85rem; color: var(--text-muted); font-weight: 600;">
          Page ${this.currentPage + 1} of ${this.totalPages}
        </span>
        <button class="btn btn-outline btn-sm" ${this.currentPage >= this.totalPages - 1 ? 'disabled' : ''} onclick="Books.changePage(${this.currentPage + 1})">
          Next →
        </button>
      </div>
    `;
    container.innerHTML = html;
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
            <span style="color: var(--primary-400);">🔍</span>
            <span style="flex: 1;">${s}</span>
            <span class="suggest-badge">Trie</span>
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

  async openBookModal(bookId) {
    try {
      const res = await API.get(`/api/books/${bookId}`);
      if (!res || !res.success || !res.data) return;
      const b = res.data;

      const modal = document.getElementById('book-detail-modal');
      const content = document.getElementById('book-detail-modal-content');
      
      const authorsStr = Array.isArray(b.authors) ? b.authors.join(', ') : b.authors;
      const isAvailable = b.availableCopies > 0;
      const isLoggedIn = Auth.isLoggedIn();

      let actionsHtml = '';
      if (!isLoggedIn) {
        actionsHtml = `
          <div style="background: rgba(245, 158, 11, 0.1); border: 1px solid rgba(245, 158, 11, 0.3); padding: 14px; border-radius: var(--radius-md); text-align: center; margin-top: 20px;">
            <p style="font-size: 0.85rem; color: #FBBF24; margin-bottom: 8px;">Please log in to borrow or reserve this book.</p>
            <button class="btn btn-primary btn-sm" onclick="App.closeAllModals(); Router.navigate('login');">Log In / Register</button>
          </div>
        `;
      } else {
        actionsHtml = `
          <div style="display: flex; gap: 12px; margin-top: 24px; flex-wrap: wrap;">
            ${isAvailable 
              ? `<button class="btn btn-primary" onclick="Books.borrowBook('${b.bookId}')">
                  📖 Borrow Book (${b.availableCopies} available)
                </button>`
              : `<button class="btn btn-warning" style="background: #F59E0B; color: #000;" onclick="Books.reserveBook('${b.bookId}')">
                  ⏳ Reserve Book (Place in Queue)
                </button>`
            }
            <button class="btn btn-outline" onclick="Books.shareBook('${b.bookId}', '${b.title}')">
              🔗 Share Short Link
            </button>
          </div>
        `;
      }

      content.innerHTML = `
        <div style="margin-bottom: 16px;">
          <span class="user-role-badge member" style="margin-bottom: 8px;">${b.category || 'General'}</span>
          <h2 style="font-size: 1.4rem; font-weight: 700; margin-top: 6px; line-height: 1.3;">${b.title}</h2>
          <p style="font-size: 0.9rem; color: var(--text-muted); margin-top: 4px;">By <strong>${authorsStr}</strong></p>
        </div>

        <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; background: var(--bg-card-subtle); border: 1px solid var(--border-color); padding: 16px; border-radius: var(--radius-md); margin-bottom: 20px; font-size: 0.85rem;">
          <div><span style="color: var(--text-dim);">Book ID:</span> <strong>${b.bookId}</strong></div>
          <div><span style="color: var(--text-dim);">ISBN:</span> <strong>${b.isbn}</strong></div>
          <div><span style="color: var(--text-dim);">Location / Shelf:</span> <strong>${b.location || 'Main Section'}</strong></div>
          <div><span style="color: var(--text-dim);">Available Stock:</span> <strong style="color: ${isAvailable ? 'var(--success)' : 'var(--danger)'};">${b.availableCopies} / ${b.totalCopies}</strong></div>
        </div>

        <div style="margin-bottom: 20px;">
          <h4 style="font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-dim); margin-bottom: 6px;">Description</h4>
          <p style="font-size: 0.88rem; line-height: 1.6; color: var(--text-muted);">${b.description || 'No detailed description available.'}</p>
        </div>

        <div id="book-share-result" style="display: none; margin-top: 14px;"></div>

        ${actionsHtml}
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
        API.toast(`Book borrowed successfully! Due: ${new Date(res.data.dueAt).toLocaleDateString()}`, 'success');
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
        API.toast(`Reservation placed! Queue position: #${res.data.queuePosition}`, 'success');
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
      // Integration call with URL Shortener service
      const targetPath = `/#books?id=${bookId}`;
      const res = await API.post('/api/short-links', { targetPath, title });
      const shareResult = document.getElementById('book-share-result');
      if (shareResult && res && res.success) {
        const fullShortUrl = window.location.origin + res.data.shortUrl;
        shareResult.style.display = 'block';
        shareResult.innerHTML = `
          <div style="background: rgba(6, 182, 212, 0.1); border: 1px solid rgba(6, 182, 212, 0.3); padding: 12px; border-radius: var(--radius-md); font-size: 0.82rem;">
            <div style="font-weight: 600; color: var(--primary-600); margin-bottom: 4px;">🔗 Shareable Short Link:</div>
            <div style="display: flex; gap: 8px; align-items: center;">
              <input type="text" class="form-control" readonly value="${fullShortUrl}" style="font-family: monospace; font-size: 0.8rem; padding: 6px 10px;" id="share-link-input" />
              <button class="btn btn-outline btn-sm" onclick="navigator.clipboard.writeText('${fullShortUrl}'); API.toast('Copied short link to clipboard!', 'success');">Copy</button>
            </div>
          </div>
        `;
      }
    } catch (err) {
      API.toast('Failed to generate short link: ' + err.message, 'error');
    }
  }
};
