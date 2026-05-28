/**
 * 管理端通用脚本：
 * - 统一管理员令牌存取；
 * - 统一请求头注入与错误处理。
 */
(function initAdminCommon(global) {
  const TOKEN_KEY = 'lycan_admin_token';

  function readToken() {
    return (global.localStorage.getItem(TOKEN_KEY) || '').trim();
  }

  function saveToken(token) {
    const value = (token || '').trim();
    if (!value) {
      global.localStorage.removeItem(TOKEN_KEY);
      return;
    }
    global.localStorage.setItem(TOKEN_KEY, value);
  }

  function clearToken() {
    global.localStorage.removeItem(TOKEN_KEY);
  }

  async function requestJson(url, options = {}) {
    const token = readToken();
    if (!token) {
      throw new Error('请先填写并保存管理员令牌');
    }

    const headers = {
      'Content-Type': 'application/json',
      'X-Lycan-Admin-Token': token,
      ...(options.headers || {})
    };

    const response = await fetch(url, {
      ...options,
      headers
    });

    const payload = await response.json();
    if (!response.ok || payload.success === false) {
      const errorCode = payload?.error?.code;
      const errorMessage = payload?.error?.message || `请求失败: ${response.status}`;
      throw new Error(errorCode ? `[${errorCode}] ${errorMessage}` : errorMessage);
    }
    return payload.data;
  }

  function bindTokenInput(inputId, statusWriter) {
    const input = document.getElementById(inputId);
    if (!input) return;
    input.value = readToken();

    input.addEventListener('change', () => {
      saveToken(input.value);
      if (typeof statusWriter === 'function') {
        statusWriter('管理员令牌已保存到当前浏览器');
      }
    });
  }

  global.adminCommon = {
    readToken,
    saveToken,
    clearToken,
    requestJson,
    bindTokenInput
  };
})(window);
