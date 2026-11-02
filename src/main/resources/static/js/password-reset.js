// Forgot password (forgot-password.html) and choosing the new password from the emailed link
// (reset-password.html?token=...).
import { confirmPasswordReset, requestPasswordReset } from "./api.js";

function wire(form, submit) {
  const errorBox = form.querySelector(".form-error");
  const successBox = form.querySelector(".form-success");
  const button = form.querySelector("button[type=submit]");
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!form.checkValidity()) {
      form.reportValidity();
      return;
    }
    errorBox.hidden = true;
    successBox.hidden = true;
    button.disabled = true;
    try {
      successBox.innerHTML = await submit(Object.fromEntries(new FormData(form)));
      successBox.hidden = false;
    } catch (error) {
      errorBox.textContent = error.message;
      errorBox.hidden = false;
      button.disabled = false;
    }
  });
}

const forgot = document.getElementById("forgot-form");
if (forgot) {
  wire(forgot, async ({ email }) => {
    const { message } = await requestPasswordReset(email);
    const div = document.createElement("div");
    div.textContent = message;
    return div.innerHTML;
  });
}

const reset = document.getElementById("reset-form");
if (reset) {
  const token = new URLSearchParams(window.location.search).get("token");
  // Take the token out of the address bar (and history) once it's read.
  history.replaceState(null, "", "reset-password.html");
  if (!token) {
    const errorBox = reset.querySelector(".form-error");
    errorBox.textContent = "This page needs the link from the reset email.";
    errorBox.hidden = false;
    reset.querySelector("button[type=submit]").disabled = true;
  }
  wire(reset, async ({ newPassword, repeat }) => {
    if (newPassword !== repeat) throw new Error("The two passwords don't match.");
    await confirmPasswordReset(token, newPassword);
    reset.querySelectorAll("input").forEach((input) => { input.disabled = true; });
    return 'Your password is changed. <a href="login.html">Log in</a> with the new one.';
  });
}
