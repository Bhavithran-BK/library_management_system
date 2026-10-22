/**
 * Admin Dashboard: Book CRUD, Member Management, All Loans & Reservations.
 */
const Admin = {
  currentTab: 'books',

  switchTab(tab) {
    this.currentTab = tab;
    document.querySelectorAll('.admin-tab-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.tab === tab);
    });
    document.querySelectorAll('.admin-tab-pane').forEach(pane => {
      pane.style.display = pane.id === `admin-pane-${tab}` ? 'block' : 'none';
    });

    if (tab === 'books') this.loadBooksTable();
    if (tab === 'loans') this.loadAllLoans();
    if (tab === 'reservations') this.loadAllReservations();
    if (tab === 'members') this.loadAllMembers();
  },

  async loadBooksTable() {
    const container = document.getElementById('admin-books-table-body');
    if (!container) return;

    try {
      const res = await API.get('/api/books?page=0&size=50');
      if (res && res.success && res.data) {
        const books = res.data.content;
        if (books.length === 0) {
          container.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-dim); padding: 24px;">No books in inventory.</td></tr>`;
          return;
        }

        container.innerHTML = books.map(b => {
          const authors = Array.isArray(b.authors) ? b.authors.join(', ') : b.authors;
          return `
            <tr>
              <td><code>${b.bookId}</code></td>
              <td><strong>${b.title}</strong></td>
              <td>${authors}</td>
              <td><code>${b.isbn}</code></td>
              <td><span class="user-role-badge member">${b.category}</span></td>
              <td><strong>${b.availableCopies}</strong> / ${b.totalCopies}</td>
              <td>
                <div style="display: flex; gap: 8px;">
                  <button class="btn btn-outline btn-sm" onclick="Admin.openEditBookModal('${b.bookId}')">✏️ Edit</button>
                  <button class="btn btn-danger btn-sm" onclick="Admin.deleteBook('${b.bookId}', '${b.title.replace(/'/g, "\\'")}')">🗑️ Delete</button>
                </div>
              </td>
            </tr>
          `;
        }).join('');
      }
    } catch (err) {
      container.innerHTML = `<tr><td colspan="7" style="color: var(--danger);">${err.message}</td></tr>`;
    }
  },

  openAddBookModal() {
    const modal = document.getElementById('book-form-modal');
    document.getElementById('book-form-title').innerText = 'Add New Book to Catalogue';
    document.getElementById('book-form-id').value = '';
    document.getElementById('book-form').reset();
    document.getElementById('book-form-available').parentElement.style.display = 'none'; // Auto defaults to total
    modal.classList.add('active');
  },

  async openEditBookModal(bookId) {
    try {
      const res = await API.get(`/api/books/${bookId}`);
      if (!res || !res.success || !res.data) return;
      const b = res.data;

      const modal = document.getElementById('book-form-modal');
      document.getElementById('book-form-title').innerText = `Edit Book (${b.bookId})`;
      document.getElementById('book-form-id').value = b.bookId;
      document.getElementById('book-form-title-input').value = b.title;
      document.getElementById('book-form-authors').value = Array.isArray(b.authors) ? b.authors.join(', ') : b.authors;
      document.getElementById('book-form-isbn').value = b.isbn;
      document.getElementById('book-form-category').value = b.category;
      document.getElementById('book-form-total').value = b.totalCopies;
      
      const availWrap = document.getElementById('book-form-available').parentElement;
      availWrap.style.display = 'block';
      document.getElementById('book-form-available').value = b.availableCopies;

      document.getElementById('book-form-location').value = b.location || '';
      document.getElementById('book-form-desc').value = b.description || '';

      modal.classList.add('active');
    } catch (err) {
      API.toast(err.message, 'error');
    }
  },

  async handleSaveBook(e) {
    e.preventDefault();
    const bookId = document.getElementById('book-form-id').value;
    const authorsRaw = document.getElementById('book-form-authors').value;
    const authors = authorsRaw.split(',').map(a => a.trim()).filter(a => a.length > 0);

    const payload = {
      title: document.getElementById('book-form-title-input').value.trim(),
      authors: authors,
      isbn: document.getElementById('book-form-isbn').value.trim(),
      category: document.getElementById('book-form-category').value.trim(),
      totalCopies: parseInt(document.getElementById('book-form-total').value, 10),
      location: document.getElementById('book-form-location').value.trim(),
      description: document.getElementById('book-form-desc').value.trim(),
      metadataSource: 'ADMIN_PANEL'
    };

    if (bookId) {
      payload.availableCopies = parseInt(document.getElementById('book-form-available').value, 10);
    }

    try {
      let res;
      if (bookId) {
        res = await API.put(`/api/books/${bookId}`, payload);
      } else {
        res = await API.post('/api/books', payload);
      }

      if (res && res.success) {
        API.toast(bookId ? 'Book updated successfully!' : `Book created with ID: ${res.data.bookId}`, 'success');
        App.closeAllModals();
        this.loadBooksTable();
        Books.loadCatalogue();
        App.loadDashboardData();
      }
    } catch (err) {
      API.toast(err.message, 'error');
    }
  },

  async deleteBook(bookId, title) {
    if (!confirm(`Are you sure you want to delete book "${title}" (${bookId})?`)) return;

    try {
      const res = await API.delete(`/api/books/${bookId}`);
      if (res && res.success) {
        API.toast('Book deleted successfully', 'success');
        this.loadBooksTable();
        Books.loadCatalogue();
        App.loadDashboardData();
      }
    } catch (err) {
      API.toast(err.message, 'error');
    }
  },

  async loadAllLoans() {
    const container = document.getElementById('admin-loans-table-body');
    if (!container) return;

    try {
      const res = await API.get('/api/loans');
      if (res && res.success && res.data) {
        const loans = res.data;
        if (loans.length === 0) {
          container.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-dim); padding: 24px;">No loans recorded.</td></tr>`;
          return;
        }

        container.innerHTML = loans.map(l => {
          const isOverdue = l.status === 'ACTIVE' && new Date() > new Date(l.dueAt);
          const statusClass = isOverdue ? 'overdue' : l.status.toLowerCase();
          const statusText = isOverdue ? 'OVERDUE' : l.status;

          return `
            <tr>
              <td><code>${l.loanId}</code></td>
              <td><code>${l.memberId}</code></td>
              <td><strong>${l.bookId}</strong></td>
              <td>${new Date(l.borrowedAt).toLocaleDateString()}</td>
              <td style="${isOverdue ? 'color: var(--danger); font-weight: 700;' : ''}">
                ${new Date(l.dueAt).toLocaleDateString()}
              </td>
              <td><span class="status-pill ${statusClass}">${statusText}</span></td>
              <td>
                ${l.status === 'ACTIVE' || isOverdue ? `
                  <button class="btn btn-outline btn-sm" onclick="Admin.processReturn('${l.loanId}')">
                    Process Return
                  </button>
                ` : `<span style="color: var(--text-dim); font-size: 0.8rem;">Returned ${new Date(l.returnedAt).toLocaleDateString()}</span>`}
              </td>
            </tr>
          `;
        }).join('');
      }
    } catch (err) {
      container.innerHTML = `<tr><td colspan="7" style="color: var(--danger);">${err.message}</td></tr>`;
    }
  },

  async processReturn(loanId) {
    if (!confirm(`Process manual return for Loan ID: ${loanId}?`)) return;

    try {
      const res = await API.post(`/api/loans/${loanId}/return`, {});
      if (res && res.success) {
        API.toast('Loan returned and inventory updated', 'success');
        this.loadAllLoans();
        App.loadDashboardData();
        Books.loadCatalogue();
      }
    } catch (err) {
      API.toast(err.message, 'error');
    }
  },

  async loadAllReservations() {
    const container = document.getElementById('admin-reservations-table-body');
    if (!container) return;

    try {
      const res = await API.get('/api/reservations');
      if (res && res.success && res.data) {
        const reservations = res.data;
        if (reservations.length === 0) {
          container.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-dim); padding: 24px;">No reservations found.</td></tr>`;
          return;
        }

        container.innerHTML = reservations.map(r => `
          <tr>
            <td><code>${r.reservationId}</code></td>
            <td><code>${r.memberId}</code></td>
            <td><strong>${r.bookId}</strong></td>
            <td>${new Date(r.createdAt).toLocaleString()}</td>
            <td><strong>#${r.queuePosition}</strong></td>
            <td><span class="status-pill ${r.status.toLowerCase()}">${r.status}</span></td>
          </tr>
        `).join('');
      }
    } catch (err) {
      container.innerHTML = `<tr><td colspan="6" style="color: var(--danger);">${err.message}</td></tr>`;
    }
  },

  async loadAllMembers() {
    const container = document.getElementById('admin-members-table-body');
    if (!container) return;

    try {
      const res = await API.get('/api/admin/members');
      if (res && res.success && res.data) {
        const members = res.data;
        container.innerHTML = members.map(m => `
          <tr>
            <td><code>${m.memberId}</code></td>
            <td><strong>${m.name}</strong></td>
            <td>${m.email}</td>
            <td><span class="user-role-badge ${m.role.toLowerCase()}">${m.role}</span></td>
            <td>${m.phone || '-'}</td>
            <td>${new Date(m.createdAt).toLocaleDateString()}</td>
          </tr>
        `).join('');
      }
    } catch (err) {
      container.innerHTML = `<tr><td colspan="6" style="color: var(--danger);">${err.message}</td></tr>`;
    }
  }
};
