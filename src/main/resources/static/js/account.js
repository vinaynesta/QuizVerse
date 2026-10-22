// Fills the account bar at the top of every page: "Log in / Sign up" or the user's name with
// (linking to their profile), links to their history and a logout button.
import { currentPageLink, currentUser, logout, saveAttempt, serverStatus } from "./api.js";
import { flushOfflineAttempts } from "./pending.js";

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

const THEME_BUTTON = '<button type="button" id="theme-button" class="theme-button"></button>';

function setupThemeButton() {
  const button = document.getElementById("theme-button");
  if (!button || !window.toggleTheme) return;
  const label = () => {
    const dark = window.isDarkTheme();
    button.textContent = dark ? "☀" : "☾";
    button.title = dark ? "Switch to light theme" : "Switch to dark theme";
    button.setAttribute("aria-label", button.title);
  };
  label();
  button.addEventListener("click", () => {
    window.toggleTheme();
    label();
  });
}

async function renderAccountBar() {
  const bar = document.getElementById("account-bar");
  if (!bar) return;
  const user = await currentUser();
  const next = encodeURIComponent(currentPageLink());
  bar.innerHTML = user
    ? `<a class="brand" href="index.html">QuizVerse</a>
       <span class="account-links">
         <a href="profile.html" class="account-name" title="Your profile">${escapeHtml(user.displayName)}</a>
         <a href="history.html">My scores</a>
         <a href="practice.html">Practice</a>
         ${user.admin ? '<a href="reports.html">Reports</a>' : ""}
         <button type="button" id="logout-button">Log out</button>
         ${THEME_BUTTON}
       </span>`
    : `<a class="brand" href="index.html">QuizVerse</a>
       <span class="account-links">
         <a href="practice.html">Practice</a>
         <a href="login.html?next=${next}">Log in</a>
         <a class="account-primary" href="register.html?next=${next}">Sign up</a>
         ${THEME_BUTTON}
       </span>`;
  setupThemeButton();
  if (user && user.offline) {
    bar.querySelector(".account-links").insertAdjacentHTML("afterbegin", '<span class="offline-badge">Offline</span>');
  } else if (user) {
    uploadOfflineResults(user.username);
  }
  const button = document.getElementById("logout-button");
  if (button) {
    button.addEventListener("click", async () => {
      button.disabled = true;
      try {
        await logout();
      } finally {
        window.location.href = "index.html";
      }
    });
  }
}

/** Test mode: the server keeps accounts in a local H2 file (not production), so say so above the bar. */
async function renderTestModeStrip() {
  const bar = document.getElementById("account-bar");
  const status = await serverStatus();
  if (!bar || !status || !status.temporaryStorage) return;
  bar.insertAdjacentHTML("beforebegin",
    '<p class="test-mode-strip" role="note">Test mode: accounts and scores are stored temporarily and may be deleted.</p>');
}

/** Results finished offline are uploaded on the next online page load, or when the connection returns. */
async function uploadOfflineResults(username) {
  const saved = await flushOfflineAttempts(username, saveAttempt);
  if (saved > 0) console.info(`Saved ${saved} quiz result(s) finished offline.`);
}

window.addEventListener("online", async () => {
  const user = await currentUser();
  if (user) uploadOfflineResults(user.username);
});

// Installable app: the service worker caches pages and quiz data so practice works offline.
if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/sw.js").catch((error) => console.error("Offline mode unavailable:", error));
  });
}

renderAccountBar();
renderTestModeStrip();
