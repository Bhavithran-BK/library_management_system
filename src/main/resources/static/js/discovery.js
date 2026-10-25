/**
 * Book Discovery, In-Memory Trie Autocomplete, Redis Cache-Aside & Crawler Controller
 */
const Discovery = {
  lastExtractedBooks: [],

  init() {
    this.loadCacheStats();
    this.setupListeners();
    const seedInput = document.getElementById('crawler-seed-url');
    if (seedInput && (!seedInput.value || seedInput.value.includes(':8080'))) {
      seedInput.value = `${window.location.origin}/api/crawler/simulated-source?page=1`;
    }
  },

  setupListeners() {
    const trieInput = document.getElementById('discovery-trie-input');
    if (trieInput && !trieInput.dataset.bound) {
      trieInput.dataset.bound = 'true';
      let debounceTimer = null;
      trieInput.addEventListener('input', (e) => {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(() => {
          this.testSuggest(e.target.value);
        }, 120);
      });
    }
  },

  async testSuggest(prefix) {
    const container = document.getElementById('discovery-trie-results');
    const latencyEl = document.getElementById('discovery-trie-latency');
    if (!container) return;

    if (!prefix || prefix.trim().length === 0) {
      container.innerHTML = '<span style="color: var(--text-dim); font-size: 0.85rem;">Type a prefix above to test Trie prefix matching...</span>';
      if (latencyEl) latencyEl.innerText = '';
      return;
    }

    const start = performance.now();
    try {
      const res = await API.get(`/api/search/suggest?prefix=${encodeURIComponent(prefix.trim())}&limit=10`);
      const latency = Math.round(performance.now() - start);
      if (latencyEl) {
        latencyEl.innerHTML = `⚡ <span style="color: var(--success); font-weight: 700;">${latency} ms</span> response latency`;
      }

      if (res && res.success && res.data && res.data.suggestions && res.data.suggestions.length > 0) {
        container.innerHTML = res.data.suggestions.map(item => `
          <div class="chip" style="cursor: pointer; background: rgba(99, 102, 241, 0.15); border: 1px solid rgba(99, 102, 241, 0.3);"
               onclick="Discovery.selectSuggestion('${item.replace(/'/g, "\\'")}')">
            <span>🔍 ${item}</span>
          </div>
        `).join('');
      } else {
        container.innerHTML = '<span style="color: var(--text-dim); font-size: 0.85rem;">No Trie suggestions matching this prefix.</span>';
      }
    } catch (err) {
      container.innerHTML = `<span style="color: var(--danger); font-size: 0.85rem;">Error: ${err.message}</span>`;
    }
  },

  selectSuggestion(term) {
    const searchInput = document.getElementById('discovery-search-q');
    if (searchInput) {
      searchInput.value = term;
      this.runSearch();
    }
  },

  async runSearch() {
    const qInput = document.getElementById('discovery-search-q');
    const availSelect = document.getElementById('discovery-search-avail');
    const resultsContainer = document.getElementById('discovery-search-results');
    const badgeEl = document.getElementById('discovery-cache-badge');
    const latencyEl = document.getElementById('discovery-search-latency');

    if (!resultsContainer) return;

    const q = qInput ? qInput.value.trim() : '';
    const avail = availSelect ? availSelect.value : '';

    resultsContainer.innerHTML = '<div style="text-align: center; padding: 20px; color: var(--text-muted);">Executing search...</div>';

    let url = `/api/search?q=${encodeURIComponent(q)}&page=0&size=10`;
    if (avail === 'true' || avail === 'false') {
      url += `&availability=${avail}`;
    }

    const start = performance.now();
    try {
      // Record hits before search
      const prevStats = await API.get('/api/search/cache/stats');
      const prevHits = (prevStats && prevStats.data) ? prevStats.data.searchCacheHits : 0;

      const res = await API.get(url);
      const latency = Math.round(performance.now() - start);

      // Fetch stats after to determine HIT vs MISS
      const newStats = await API.get('/api/search/cache/stats');
      const newHits = (newStats && newStats.data) ? newStats.data.searchCacheHits : 0;
      const wasHit = newHits > prevHits;

      if (badgeEl) {
        if (wasHit) {
          badgeEl.className = 'badge-concept';
          badgeEl.style.background = 'rgba(16, 185, 129, 0.2)';
          badgeEl.style.borderColor = 'rgba(16, 185, 129, 0.4)';
          badgeEl.style.color = '#10B981';
          badgeEl.innerHTML = '⚡ FAST CACHE HIT';
        } else {
          badgeEl.className = 'badge-concept';
          badgeEl.style.background = 'rgba(245, 158, 11, 0.2)';
          badgeEl.style.borderColor = 'rgba(245, 158, 11, 0.4)';
          badgeEl.style.color = '#FBBF24';
          badgeEl.innerHTML = '📦 DATABASE RETRIEVAL';
        }
      }

      if (latencyEl) {
        latencyEl.innerText = `${latency} ms`;
      }

      this.renderCacheStats(newStats ? newStats.data : null);

      if (res && res.success && res.data) {
        const data = res.data;
        const books = data.content || [];
        if (books.length === 0) {
          resultsContainer.innerHTML = '<div style="text-align: center; padding: 30px; color: var(--text-muted);">No books matched the search criteria.</div>';
          return;
        }

        resultsContainer.innerHTML = `
          <div style="margin-bottom: 12px; font-size: 0.85rem; color: var(--text-muted);">
            Found <strong>${data.totalElements}</strong> matching book(s) across titles, authors, categories, and ISBNs (Page ${data.page + 1} of ${data.totalPages || 1}):
          </div>
          <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px;">
            ${books.map(b => `
              <div class="card" style="padding: 14px; border: 1px solid var(--border-color);">
                <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 6px;">
                  <span class="user-role-badge member" style="font-size: 0.75rem;">${b.category || 'General'}</span>
                  <span style="font-size: 0.78rem; font-weight: 700; color: ${b.availableCopies > 0 ? 'var(--success)' : 'var(--danger)'};">
                    ${b.availableCopies} / ${b.totalCopies} Avail
                  </span>
                </div>
                <h4 style="font-size: 0.95rem; font-weight: 700; margin-bottom: 4px;">${b.title}</h4>
                <div style="font-size: 0.8rem; color: var(--text-muted); margin-bottom: 6px;">
                  By ${(b.authors || []).join(', ')}
                </div>
                <div style="font-size: 0.75rem; color: var(--text-dim); font-family: monospace;">
                  ISBN: ${b.isbn} | ID: ${b.bookId}
                </div>
              </div>
            `).join('')}
          </div>
        `;
      }
    } catch (err) {
      resultsContainer.innerHTML = `<div style="color: var(--danger); padding: 20px;">Search failed: ${err.message}</div>`;
    }
  },

  async loadCacheStats() {
    try {
      const res = await API.get('/api/search/cache/stats');
      if (res && res.success && res.data) {
        this.renderCacheStats(res.data);
      }
    } catch (err) {
      console.warn('Failed to load Redis cache stats:', err);
    }
  },

  renderCacheStats(data) {
    if (!data) return;
    const elBHits = document.getElementById('stat-book-hits');
    const elBMisses = document.getElementById('stat-book-misses');
    const elSHits = document.getElementById('stat-search-hits');
    const elSMisses = document.getElementById('stat-search-misses');
    const elRatio = document.getElementById('stat-cache-ratio');
    const elStatus = document.getElementById('stat-redis-status');

    if (elBHits) elBHits.innerText = data.bookCacheHits;
    if (elBMisses) elBMisses.innerText = data.bookCacheMisses;
    if (elSHits) elSHits.innerText = data.searchCacheHits;
    if (elSMisses) elSMisses.innerText = data.searchCacheMisses;
    if (elRatio) elRatio.innerText = data.hitRatio;
    if (elStatus) {
      elStatus.innerHTML = data.connected
        ? '<span style="color: var(--success); font-weight: 700;">🟢 CONNECTED</span>'
        : '<span style="color: var(--danger); font-weight: 700;">🔴 DISCONNECTED</span>';
    }
  },

  async runCrawler() {
    const seedUrlInput = document.getElementById('crawler-seed-url');
    const statusContainer = document.getElementById('crawler-stats-container');
    const resultsContainer = document.getElementById('crawler-extracted-books');

    if (!seedUrlInput || !statusContainer || !resultsContainer) return;

    const seedUrl = seedUrlInput.value.trim();
    if (!seedUrl) {
      API.toast('Please enter a valid seed URL', 'error');
      return;
    }

    statusContainer.style.display = 'block';
    statusContainer.innerHTML = `
      <div style="text-align: center; padding: 20px; color: var(--text-muted);">
        <div class="spinner" style="font-size: 24px; margin-bottom: 8px;">⏳</div>
        Crawling pages safely and parsing HTML book records...
      </div>
    `;
    resultsContainer.innerHTML = '';

    try {
      const res = await API.post(`/api/crawler/run?seedUrl=${encodeURIComponent(seedUrl)}`);
      if (res && res.success && res.data) {
        const d = res.data;
        this.lastExtractedBooks = d.extractedBooks || [];

        statusContainer.innerHTML = `
          <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; background: var(--bg-card-subtle); border: 1px solid var(--border-color); padding: 16px; border-radius: var(--radius-md); font-size: 0.85rem;">
            <div><span style="color: var(--text-dim);">Pages Visited:</span> <strong style="color: var(--primary-600); font-size: 1.1rem;">${d.pagesVisited}</strong></div>
            <div><span style="color: var(--text-dim);">Records Extracted:</span> <strong style="color: var(--success); font-size: 1.1rem;">${d.validRecordsExtracted}</strong></div>
            <div><span style="color: var(--text-dim);">Duplicate URLs Skipped:</span> <strong>${d.duplicateUrlsSkipped}</strong></div>
            <div><span style="color: var(--text-dim);">Invalid Records Skipped:</span> <strong>${d.invalidRecordsSkipped}</strong></div>
            <div><span style="color: var(--text-dim);">Crawl Status:</span> <strong style="color: var(--success);">${d.status}</strong></div>
          </div>
        `;

        if (this.lastExtractedBooks.length === 0) {
          resultsContainer.innerHTML = '<div style="text-align: center; padding: 30px; color: var(--text-muted);">No valid book records were extracted from this crawl.</div>';
          return;
        }

        resultsContainer.innerHTML = `
          <div style="display: flex; justify-content: space-between; align-items: center; margin: 20px 0 12px 0;">
            <h4 style="font-size: 1rem; font-weight: 700;">Extracted Records For Catalogue Review (${this.lastExtractedBooks.length})</h4>
            <button class="btn btn-primary btn-sm" onclick="Discovery.importAllExtracted()">
              📥 Import All to Library Catalogue
            </button>
          </div>
          <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px;">
            ${this.lastExtractedBooks.map((b, idx) => `
              <div class="card" style="padding: 16px; border: 1px solid var(--border-color);">
                <div style="display: flex; justify-content: space-between; margin-bottom: 6px;">
                  <span class="user-role-badge member" style="font-size: 0.75rem;">${b.category || 'General'}</span>
                  ${b.rating ? `<span style="font-size: 0.8rem; color: #FBBF24;">⭐ ${b.rating} / 5</span>` : ''}
                </div>
                <h4 style="font-size: 0.95rem; font-weight: 700; margin-bottom: 4px;">${b.title}</h4>
                <div style="font-size: 0.8rem; color: var(--text-muted); margin-bottom: 6px;">
                  By ${(b.authors || []).join(', ')}
                </div>
                <div style="font-size: 0.75rem; color: var(--text-dim); margin-bottom: 8px;">
                  ISBN: <strong>${b.isbn}</strong>
                </div>
                <p style="font-size: 0.8rem; color: var(--text-muted); line-height: 1.4; margin-bottom: 12px;">
                  ${b.description || 'Imported via web crawler'}
                </p>
                <button class="btn btn-outline btn-sm" style="width: 100%;" onclick="Discovery.importSingleBook(${idx})">
                  Import Single Volume →
                </button>
              </div>
            `).join('')}
          </div>
        `;
      }
    } catch (err) {
      statusContainer.innerHTML = `<div style="color: var(--danger); padding: 14px;">Crawler error: ${err.message}</div>`;
    }
  },

  setSeedUrl(url) {
    const input = document.getElementById('crawler-seed-url');
    if (input) {
      input.value = url;
    }
  },

  async importAllExtracted() {
    if (!this.lastExtractedBooks || this.lastExtractedBooks.length === 0) {
      API.toast('No books to import', 'error');
      return;
    }

    try {
      const payload = {
        books: this.lastExtractedBooks,
        defaultCopies: 3,
        location: "Main Section / Crawler Import"
      };
      const res = await API.post('/api/crawler/import', payload);
      if (res && res.success) {
        API.toast(`Successfully imported ${res.data.importedCount} books (${res.data.skippedDuplicateCount} duplicates skipped)!`, 'success');
        this.loadCacheStats();
        App.loadDashboardData();
        Books.loadCatalogue();
      }
    } catch (err) {
      API.toast('Import failed: ' + err.message, 'error');
    }
  },

  async importSingleBook(idx) {
    const book = this.lastExtractedBooks[idx];
    if (!book) return;

    try {
      const payload = {
        books: [book],
        defaultCopies: 3,
        location: "Main Section / Crawler Import"
      };
      const res = await API.post('/api/crawler/import', payload);
      if (res && res.success) {
        if (res.data.importedCount > 0) {
          API.toast(`Book '${book.title}' imported successfully!`, 'success');
        } else {
          API.toast(`Book '${book.title}' already exists (duplicate ISBN skipped).`, 'warning');
        }
        this.loadCacheStats();
        App.loadDashboardData();
        Books.loadCatalogue();
      }
    } catch (err) {
      API.toast('Import failed: ' + err.message, 'error');
    }
  }
};
