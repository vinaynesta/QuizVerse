// Quiz results that couldn't be saved straight away:
//  - finished while logged out: kept for this tab until the user logs in or signs up (sessionStorage);
//  - finished offline while logged in: kept on this device, tagged with the username, and uploaded when
//    the connection is back (localStorage). Only the same user's results are uploaded, so a shared phone
//    never saves one person's result to another's account.
const KEY = "quizverse.pendingAttempt";
const OFFLINE_KEY = "quizverse.offlineAttempts";
const OFFLINE_MAX = 50;

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

function readOffline() {
  try {
    const list = JSON.parse(localStorage.getItem(OFFLINE_KEY));
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

function writeOffline(list) {
  try {
    if (list.length) localStorage.setItem(OFFLINE_KEY, JSON.stringify(list.slice(-OFFLINE_MAX)));
    else localStorage.removeItem(OFFLINE_KEY);
  } catch {
    // Storage blocked: nothing we can do.
  }
}

/** Returns false if the result couldn't be stored (storage blocked). */
export function storeOfflineAttempt(username, attempt) {
  const list = readOffline();
  list.push({ username, attempt });
  writeOffline(list);
  return readOffline().length === Math.min(list.length, OFFLINE_MAX);
}

let flushing = null;

/**
 * Uploads this user's waiting results with save(attempt). Stops at the first failure (still offline)
 * and keeps the rest. Results the server rejects as invalid are dropped so they can't block the queue.
 * Calls made while an upload is running share it, so a result is never sent twice. Resolves to how
 * many were saved.
 */
export function flushOfflineAttempts(username, save) {
  if (!flushing) flushing = doFlush(username, save).finally(() => { flushing = null; });
  return flushing;
}

async function doFlush(username, save) {
  const list = readOffline();
  const mine = list.filter((x) => x.username === username);
  if (!mine.length) return 0;
  const done = new Set();
  let saved = 0;
  for (const item of mine) {
    try {
      await save(item.attempt);
      done.add(item);
      saved += 1;
    } catch (error) {
      if (error.status === 400) done.add(item);
      else break;
    }
  }
  writeOffline(list.filter((x) => !done.has(x)));
  return saved;
}
