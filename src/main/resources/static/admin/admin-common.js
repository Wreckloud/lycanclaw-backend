/**
 * 管理端通用脚本：
 * - Waline 登录换取后端会话；
 * - 管理会话凭证存取；
 * - 统一请求与错误处理。
 */
(function initAdminCommon(global) {
  const TOKEN_KEY = 'lycan_admin_session_token';
  const ADMIN_TOKEN_HEADER = 'X-Lycan-Admin-Token';
  const WALINE_BASE = '/waline';

  function readToken() {
    return (global.localStorage.getItem(TOKEN_KEY) || '').trim();
  }

  function saveToken(token) {
    const value = (token || '').trim();
    if (!value) {
      clearToken();
      return;
    }
    global.localStorage.setItem(TOKEN_KEY, value);
  }

  function clearToken() {
    global.localStorage.removeItem(TOKEN_KEY);
  }

  function parseJsonBody(rawBody) {
    if (!rawBody) return null;
    try {
      return JSON.parse(rawBody);
    } catch (_ignored) {
      return null;
    }
  }

  async function requestJson(url, options = {}, authRequired = true) {
    const token = readToken();
    if (authRequired && !token) {
      throw new Error('请先完成管理员登录');
    }

    const headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    };
    if (authRequired && token) {
      headers[ADMIN_TOKEN_HEADER] = token;
    }

    const response = await fetch(url, {
      ...options,
      headers
    });
    const rawBody = await response.text();
    const payload = parseJsonBody(rawBody);

    if (!response.ok || payload?.success === false) {
      const errorCode = payload?.error?.code;
      const errorMessage = payload?.error?.message || rawBody || `请求失败: ${response.status}`;
      if (response.status === 401 || response.status === 403) {
        clearToken();
      }
      throw new Error(errorCode ? `[${errorCode}] ${errorMessage}` : errorMessage);
    }

    if (!payload || typeof payload !== 'object' || !('data' in payload)) {
      throw new Error(`响应结构异常: ${url}`);
    }
    return payload.data;
  }

  /**
   * 打开 Waline 登录页，等待 postMessage 返回 userInfo（含 token）。
   */
  function walineLoginPopup() {
    return new Promise((resolve, reject) => {
      const popupUrl = `${WALINE_BASE}/ui/login?from=${encodeURIComponent(location.href)}`;
      const popup = global.open(popupUrl, 'waline-login', 'popup=yes,width=420,height=560');
      if (!popup) {
        reject(new Error('浏览器阻止了登录弹窗，请允许弹窗后重试'));
        return;
      }

      const timeout = global.setTimeout(() => {
        cleanup();
        reject(new Error('等待 Waline 登录结果超时'));
      }, 90_000);

      function cleanup() {
        global.clearTimeout(timeout);
        global.removeEventListener('message', onMessage);
        try {
          popup.close();
        } catch (_ignored) {
          // ignore
        }
      }

      function onMessage(event) {
        if (event.origin !== location.origin) {
          return;
        }
        const data = event?.data;
        if (!data || data.type !== 'userInfo') {
          return;
        }
        const userInfo = data.data || {};
        const token = String(userInfo.token || '').trim();
        if (!token) {
          cleanup();
          reject(new Error('Waline 未返回有效登录 token'));
          return;
        }
        cleanup();
        resolve(userInfo);
      }

      global.addEventListener('message', onMessage);
    });
  }

  async function loginWithWaline() {
    const userInfo = await walineLoginPopup();
    const token = String(userInfo.token || '').trim();
    const session = await requestJson('/api/admin/auth/waline/exchange', {
      method: 'POST',
      body: JSON.stringify({ walineToken: token })
    }, false);
    saveToken(session.sessionToken || '');
    return session;
  }

  async function fetchMe() {
    return requestJson('/api/admin/auth/me', { method: 'GET' }, true);
  }

  async function logout() {
    try {
      await requestJson('/api/admin/auth/logout', { method: 'POST' }, true);
    } finally {
      clearToken();
    }
  }

  async function ensureAuthenticated(redirectToAuth = true) {
    try {
      return await fetchMe();
    } catch (error) {
      clearToken();
      if (redirectToAuth) {
        global.location.href = '/admin/auth.html';
      }
      throw error;
    }
  }

  global.adminCommon = {
    readToken,
    saveToken,
    clearToken,
    requestJson,
    loginWithWaline,
    fetchMe,
    logout,
    ensureAuthenticated
  };
})(window);
