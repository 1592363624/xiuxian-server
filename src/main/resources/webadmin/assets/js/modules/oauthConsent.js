import * as api from '../api.js';

/**
 * 第三方应用授权同意页。
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>第三方把玩家浏览器重定向到
 *       {@code /api/auth/oauth/authorize?response_type=code&client_id=...&redirect_uri=...&scope=...&state=...}；</li>
 *   <li>本页读取 URL 上的参数，调后端 {@code GET /api/auth/oauth/authorize} 校验；</li>
 *   <li>校验通过后展示「应用名 + 它要什么权限」，由玩家决定同意或拒绝；</li>
 *   <li>点同意 → {@code POST /authorize/confirm} 拿到带 code 的回调地址 → 跳回第三方。</li>
 * </ol>
 *
 * <h3>为什么必须停在这一页</h3>
 * <p>不能"参数合法就直接发码"。OAuth 的意义是<b>玩家知情同意</b>，
 * 跳过同意页等于第三方自己给自己授权。而且玩家有权看到
 * 具体是哪些权限、风险多高，而不是一个笼统的「访问你的数据」。</p>
 *
 * <h3>state 原样回传</h3>
 * <p>{@code state} 由第三方生成用于防 CSRF，服务端不解释它的内容，
 * 但必须原样带回 —— 丢掉它会让第三方的防护失效。</p>
 */

const PARAMS = ['response_type', 'client_id', 'redirect_uri', 'scope', 'state'];

let currentRequest = null;

/** URL 上是否带有授权请求参数（决定是否切到同意页） */
export function hasAuthorizeRequest() {
  const q = new URLSearchParams(window.location.search);
  return q.has('client_id') && (q.has('redirect_uri') || q.has('scope'));
}

/** 从当前 URL 取出授权请求参数 */
export function readAuthorizeRequest() {
  const q = new URLSearchParams(window.location.search);
  const req = {};
  for (const key of PARAMS) {
    req[key] = q.get(key) || '';
  }
  if (!req.response_type) {
    req.response_type = 'code';
  }
  return req;
}

export async function load() {
  const box = document.getElementById('consentBody');
  if (!box) {
    return;
  }

  const req = readAuthorizeRequest();
  if (!req.client_id) {
    box.innerHTML = '<div class="empty err">缺少 client_id，这不是一个有效的授权请求。</div>';
    return;
  }

  try {
    const query = `?response_type=${encodeURIComponent(req.response_type)}`
      + `&client_id=${encodeURIComponent(req.client_id)}`
      + `&redirect_uri=${encodeURIComponent(req.redirect_uri)}`
      + `&scope=${encodeURIComponent(req.scope)}`
      + (req.state ? `&state=${encodeURIComponent(req.state)}` : '');

    const resp = await api.get('/auth/oauth/authorize' + query);
    if (resp.code !== 0 && resp.code !== 200) {
      // 这些错误必须原样展示给用户，而不是跳转 —— 参数不合法时
      // 绝不能往那个 redirect_uri 重定向（否则成了开放重定向）
      box.innerHTML = `<div class="empty err">
        <strong>授权请求无效</strong><br>
        ${escapeHtml(resp.message || resp.error_description || '未知错误')}
      </div>`;
      return;
    }

    currentRequest = resp.data || {};
    renderConsent(req, currentRequest);
  } catch (e) {
    box.innerHTML = `<div class="empty err">加载授权请求失败: ${escapeHtml(e.message)}</div>`;
  }
}

function renderConsent(req, data) {
  const box = document.getElementById('consentBody');
  const title = document.getElementById('consentTitle');
  const appName = data.client_name || req.client_id;

  if (title) {
    title.textContent = '🔐 第三方应用授权';
  }

  const scopes = data.scopes || [];
  const hasHighRisk = scopes.some(s => s.risk === 'HIGH');

  const scopeRows = scopes.map(s => {
    const risk = s.risk === 'HIGH' ? 'high' : (s.risk === 'MEDIUM' ? 'medium' : 'low');
    const riskLabel = s.risk === 'HIGH' ? '高风险' : (s.risk === 'MEDIUM' ? '中风险' : '只读');
    return `
      <li class="consent-scope">
        <span class="consent-icon">${escapeHtml(s.icon || '•')}</span>
        <div class="consent-scope-text">
          <div class="consent-scope-title">
            ${escapeHtml(s.title)}
            <span class="scope-chip ${risk}">${riskLabel}</span>
          </div>
          <div class="consent-scope-desc">${escapeHtml(s.description)}</div>
        </div>
      </li>`;
  }).join('');

  box.innerHTML = `
    <div class="consent-head">
      <div class="consent-app">${escapeHtml(appName)}</div>
      <div class="consent-sub">请求访问你的修仙世界账号数据</div>
    </div>

    <ul class="consent-list">${scopeRows}</ul>

    ${hasHighRisk ? `
      <div class="consent-warn">
        ⚠️ 该应用申请了<b>会改动游戏数据</b>的权限。
        授予后它可以在你授权的范围内替你执行操作（如装备、交易）。
        请只对你信任的应用授权。
      </div>` : ''}

    <div class="consent-tip">
      授权后你可以随时在「已授权应用」页面撤销。撤销后该应用立即失去访问能力。
    </div>

    <div class="consent-actions">
      <button class="btn btn-danger" id="consentDeny">拒绝</button>
      <button class="btn btn-primary" id="consentAllow">同意授权</button>
    </div>
  `;

  document.getElementById('consentAllow').onclick = () => confirmConsent(true);
  document.getElementById('consentDeny').onclick = () => confirmConsent(false);
}

async function confirmConsent(approved) {
  if (!currentRequest) {
    return;
  }
  const req = readAuthorizeRequest();

  const btnAllow = document.getElementById('consentAllow');
  const btnDeny = document.getElementById('consentDeny');
  if (btnAllow) {
    btnAllow.disabled = true;
  }
  if (btnDeny) {
    btnDeny.disabled = true;
  }

  try {
    const resp = await api.post('/auth/oauth/authorize/confirm', {
      client_id: currentRequest.client_id || req.client_id,
      redirect_uri: currentRequest.redirect_uri || req.redirect_uri,
      scope: req.scope,
      state: req.state,
      approved: approved
    });

    if (resp.code !== 0 && resp.code !== 200) {
      const box = document.getElementById('consentBody');
      box.innerHTML = `<div class="empty err">${escapeHtml(resp.message || '操作失败')}</div>`;
      return;
    }

    const target = resp.redirect_uri || resp.data?.redirect_uri;
    if (target) {
      box_showRedirecting(target);
      window.location.href = target;
    } else {
      document.getElementById('consentBody').innerHTML =
        '<div class="empty">操作已完成，但服务端未返回跳转地址。你可以关闭此页面。</div>';
    }
  } catch (e) {
    document.getElementById('consentBody').innerHTML =
      `<div class="empty err">操作失败: ${escapeHtml(e.message)}</div>`;
  }
}

function box_showRedirecting(target) {
  const box = document.getElementById('consentBody');
  if (!box) {
    return;
  }
  box.innerHTML = `
    <div class="empty">
      正在返回应用…<br>
      <span style="color:#888;font-size:12px;">如果没有自动跳转，请
        <a href="${escapeAttr(target)}">点击这里</a>。
      </span>
    </div>`;
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
