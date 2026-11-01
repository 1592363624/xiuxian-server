import * as api from '../api.js';

/**
 * 已授权应用页面。
 *
 * <p>玩家在这里看到「哪些第三方应用能读我的数据」，并能一键撤销。
 * 这是 OAuth 授权流程的必要组成部分 —— 不能只有授权入口而没有撤销入口，
 * 那等于让玩家签一份无法解除的合同。</p>
 *
 * <p>页面刻意显示<b>每一项权限的具体含义</b>（来自后端 scope 定义的 title），
 * 而不是 {@code inventory:read} 这种技术名词。玩家需要能判断
 * "这个应用到底能看什么"才谈得上知情同意。</p>
 */

let cached = [];

export async function load() {
  const box = document.getElementById('authorizedAppsList');
  if (!box) {
    return;
  }
  try {
    const resp = await api.get('/auth/oauth/grants');
    if (resp.code !== 0 && resp.code !== 200) {
      box.innerHTML = `<div class="empty err">${resp.message || '加载失败'}</div>`;
      return;
    }
    const data = resp.data || {};
    cached = data.grants || [];
    render(cached);
  } catch (e) {
    box.innerHTML = `<div class="empty err">加载已授权应用失败: ${escapeHtml(e.message)}</div>`;
  }
}

function render(grants) {
  const box = document.getElementById('authorizedAppsList');
  if (!box) {
    return;
  }

  if (!grants.length) {
    box.innerHTML = `
      <div class="empty">
        还没有任何第三方应用获得你的授权。<br>
        <span style="color:#888;font-size:13px;">
          当你在第三方应用中点击「使用修仙世界账号登录」并同意授权后，它会出现在这里。
        </span>
      </div>`;
    return;
  }

  box.innerHTML = grants.map(renderCard).join('');
}

function renderCard(grant) {
  const scopes = grant.scopes || [];
  const scopeHtml = scopes.length
    ? scopes.map(s => {
        const risk = s.risk === 'HIGH' ? 'high' : (s.risk === 'MEDIUM' ? 'medium' : 'low');
        return `<span class="scope-chip ${risk}" title="${escapeHtml(s.name)}">${escapeHtml(s.title || s.name)}</span>`;
      }).join('')
    : '<span style="color:#888;">（无权限）</span>';

  const riskHint = scopes.some(s => s.risk === 'HIGH')
    ? `<div class="scope-warn">⚠️ 该应用拥有会改动游戏数据的权限，请确认你信任它。</div>`
    : '';

  return `
    <div class="app-grant-card">
      <div class="app-grant-head">
        <div class="app-grant-name">${escapeHtml(grant.client_name || grant.client_id)}</div>
        <button class="btn-sm btn-danger" onclick="window.app.modules.authorizedApps.revoke('${escapeAttr(grant.grant_id)}')">
          撤销授权
        </button>
      </div>
      <div class="app-grant-scopes">${scopeHtml}</div>
      ${riskHint}
      <div class="app-grant-meta">
        授权时间：${formatTime(grant.created_at)} &nbsp;·&nbsp;
        最近使用：${formatTime(grant.last_used_at)}
      </div>
    </div>`;
}

/**
 * 撤销一条授权。
 *
 * <p>确认框是必要的：撤销后第三方会立即失去访问能力，
 * 误点会导致那边报错，用户不知道为什么。</p>
 */
export async function revoke(grantId) {
  if (!grantId) {
    return;
  }
  if (!confirm('确定要撤销这个应用的授权吗？\n\n撤销后它会立即失去访问你游戏数据的能力，如需再次使用需要重新授权。')) {
    return;
  }
  try {
    const resp = await api.post('/auth/oauth/revoke', { grant_id: grantId });
    if (resp.code === 0 || resp.code === 200) {
      load();
    } else {
      alert(resp.message || '撤销失败');
    }
  } catch (e) {
    alert('撤销失败: ' + e.message);
  }
}

/** 撤销当前用户的全部授权（恐慌时用） */
export async function revokeAll() {
  if (!cached.length) {
    alert('当前没有需要撤销的授权');
    return;
  }
  if (!confirm(`确定要撤销全部 ${cached.length} 个应用的授权吗？`)) {
    return;
  }
  for (const g of cached) {
    try {
      await api.post('/auth/oauth/revoke', { grant_id: g.grant_id });
    } catch (e) {
      // 单条失败不影响其它，最后统一刷新
    }
  }
  load();
}

// ==================== 工具 ====================

function formatTime(ms) {
  if (!ms) {
    return '—';
  }
  const d = new Date(Number(ms));
  if (Number.isNaN(d.getTime())) {
    return '—';
  }
  const pad = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
       + `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function escapeHtml(text) {
  if (text == null) {
    return '';
  }
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function escapeAttr(text) {
  return escapeHtml(text).replace(/'/g, '&#39;');
}
