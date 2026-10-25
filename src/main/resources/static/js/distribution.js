/**
 * Distributed Storage, MongoDB Sharded Cluster & Consistent Hash Ring Controller
 */
const Distribution = {
  overviewData: null,

  init() {
    this.loadOverview();
    this.setupListeners();
  },

  setupListeners() {
    const routeInput = document.getElementById('dist-route-input');
    if (routeInput && !routeInput.dataset.bound) {
      routeInput.dataset.bound = 'true';
      let debounce = null;
      routeInput.addEventListener('input', (e) => {
        clearTimeout(debounce);
        debounce = setTimeout(() => {
          this.testKeyRoute(e.target.value);
        }, 150);
      });
    }
  },

  async loadOverview() {
    try {
      const res = await API.get('/api/admin/distribution');
      if (res && res.success && res.data) {
        this.overviewData = res.data;
        this.renderAll(res.data);
      }
    } catch (err) {
      console.warn('Failed to load distribution overview:', err);
      const container = document.getElementById('dist-overview-container');
      if (container) {
        container.innerHTML = `<div style="color: var(--danger); padding: 16px;">Error loading distribution metrics: ${err.message}</div>`;
      }
    }
  },

  renderAll(data) {
    this.renderShardingStatus(data.mongoSharding);
    this.renderHashRingStatus(data.hashRing, data.keyDistribution);
    this.renderCooperationModel(data.cooperationModel);
  },

  renderShardingStatus(sharding) {
    if (!sharding) return;

    const clusterBadge = document.getElementById('dist-cluster-badge');
    if (clusterBadge) {
      if (sharding.isShardedCluster) {
        clusterBadge.className = 'status-pill active';
        clusterBadge.innerHTML = '<span class="status-dot"></span> Sharded Cluster Active';
      } else {
        clusterBadge.className = 'status-pill active';
        clusterBadge.innerHTML = '<span class="status-dot"></span> Storage Node Active';
      }
    }

    const typeEl = document.getElementById('dist-cluster-type');
    if (typeEl) typeEl.innerText = sharding.clusterType || 'Standalone Mongo';

    const connEl = document.getElementById('dist-conn-uri');
    if (connEl) connEl.innerText = sharding.connectionUri || 'mongodb://localhost:27017/library_db';

    const configsvrEl = document.getElementById('dist-configsvr');
    if (configsvrEl) configsvrEl.innerText = sharding.configServer || 'configsvr-rs';

    const mongosEl = document.getElementById('dist-mongos');
    if (mongosEl) mongosEl.innerText = sharding.mongosRouter || 'mongos:27017';

    // Render Shards List
    const shardsContainer = document.getElementById('dist-shards-list');
    if (shardsContainer && sharding.shards) {
      shardsContainer.innerHTML = sharding.shards.map(s => `
        <div class="card card-subtle-box" style="padding: 14px;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
            <div style="font-weight: 700; color: var(--primary-600); font-family: monospace;">${s.id}</div>
            <span class="status-pill active" style="font-size: 0.72rem;">${s.state}</span>
          </div>
          <div style="font-size: 0.8rem; color: var(--text-muted); margin-bottom: 4px;">Host: <code style="color: #A5B4FC;">${s.host}</code></div>
          ${s.details && s.details.allocationStrategy ? `
            <div style="font-size: 0.75rem; color: var(--text-dim);">${s.details.allocationStrategy}</div>
          ` : ''}
        </div>
      `).join('');
    }

    // Render Shard Keys
    const keysContainer = document.getElementById('dist-shard-keys-list');
    if (keysContainer && sharding.shardKeys) {
      keysContainer.innerHTML = Object.entries(sharding.shardKeys).map(([coll, keyDef]) => `
        <div class="card-subtle-box" style="padding: 8px 12px; border-radius: var(--radius-sm); margin-bottom: 8px;">
          <div style="font-weight: 700; color: var(--primary-600); font-size: 0.82rem; margin-bottom: 2px;">${coll}</div>
          <div style="font-size: 0.78rem; color: var(--text-muted); font-family: monospace;">Shard Key: ${keyDef}</div>
        </div>
      `).join('');
    }
  },

  renderHashRingStatus(ring, keyDist) {
    if (!ring) return;

    const nodeCountEl = document.getElementById('dist-node-count');
    if (nodeCountEl) nodeCountEl.innerText = ring.activeNodeCount;

    const vnodeCountEl = document.getElementById('dist-vnode-count');
    if (vnodeCountEl) vnodeCountEl.innerText = ring.virtualNodesPerNode;

    const totalVnodesEl = document.getElementById('dist-total-vnodes');
    if (totalVnodesEl) totalVnodesEl.innerText = ring.totalVirtualNodes;

    const algoEl = document.getElementById('dist-algo');
    if (algoEl) algoEl.innerText = ring.hashAlgorithm;

    // Uniformity metrics
    if (keyDist) {
      const totalKeysEl = document.getElementById('dist-total-keys');
      if (totalKeysEl) totalKeysEl.innerText = keyDist.totalKeys;

      const stdDevEl = document.getElementById('dist-std-dev');
      if (stdDevEl) stdDevEl.innerText = `${keyDist.standardDeviation} keys`;

      const cvEl = document.getElementById('dist-cv');
      if (cvEl) cvEl.innerText = `${keyDist.coefficientOfVariationPercent}%`;
    }

    // Render Physical Node Cards
    const nodesList = document.getElementById('dist-nodes-grid');
    if (nodesList) {
      const counts = (keyDist && keyDist.perNodeCounts) ? keyDist.perNodeCounts : {};
      const pcts = (keyDist && keyDist.perNodePercentages) ? keyDist.perNodePercentages : {};

      nodesList.innerHTML = ring.physicalNodes.map((node, idx) => {
        const count = counts[node] || 0;
        const pct = pcts[node] || 0.0;
        return `
          <div class="card stat-card" style="padding: 16px; border: 1px solid var(--border-color); background: rgba(15, 23, 42, 0.65);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;">
              <div style="font-weight: 600; font-size: 0.95rem; color: var(--text-main); display: flex; align-items: center; gap: 8px;">
                <span class="user-role-badge" style="font-size: 0.72rem; padding: 2px 6px;">NODE</span> ${node}
              </div>
              ${ring.activeNodeCount > 1 ? `
                <button class="btn btn-outline btn-sm" style="padding: 2px 8px; font-size: 0.75rem; color: var(--danger); border-color: rgba(239, 68, 68, 0.4);"
                        onclick="Distribution.removeNode('${node}')" title="Remove node and trigger consistent rebalance">
                  Remove
                </button>
              ` : ''}
            </div>

            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-bottom: 10px; font-size: 0.8rem;">
              <div>
                <span style="color: var(--text-dim);">Assigned Keys:</span>
                <strong style="color: var(--accent-cyan); display: block; font-size: 1.05rem;">${count}</strong>
              </div>
              <div>
                <span style="color: var(--text-dim);">Ring Share:</span>
                <strong style="color: #FCD34D; display: block; font-size: 1.05rem;">${pct}%</strong>
              </div>
            </div>

            <!-- Progress Bar -->
            <div style="height: 6px; background: rgba(255,255,255,0.1); border-radius: 3px; overflow: hidden; margin-bottom: 8px;">
              <div style="width: ${Math.min(100, pct * 2.5)}%; height: 100%; background: linear-gradient(90deg, #6366F1, #06B6D4); border-radius: 3px;"></div>
            </div>

            <div style="font-size: 0.72rem; color: var(--text-dim); display: flex; justify-content: space-between;">
              <span>${ring.virtualNodesPerNode} Virtual Nodes</span>
              <span style="font-family: monospace;">[0 .. 2^32]</span>
            </div>
          </div>
        `;
      }).join('');
    }
  },

  renderCooperationModel(coop) {
    if (!coop) return;
    const appRoleEl = document.getElementById('dist-coop-app');
    if (appRoleEl) appRoleEl.innerText = coop.applicationRouterRole;

    const dbRoleEl = document.getElementById('dist-coop-db');
    if (dbRoleEl) dbRoleEl.innerText = coop.databaseShardingRole;

    const synergyEl = document.getElementById('dist-coop-synergy');
    if (synergyEl) synergyEl.innerText = coop.synergySummary;
  },

  async addNode() {
    const input = document.getElementById('dist-new-node-input');
    const name = input ? input.value.trim() : '';
    if (!name) {
      API.toast('Please enter a valid node identifier (e.g. storage-node-5)', 'error');
      return;
    }

    try {
      const res = await API.post('/api/admin/distribution/nodes', { nodeName: name });
      if (res && res.success) {
        API.toast(`Node '${name}' added to Consistent Hash Ring!`, 'success');
        if (input) input.value = '';
        this.loadOverview();
      }
    } catch (err) {
      API.toast(`Failed to add node: ${err.message}`, 'error');
    }
  },

  async removeNode(nodeName) {
    if (!confirm(`Are you sure you want to remove node '${nodeName}' from the consistent hash ring? Its assigned keys will smoothly migrate to neighboring nodes.`)) {
      return;
    }

    try {
      const res = await API.delete(`/api/admin/distribution/nodes/${encodeURIComponent(nodeName)}`);
      if (res && res.success) {
        API.toast(`Node '${nodeName}' removed. Keys migrated seamlessly!`, 'success');
        this.loadOverview();
      }
    } catch (err) {
      API.toast(`Failed to remove node: ${err.message}`, 'error');
    }
  },

  async testKeyRoute(key) {
    const resultBox = document.getElementById('dist-route-result');
    if (!resultBox) return;

    if (!key || key.trim().length === 0) {
      resultBox.innerHTML = '<span style="color: var(--text-dim); font-size: 0.85rem;">Type a key above (e.g. BK-RQwX87z9s2 or MEM-101) to trace its ring position...</span>';
      return;
    }

    try {
      const res = await API.get(`/api/admin/distribution/route?key=${encodeURIComponent(key.trim())}`);
      if (res && res.success && res.data) {
        const d = res.data;
        resultBox.innerHTML = `
          <div style="background: rgba(99, 102, 241, 0.1); border: 1px solid rgba(99, 102, 241, 0.3); border-radius: var(--radius-md); padding: 14px;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
              <span style="font-size: 0.85rem; color: var(--text-muted);">Assigned Logical Node:</span>
              <span class="user-role-badge admin" style="font-size: 0.85rem;">${d.assignedNode}</span>
            </div>
            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 8px; font-size: 0.8rem;">
              <div><span style="color: var(--text-dim);">Murmur3 32-bit Hash:</span> <code style="color: #A5B4FC;">${d.keyHash}</code></div>
              <div><span style="color: var(--text-dim);">Ring Coordinate:</span> <strong style="color: #FCD34D;">${d.ringPositionPercent}%</strong></div>
              <div><span style="color: var(--text-dim);">Target Virtual Node:</span> <code style="color: var(--accent-cyan);">${d.virtualNode}</code></div>
            </div>
          </div>
        `;
      }
    } catch (err) {
      resultBox.innerHTML = `<span style="color: var(--danger); font-size: 0.85rem;">Route error: ${err.message}</span>`;
    }
  },

  async runRebalanceSimulation() {
    const resultBox = document.getElementById('dist-rebalance-result');
    const nodeInput = document.getElementById('dist-sim-node-input');
    const opSelect = document.getElementById('dist-sim-op-select');
    const keyCountInput = document.getElementById('dist-sim-keys-input');

    if (!resultBox) return;

    const nodeName = nodeInput ? nodeInput.value.trim() : 'storage-node-scale-test';
    const operation = opSelect ? opSelect.value : 'ADD';
    const keyCount = keyCountInput ? parseInt(keyCountInput.value) || 2000 : 2000;

    resultBox.style.display = 'block';
    resultBox.innerHTML = '<div style="text-align: center; padding: 20px; color: var(--text-muted);">Executing consistent hash simulation...</div>';

    try {
      const res = await API.post('/api/admin/distribution/rebalance-simulation', {
        nodeName: nodeName,
        operation: operation,
        keyCount: keyCount
      });

      if (res && res.success && res.data) {
        const d = res.data;
        resultBox.innerHTML = `
          <div style="border-left: 4px solid var(--accent-cyan); padding-left: 14px; margin-bottom: 16px;">
            <h4 style="font-size: 1rem; font-weight: 600; color: var(--text-main); margin-bottom: 4px;">
              ${d.operation === 'ADD' ? 'Node Addition Simulation' : 'Node Removal Simulation'}
            </h4>
            <p style="font-size: 0.85rem; color: var(--text-main); line-height: 1.5;">${d.conclusion}</p>
          </div>

          <div class="stats-grid" style="grid-template-columns: repeat(4, 1fr); margin-bottom: 20px;">
            <div class="card stat-card card-subtle-box">
              <div class="stat-content">
                <div class="metric-highlight" style="font-size: 1.4rem;">${d.totalKeys}</div>
                <p>Keys Evaluated</p>
              </div>
            </div>
            <div class="card stat-card card-subtle-box">
              <div class="stat-content">
                <div class="metric-highlight" style="font-size: 1.4rem; color: var(--warning);">${d.movedKeys}</div>
                <p>Keys Remapped</p>
              </div>
            </div>
            <div class="card stat-card card-subtle-box">
              <div class="stat-content">
                <div class="metric-highlight" style="font-size: 1.4rem; color: var(--success);">${d.actualRemapPercent}%</div>
                <p>Actual Remapped %</p>
              </div>
            </div>
            <div class="card stat-card card-subtle-box">
              <div class="stat-content">
                <div class="metric-highlight" style="font-size: 1.4rem; color: var(--primary-600);">${d.theoreticalRemapPercent}%</div>
                <p>Theoretical 1/${d.operation === 'ADD' ? '(N+1)' : 'N'}</p>
              </div>
            </div>
          </div>

          <!-- Comparison Table -->
          <div style="font-size: 0.82rem; margin-top: 10px;">
            <div style="font-weight: 700; color: var(--text-dim); margin-bottom: 8px; text-transform: uppercase;">Per-Node Key Allocation (Before vs After):</div>
            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px;">
              ${Object.keys(d.distributionAfter || {}).map(node => {
                const before = (d.distributionBefore && d.distributionBefore[node]) || 0;
                const after = d.distributionAfter[node] || 0;
                const diff = after - before;
                return `
                  <div class="card-subtle-box" style="padding: 10px; border-radius: var(--radius-sm);">
                    <div style="font-weight: 700; color: var(--primary-600); font-size: 0.85rem;">${node}</div>
                    <div style="display: flex; justify-content: space-between; margin-top: 4px;">
                      <span style="color: var(--text-dim);">Before: ${before}</span>
                      <span style="color: var(--text-main); font-weight: 700;">After: ${after}</span>
                    </div>
                    <div style="font-size: 0.75rem; color: ${diff >= 0 ? 'var(--success)' : 'var(--danger)'}; margin-top: 2px;">
                      Delta: ${diff >= 0 ? '+' + diff : diff} keys
                    </div>
                  </div>
                `;
              }).join('')}
            </div>
          </div>
        `;
      }
    } catch (err) {
      resultBox.innerHTML = `<div style="color: var(--danger); padding: 16px;">Simulation failed: ${err.message}</div>`;
    }
  },

  async runUniformityBenchmark() {
    const reportBox = document.getElementById('dist-bench-report');
    if (!reportBox) return;

    reportBox.style.display = 'block';
    reportBox.innerHTML = '<div style="text-align: center; padding: 20px; color: var(--text-muted);">Generating 10,000 synthetic keys and evaluating ring balance...</div>';

    try {
      const res = await API.post('/api/admin/distribution/benchmark?sampleSize=10000');
      if (res && res.success && res.data) {
        const d = res.data;
        reportBox.innerHTML = `
          <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-color); padding-bottom: 12px; margin-bottom: 16px;">
            <div>
              <h4 style="font-size: 1.05rem; font-weight: 700; color: var(--accent-cyan);">10,000 Key Uniformity Benchmark Results</h4>
              <p style="font-size: 0.8rem; color: var(--text-muted);">Evaluates standard deviation and coefficient of variation with ${d.virtualNodesPerNode} vnodes/node</p>
            </div>
            <span class="status-pill active" style="font-size: 0.85rem;">${d.evaluation}</span>
          </div>

          <div class="stats-grid" style="grid-template-columns: repeat(4, 1fr); margin-bottom: 16px;">
            <div class="card stat-card card-subtle-box">
              <div class="stat-content">
                <div class="metric-highlight" style="font-size: 1.4rem; color: var(--success);">${d.balanceScorePercent}%</div>
                <p>Ring Balance Score</p>
              </div>
            </div>
            <div class="card stat-card card-subtle-box">
              <div class="stat-content">
                <div class="metric-highlight" style="font-size: 1.4rem;">${d.standardDeviation}</div>
                <p>Standard Deviation</p>
              </div>
            </div>
            <div class="card stat-card card-subtle-box">
              <div class="stat-content">
                <div class="metric-highlight" style="font-size: 1.4rem; color: var(--warning);">${d.coefficientOfVariationPercent}%</div>
                <p>Coeff. of Variation</p>
              </div>
            </div>
            <div class="card stat-card card-subtle-box">
              <div class="stat-content">
                <div class="metric-highlight" style="font-size: 1.4rem; color: var(--primary-600);">${d.totalVirtualNodes}</div>
                <p>Total Virtual Points</p>
              </div>
            </div>
          </div>
        `;
      }
    } catch (err) {
      reportBox.innerHTML = `<div style="color: var(--danger); padding: 16px;">Benchmark failed: ${err.message}</div>`;
    }
  }
};
