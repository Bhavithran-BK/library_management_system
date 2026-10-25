/**
 * Editorial Book Cover Art and URL Resolution.
 * Matches ISBN, title, and author to real book jackets with elegant typography fallbacks.
 */
const BookCovers = {
  // Known canonical classics with verified ISBNs
  CANONICAL_ISBNS: {
    'clean code': '9780132350884',
    'the pragmatic programmer': '9780135957059',
    'pragmatic programmer': '9780135957059',
    'introduction to algorithms': '9780262033848',
    'artificial intelligence: a modern approach': '9780134610993',
    'database system concepts': '9780073523323',
    'operating system concepts': '9781119800361',
    'computer networking: a top-down approach': '9780133594140',
    'designing data-intensive applications': '9781449373320',
    "system design interview – an insider's guide": '9798664653403',
    'system design interview': '9798664653403',
    'database internals': '9781492040347',
    'site reliability engineering': '9781491929124',
    'distributed systems: principles and paradigms': '9780132392273',
    'distributed systems': '9780132392273',
    'grokking algorithms': '9781617292231',
    'release it!': '9781680502398',
    'clean architecture': '9780134494166',
    'designing distributed systems': '9781491983640'
  },

  escapeHtml(str) {
    return (str || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  },

  cleanIsbn(isbn) {
    return (isbn || '').replace(/[^\dXx]/g, '');
  },

  resolveIsbn(isbn, title) {
    const direct = this.cleanIsbn(isbn);
    if (direct && (direct.length === 10 || direct.length === 13)) {
      return direct;
    }
    if (title) {
      const normTitle = title.toLowerCase().trim();
      for (const [key, val] of Object.entries(this.CANONICAL_ISBNS)) {
        if (normTitle.includes(key)) {
          return val;
        }
      }
    }
    return direct || null;
  },

  coverUrl(isbn, title = '') {
    const resolved = this.resolveIsbn(isbn, title);
    if (!resolved) return null;
    return `https://covers.openlibrary.org/b/isbn/${resolved}-M.jpg?default=false`;
  },

  largeCoverUrl(isbn, title = '') {
    const resolved = this.resolveIsbn(isbn, title);
    if (!resolved) return null;
    return `https://covers.openlibrary.org/b/isbn/${resolved}-L.jpg?default=false`;
  },

  // Deterministic calm palette for book covers without cover artwork
  getPalette(title = '') {
    const palettes = [
      { bg: '#F2ECE4', border: '#DED6CD', accent: '#355B45' },
      { bg: '#EAEBE6', border: '#D4D8CF', accent: '#2F4F3D' },
      { bg: '#ECE8E1', border: '#D8D1C7', accent: '#4E5340' },
      { bg: '#E6EAE7', border: '#CFD9D2', accent: '#2E4C3B' },
      { bg: '#EBE9E4', border: '#D9D5CD', accent: '#3A4B40' }
    ];
    let hash = 0;
    for (let i = 0; i < title.length; i++) {
      hash = (hash << 5) - hash + title.charCodeAt(i);
      hash |= 0;
    }
    const idx = Math.abs(hash) % palettes.length;
    return palettes[idx];
  },

  fallbackHtml(title, authors, extraClass = '') {
    const safeTitle = this.escapeHtml(title || 'Untitled Book');
    const safeAuthors = this.escapeHtml(authors || 'Unknown Author');
    const pal = this.getPalette(title);

    return `
      <div class="book-cover-fallback ${extraClass}" style="background-color: ${pal.bg}; border: 1px solid ${pal.border};">
        <div class="fallback-frame" style="border-color: ${pal.border};">
          <div class="fallback-top-mark">
            <svg viewBox="0 0 24 24" class="fallback-icon" style="color: ${pal.accent};">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
            </svg>
          </div>
          <div class="fallback-body">
            <div class="fallback-title">${safeTitle}</div>
            <div class="fallback-divider" style="background-color: ${pal.accent};"></div>
            <div class="fallback-author">${safeAuthors}</div>
          </div>
          <div class="fallback-footer">
            <span class="fallback-imprint">ACADEMIC PRESS</span>
          </div>
        </div>
      </div>
    `;
  },

  onImgError(img) {
    const title = img.dataset.title || 'Untitled';
    const authors = img.dataset.authors || '';
    const parent = img.parentElement;
    if (parent) {
      const isThumb = parent.classList.contains('recent-book-thumb') || parent.classList.contains('thumb');
      parent.innerHTML = this.fallbackHtml(title, authors, isThumb ? 'compact' : '');
    }
  },

  coverMarkup(isbn, title, authors, size = 'M') {
    const url = size === 'L' ? this.largeCoverUrl(isbn, title) : this.coverUrl(isbn, title);
    const safeTitle = this.escapeHtml(title);
    const safeAuthors = this.escapeHtml(authors);

    if (!url) {
      return this.fallbackHtml(title, authors);
    }

    return `
      <img src="${url}"
           alt="Cover of ${safeTitle}"
           loading="lazy"
           decoding="async"
           data-title="${safeTitle}"
           data-authors="${safeAuthors}"
           onerror="BookCovers.onImgError(this)" />
    `;
  },

  imgTag(url, title, authors, context = 'grid') {
    const safeTitle = this.escapeHtml(title);
    const safeAuthors = this.escapeHtml(authors);

    if (!url) {
      return this.fallbackHtml(title, authors, context === 'recent' ? 'compact' : '');
    }

    return `
      <img src="${url}"
           alt="${safeTitle}"
           loading="lazy"
           decoding="async"
           data-title="${safeTitle}"
           data-authors="${safeAuthors}"
           onerror="BookCovers.onImgError(this)" />
    `;
  }
};
