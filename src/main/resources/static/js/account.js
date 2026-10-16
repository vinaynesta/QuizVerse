// Fills the account bar at the top of every page: "Log in / Sign up" or the user's name with
// (linking to their profile), links to their history and a logout button.
import { currentPageLink, currentUser, logout } from "./api.js";

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
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
       </span>`
    : `<a class="brand" href="index.html">QuizVerse</a>
       <span class="account-links">
         <a href="practice.html">Practice</a>
         <a href="login.html?next=${next}">Log in</a>
         <a class="account-primary" href="register.html?next=${next}">Sign up</a>
       </span>`;
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

renderAccountBar();
