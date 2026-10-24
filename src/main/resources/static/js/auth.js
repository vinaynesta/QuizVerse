// Login and sign-up forms, plus "Continue with Google" on both. After success: save any quiz result finished
// while logged out, then go to ?next= (or the history page).
import { googleSignIn, login, register, safeNext, saveAttempt } from "./api.js";
import { renderGoogleButton } from "./google.js";
import { takePendingAttempt } from "./pending.js";

const next = safeNext(new URLSearchParams(window.location.search).get("next"), "history.html");
const form = document.getElementById("login-form") || document.getElementById("register-form");
const errorBox = document.getElementById("form-error");

// Keep ?next= when switching between the two forms.
const switchLink = document.getElementById("switch-link");
switchLink.href = `${switchLink.getAttribute("href")}?next=${encodeURIComponent(next)}`;

function showError(message) {
  errorBox.textContent = message;
  errorBox.hidden = false;
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!form.checkValidity()) {
    form.reportValidity();
    return;
  }
  const values = Object.fromEntries(new FormData(form));
  const button = form.querySelector("button[type=submit]");
  button.disabled = true;
  errorBox.hidden = true;
  try {
    if (form.id === "login-form") await login(values.login, values.password);
    else await register(values);
  } catch (error) {
    showError(error.message);
    button.disabled = false;
    return;
  }
  await afterSignIn();
});

renderGoogleButton(document.getElementById("google-signin"), async (credential) => {
  errorBox.hidden = true;
  try {
    await googleSignIn(credential);
  } catch (error) {
    showError(error.message);
    return;
  }
  await afterSignIn();
}, { text: form.id === "login-form" ? "signin_with" : "signup_with" });

async function afterSignIn() {
  const pending = takePendingAttempt();
  if (pending) {
    try {
      await saveAttempt(pending);
    } catch (error) {
      console.error("Could not save the earlier quiz result:", error);
    }
  }
  window.location.href = next;
}
