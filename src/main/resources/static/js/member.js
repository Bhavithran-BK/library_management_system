/**
 * Member Dashboard: Borrowed Books, Due Dates, History, Reservations, Fines.
 */
const MemberDashboard = {
  async loadAll() {
    if (!Auth.isLoggedIn()) {
      Router.navigate('login');
      return;
    }
    const memberId = Auth.getMemberId();
    await Promise.all([
      this.loadLoans(memberId),
      this.loadReservations(memberId),
      this.loadFines(memberId)
    ]);
  },

  async loadLoans(memberId) {
    const activeList = document.getElementById('member-active-loans-list');
    const historyList = document.getElementById('member-loan-history-list');
    if (!activeList || !historyList) return;

    try {
      const res = await API.get(`/api/loans?memberId=${memberId}`);
      if (res && res.success && res.data) {
        const loans = res.data;
        const active = loans.filter(l => l.status === 'ACTIVE' || l.status === 'OVERDUE');
        const history = loans.filter(l => l.status === 'RETURNED');

        // Render Active Loans
        if (active.length === 0) {
          activeList.innerHTML = `<div style="text-align: center; padding: 24px; color: var(--text-dim);">No active book loans. Explore the catalogue to borrow books!</div>`;
        } else {
          activeList.innerHTML = `
            <div class="table-responsive">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>Loan ID</th>
                    <th>Book ID</th>
                    <th>Borrowed Date</th>
                    <th>Due Date</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  ${active.map(l => {
                    const isOverdue = new Date() > new Date(l.dueAt);
                    const statusClass = isOverdue ? 'overdue' : 'active';
                    const statusLabel = isOverdue ? 'OVERDUE' : 'ACTIVE';
                    return `
                      <tr>
                        <td><code>${l.loanId}</code></td>
                        <td><strong>${l.bookId}</strong></td>
                        <td>${new Date(l.borrowedAt).toLocaleDateString()}</td>
                        <td style="${isOverdue ? 'color: var(--danger); font-weight: 600;' : ''}">
                          ${new Date(l.dueAt).toLocaleDateString()}
                        </td>
                        <td><span class="status-pill ${statusClass}">${statusLabel}</span></td>
                        <td>
                          <button class="btn btn-outline btn-sm" onclick="MemberDashboard.returnBook('${l.loanId}')">
                            Return Book
                          </button>
                        </td>
                      </tr>
                    `;
                  }).join('')}
                </tbody>
              </table>
            </div>
          `;
        }

        // Render Loan History
        if (history.length === 0) {
          historyList.innerHTML = `<div style="text-align: center; padding: 24px; color: var(--text-dim);">No completed returns yet.</div>`;
        } else {
          historyList.innerHTML = `
            <div class="table-responsive">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>Loan ID</th>
                    <th>Book ID</th>
                    <th>Borrowed Date</th>
                    <th>Returned Date</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  ${history.map(l => `
                    <tr>
                      <td><code>${l.loanId}</code></td>
                      <td>${l.bookId}</td>
                      <td>${new Date(l.borrowedAt).toLocaleDateString()}</td>
                      <td>${new Date(l.returnedAt).toLocaleDateString()}</td>
                      <td><span class="status-pill returned">RETURNED</span></td>
                    </tr>
                  `).join('')}
                </tbody>
              </table>
            </div>
          `;
        }
      }
    } catch (err) {
      activeList.innerHTML = `<div style="color: var(--danger); padding: 12px;">Error: ${err.message}</div>`;
    }
  },

  async returnBook(loanId) {
    if (!confirm(`Confirm return for Loan ID: ${loanId}?`)) return;

    try {
      const res = await API.post(`/api/loans/${loanId}/return`, {});
      if (res && res.success) {
        API.toast('Book returned successfully! Inventory replenished.', 'success');
        this.loadAll();
        App.loadDashboardData();
        Books.loadCatalogue();
      }
    } catch (err) {
      API.toast(err.message, 'error');
    }
  },

  async loadReservations(memberId) {
    const list = document.getElementById('member-reservations-list');
    if (!list) return;

    try {
      const res = await API.get(`/api/members/${memberId}/reservations`);
      if (res && res.success && res.data) {
        const reservations = res.data;
        if (reservations.length === 0) {
          list.innerHTML = `<div style="text-align: center; padding: 24px; color: var(--text-dim);">No active reservations.</div>`;
          return;
        }

        list.innerHTML = `
          <div class="table-responsive">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Reservation ID</th>
                  <th>Book ID</th>
                  <th>Reserved On</th>
                  <th>Queue Position</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                ${reservations.map(r => {
                  const isPending = r.status === 'PENDING';
                  return `
                    <tr>
                      <td><code>${r.reservationId}</code></td>
                      <td><strong>${r.bookId}</strong></td>
                      <td>${new Date(r.createdAt).toLocaleDateString()}</td>
                      <td>
                        <strong style="color: var(--accent-cyan);">${isPending ? '#' + r.queuePosition + ' in queue' : '-'}</strong>
                      </td>
                      <td><span class="status-pill ${r.status.toLowerCase()}">${r.status}</span></td>
                      <td>
                        ${isPending ? `
                          <button class="btn btn-danger btn-sm" onclick="MemberDashboard.cancelReservation('${r.reservationId}')">
                            Cancel
                          </button>
                        ` : `<span style="color: var(--text-dim);">-</span>`}
                      </td>
                    </tr>
                  `;
                }).join('')}
              </tbody>
            </table>
          </div>
        `;
      }
    } catch (err) {
      list.innerHTML = `<div style="color: var(--danger); padding: 12px;">Error: ${err.message}</div>`;
    }
  },

  async cancelReservation(resId) {
    if (!confirm('Are you sure you want to cancel this reservation?')) return;

    try {
      const res = await API.delete(`/api/reservations/${resId}`);
      if (res && res.success) {
        API.toast('Reservation cancelled successfully', 'info');
        this.loadReservations(Auth.getMemberId());
        App.loadDashboardData();
      }
    } catch (err) {
      API.toast(err.message, 'error');
    }
  },

  async loadFines(memberId) {
    const list = document.getElementById('member-fines-list');
    if (!list) return;

    try {
      const res = await API.get(`/api/members/${memberId}/fines`);
      if (res && res.success && res.data) {
        const fines = res.data;
        if (fines.length === 0) {
          list.innerHTML = `<div style="text-align: center; padding: 24px; color: var(--text-dim);">No fine transactions on record. Your account is in good standing.</div>`;
          return;
        }

        list.innerHTML = `
          <div class="table-responsive">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Transaction ID</th>
                  <th>Loan ID</th>
                  <th>Amount</th>
                  <th>Created Date</th>
                  <th>Status</th>
                  <th>Payment Type</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                ${fines.map(f => {
                  const isPending = f.status === 'PENDING';
                  return `
                    <tr>
                      <td><code>${f.transactionId}</code></td>
                      <td>${f.loanId}</td>
                      <td><strong style="color: ${isPending ? 'var(--danger)' : 'var(--success)'}; font-size: 1rem;">$${parseFloat(f.amount).toFixed(2)}</strong></td>
                      <td>${new Date(f.createdAt).toLocaleDateString()}</td>
                      <td><span class="status-pill ${f.status.toLowerCase()}">${f.status}</span></td>
                      <td><span style="font-size: 0.75rem; color: var(--text-dim);">SIMULATED</span></td>
                      <td>
                        ${isPending ? `
                          <button class="btn btn-primary btn-sm" onclick="MemberDashboard.openPayFineModal('${f.transactionId}', '${f.amount}')">
                            Pay Fine
                          </button>
                        ` : `
                          <span style="font-size: 0.78rem; color: var(--text-muted);">Ref: ${f.paymentReference || 'PAID'}</span>
                        `}
                      </td>
                    </tr>
                  `;
                }).join('')}
              </tbody>
            </table>
          </div>
        `;
      }
    } catch (err) {
      list.innerHTML = `<div style="color: var(--danger); padding: 12px;">Error: ${err.message}</div>`;
    }
  },

  openPayFineModal(txnId, amount) {
    const modal = document.getElementById('pay-fine-modal');
    const content = document.getElementById('pay-fine-modal-content');
    if (!modal || !content) return;

    content.innerHTML = `
      <div style="text-align: center; margin-bottom: 20px;">
        <div style="margin-bottom: 8px; color: var(--accent);">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="1" y="4" width="22" height="16" rx="2" ry="2"></rect><line x1="1" y1="10" x2="23" y2="10"></line></svg>
        </div>
        <h3 style="font-size: 1.15rem; font-weight: 600;">Simulated Fine Settlement</h3>
        <p style="font-size: 0.85rem; color: var(--text-muted); margin-top: 4px;">
          Transaction ID: <code>${txnId}</code>
        </p>
      </div>

      <div style="background: rgba(16, 185, 129, 0.1); border: 1px solid rgba(16, 185, 129, 0.3); padding: 16px; border-radius: var(--radius-md); text-align: center; margin-bottom: 20px;">
        <span style="font-size: 0.85rem; color: var(--text-dim);">Total Amount Due</span>
        <div style="font-size: 1.8rem; font-weight: 800; color: var(--success);">$${parseFloat(amount).toFixed(2)}</div>
        <div style="font-size: 0.75rem; color: var(--accent-cyan); margin-top: 4px;">Notice: This is a safe simulation. No real money or card is charged.</div>
      </div>

      <form id="pay-fine-form" onsubmit="event.preventDefault(); MemberDashboard.processPayment('${txnId}', ${amount});">
        <div class="form-group">
          <label>Simulated Payment Method</label>
          <select class="form-control" id="pay-method">
            <option value="SIMULATED_DEBIT">Simulated Debit Card</option>
            <option value="SIMULATED_UPI">Simulated UPI / Net Banking</option>
            <option value="LIBRARY_CREDIT">Library Credits Balance</option>
          </select>
        </div>

        <div class="form-group">
          <label>Card / Account Identifier (Any test digits)</label>
          <input type="text" class="form-control" value="4111 •••• •••• 9921" readonly style="font-family: monospace;" />
        </div>

        <button type="submit" class="btn btn-success" style="width: 100%; padding: 12px; font-size: 0.95rem;">
          Confirm Simulated Payment ($${parseFloat(amount).toFixed(2)})
        </button>
      </form>
    `;

    modal.classList.add('active');
  },

  async processPayment(txnId, amount) {
    try {
      const res = await API.post(`/api/fines/${txnId}/pay`, {
        amount: amount,
        paymentMethod: document.getElementById('pay-method').value,
        idempotencyKey: 'IDEMP-' + Date.now()
      });

      if (res && res.success) {
        API.toast(`Payment successful! Receipt ref: ${res.data.paymentReference}`, 'success');
        App.closeAllModals();
        this.loadFines(Auth.getMemberId());
        App.loadDashboardData();
      }
    } catch (err) {
      API.toast(err.message, 'error');
    }
  }
};
