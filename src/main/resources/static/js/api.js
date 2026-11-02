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

let statusPromise = null;

/** {temporaryStorage, googleClientId} from the server, or null when it can't be reached (offline). */
export function serverStatus() {
  if (!statusPromise) statusPromise = api("/api/status").catch(() => null);
  return statusPromise;
}

export const googleSignIn = (credential) => api("/api/auth/google", { method: "POST", body: { credential } });
export const linkGoogle = (credential) => api("/api/me/google", { method: "POST", body: { credential } });
export const unlinkGoogle = () => api("/api/me/google", { method: "DELETE" });
export const createChallenge = (attemptId) => api(`/api/challenges/from-attempt/${attemptId}`, { method: "POST" });
export const getChallenge = (code) => api(`/api/challenges/${encodeURIComponent(code)}`);
export const requestPasswordReset = (email) => api("/api/auth/password-reset", { method: "POST", body: { email } });
export const confirmPasswordReset = (token, newPassword) =>
  api("/api/auth/password-reset/confirm", { method: "POST", body: { token, newPassword } });
export const setEmailPreferences = (reminders, weekly) =>
  api("/api/me/email-preferences", { method: "PUT", body: { reminders, weekly } });
export const unsubscribe = (token) => api(`/api/email/unsubscribe?token=${encodeURIComponent(token)}`, { method: "POST" });
export const startServerQuiz = (quiz) => api("/api/quiz/start", { method: "POST", body: { quiz } });
export const submitServerQuiz = (quiz, startToken, chosen) =>
  api("/api/quiz/submit", { method: "POST", body: { quiz, startToken, chosen } });
export const quizLeaderboard = (key) => api(`/api/leaderboards/quiz?key=${encodeURIComponent(key)}`);
export const weeklyLeaderboard = () => api("/api/leaderboards/weekly");
export const setLeaderboardHidden = (hidden) => api("/api/me/leaderboard", { method: "PUT", body: { hidden } });
export const listContests = () => api("/api/contests");
export const contest = (id) => api(`/api/contests/${id}`);
export const registerContest = (id) => api(`/api/contests/${id}/register`, { method: "POST" });
export const contestPaper = (id) => api(`/api/contests/${id}/paper`);
export const submitContest = (id, chosen) => api(`/api/contests/${id}/submit`, { method: "POST", body: { chosen } });
export const contestResults = (id) => api(`/api/contests/${id}/results`);
export const createContest = (c) => api("/api/admin/contests", { method: "POST", body: c });
export const contestQuizzes = () => api("/api/admin/contests/quizzes");
export const register = (form) => api("/api/auth/register", { method: "POST", body: form });
export const login = (loginName, password) =>
  api("/api/auth/login", { method: "POST", body: { login: loginName, password } });
export const logout = () => api("/api/auth/logout", { method: "POST" }).finally(forgetUser);
export const updateProfile = (profile) => api("/api/me", { method: "PUT", body: profile });
export const changePassword = (currentPassword, newPassword) =>
  api("/api/me/password", { method: "POST", body: { currentPassword, newPassword } });
export const deleteAccount = (password, confirmUsername) =>
  api("/api/me", { method: "DELETE", body: { password, confirmUsername } }).then((r) => { forgetUser(); return r; });
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
export const questionEdits = () => api("/api/admin/question-edits");
export const saveQuestionEdit = (questionId, edit) =>
  api(`/api/admin/question-edits/${questionId}`, { method: "PUT", body: edit });
export const discardQuestionEdit = (questionId) => api(`/api/admin/question-edits/${questionId}`, { method: "DELETE" });

/** Only same-site page links (e.g. "quiz.html?exam=..."), so ?next= can't send users elsewhere. */
export function safeNext(next, fallback = "index.html") {
  return next && /^[a-z-]+\.html(\?[^#]*)?$/.test(next) ? next : fallback;
}

export function currentPageLink() {
  return window.location.pathname.split("/").pop() + window.location.search;
}
