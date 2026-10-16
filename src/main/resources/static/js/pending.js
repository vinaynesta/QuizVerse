// A result finished while logged out waits here (this tab only) until the user logs in or signs up.
const KEY = "quizverse.pendingAttempt";

export function storePendingAttempt(attempt) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(attempt));
  } catch {
    // Storage blocked (private mode etc.): the result just isn't carried over.
  }
}

export function takePendingAttempt() {
  try {
    const raw = sessionStorage.getItem(KEY);
    sessionStorage.removeItem(KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}
