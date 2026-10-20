// Small wrapper around the account API. Requests carry the session cookie; writes also send
// the CSRF token from the XSRF-TOKEN cookie (see SecurityConfig.java).

function csrfFromCookie() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

async function csrfToken() {
  const fromCookie = csrfFromCookie();
  if (fromCookie) return fromCookie;
  const res = await fetch("/api/auth/csrf", { credentials: "same-origin" });
  return (await res.json()).token;
}

/** Returns parsed JSON (or null), and throws an Error carrying the server's message and status. */
export async function api(path, { method = "GET", body } = {}) {
  const headers = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (method !== "GET") headers["X-XSRF-TOKEN"] = await csrfToken();
  const res = await fetch(path, {
    method,
    headers,
    credentials: "same-origin",
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const error = new Error((data && data.error) || `Request failed (${res.status})`);
    error.status = res.status;
    throw error;
  }
  return data;
}

let mePromise = null;

// Who was last logged in on this device (username, name and admin flag only), so an offline visit still
// knows who is practising and can keep their results for later. Cleared on logout, deletion and 401.
const LAST_USER_KEY = "quizverse.lastUser";

function rememberUser(user) {
  try {
    if (user) {
      localStorage.setItem(LAST_USER_KEY, JSON.stringify({
        username: user.username, displayName: user.displayName, admin: user.admin,
      }));
    } else {
      localStorage.removeItem(LAST_USER_KEY);
    }
  } catch {
    // Storage blocked: offline visits just look logged out.
  }
}

function lastUser() {
  try {
    const user = JSON.parse(localStorage.getItem(LAST_USER_KEY));
    return user && user.username ? { ...user, offline: true } : null;
  } catch {
    return null;
  }
}

export const forgetUser = () => rememberUser(null);

/**
 * The logged-in user, or null. Cached for the page's lifetime. When the server can't be reached, the
 * last user seen on this device, marked {offline: true}.
 */
export function currentUser() {
  if (!mePromise) {
    mePromise = api("/api/me")
      .then((user) => {
        rememberUser(user);
        return user;
      })
      .catch((error) => {
        if (error.status === 401) {
          rememberUser(null);
          return null;
        }
        if (error.status === undefined) return lastUser(); // network error: offline
        console.error("Could not check login:", error);
        return null;
      });
  }
  return mePromise;
}

export const register = (form) => api("/api/auth/register", { method: "POST", body: form });
export const login = (loginName, password) =>
  api("/api/auth/login", { method: "POST", body: { login: loginName, password } });
export const logout = () => api("/api/auth/logout", { method: "POST" }).finally(forgetUser);
export const updateProfile = (profile) => api("/api/me", { method: "PUT", body: profile });
export const changePassword = (currentPassword, newPassword) =>
  api("/api/me/password", { method: "POST", body: { currentPassword, newPassword } });
export const deleteAccount = (password) =>
  api("/api/me", { method: "DELETE", body: { password } }).then((r) => { forgetUser(); return r; });
export const saveAttempt =(attempt) => api("/api/attempts", { method: "POST", body: attempt });
export const listAttempts = () => api("/api/attempts");
export const bestScores = () => api("/api/attempts/best");
export const topicStats = () => api("/api/me/topics");
export const streak = () => api("/api/me/streak");
export const setDailyGoal = (goal) => api("/api/me/goal", { method: "PUT", body: { goal } });
export const mistakes = () => api("/api/me/mistakes");
export const listBookmarks = () => api("/api/me/bookmarks");
export const addBookmark = (questionId) => api(`/api/me/bookmarks/${questionId}`, { method: "PUT" });
export const removeBookmark = (questionId) => api(`/api/me/bookmarks/${questionId}`, { method: "DELETE" });
export const reportQuestion = (questionId, reason, note) =>
  api("/api/reports", { method: "POST", body: { questionId, reason, note } });
export const openReports = () => api("/api/admin/reports");
export const resolveReport = (id) => api(`/api/admin/reports/${id}/resolve`, { method: "POST" });

/** Only same-site page links (e.g. "quiz.html?exam=..."), so ?next= can't send users elsewhere. */
export function safeNext(next, fallback = "index.html") {
  return next && /^[a-z-]+\.html(\?[^#]*)?$/.test(next) ? next : fallback;
}

export function currentPageLink() {
  return window.location.pathname.split("/").pop() + window.location.search;
}
