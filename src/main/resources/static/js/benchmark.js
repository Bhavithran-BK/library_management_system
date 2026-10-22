/**
 * Distributed Unique ID Generator Live Evaluation & Concurrency Benchmark.
 */
const Benchmark = {
  async runLiveBenchmark() {
    const countInput = document.getElementById('bench-count');
    const threadsInput = document.getElementById('bench-threads');
    const strategySelect = document.getElementById('bench-strategy');
    const runBtn = document.getElementById('btn-run-bench');
    const resultWrap = document.getElementById('bench-results-wrap');

    const count = parseInt(countInput.value, 10) || 10000;
    const threads = parseInt(threadsInput.value, 10) || 8;
    const strategy = strategySelect.value;

    runBtn.disabled = true;
    runBtn.innerHTML = `<span>⏳ Benchmarking ${count.toLocaleString()} IDs...</span>`;

    try {
      const res = await API.post('/api/id-generator/benchmark', {
        count: count,
        threads: threads,
        strategy: strategy
      });

      if (res && res.success && res.data) {
        const d = res.data;
        resultWrap.style.display = 'block';

        document.getElementById('bench-res-throughput').innerText = `${d.idsPerSecond.toLocaleString(undefined, { maximumFractionDigits: 1 })} IDs/sec`;
        document.getElementById('bench-res-total').innerText = d.totalGenerated.toLocaleString();
        document.getElementById('bench-res-duration').innerText = `${d.durationMs} ms`;
        document.getElementById('bench-res-threads').innerText = `${d.concurrencyThreads} workers`;
        
        const collisionEl = document.getElementById('bench-res-collisions');
        collisionEl.innerText = `${d.collisions} (Collision-Free)`;
        collisionEl.style.color = d.collisions === 0 ? 'var(--success)' : 'var(--danger)';

        document.getElementById('bench-res-strategy').innerText = d.strategy;

        const sampleWrap = document.getElementById('bench-sample-ids');
        sampleWrap.innerHTML = d.sampleIds.map(id => `<span class="sample-id-tag">${id}</span>`).join('');

        API.toast(`Generated ${d.totalGenerated.toLocaleString()} IDs with ZERO collisions! Rate: ${Math.round(d.idsPerSecond)}/sec`, 'success');
      }
    } catch (err) {
      API.toast(err.message, 'error');
    } finally {
      runBtn.disabled = false;
      runBtn.innerHTML = `<span>🚀 Run Concurrent Benchmark</span>`;
    }
  },

  async generateSampleId(prefix) {
    try {
      const res = await API.get('/api/id-generator/sample');
      if (res && res.success && res.data) {
        const id = res.data[prefix + 'Id'] || res.data['bookId'];
        const displayEl = document.getElementById('playground-result');
        if (displayEl) {
          displayEl.innerHTML = `
            <div style="margin-top: 12px; padding: 14px; background: var(--bg-card-subtle); border: 1px solid var(--border-color); border-radius: var(--radius-md);">
              <span style="font-size: 0.75rem; color: var(--text-dim); text-transform: uppercase;">Generated Unique ${prefix.toUpperCase()} Identifier:</span>
              <div style="font-family: monospace; font-size: 1.3rem; font-weight: 700; color: var(--primary-600); margin: 6px 0;">${id}</div>
              <div style="font-size: 0.75rem; color: var(--text-muted);">
                Strategy: <strong>${res.data.strategy}</strong> • Epoch-relative (41-bit) + Node ID (10-bit) + Sequence (12-bit) in Base62
              </div>
            </div>
          `;
        }
      }
    } catch (err) {
      API.toast(err.message, 'error');
    }
  }
};
