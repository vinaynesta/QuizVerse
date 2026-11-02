// Unsubscribe link from reminder and weekly emails (unsubscribe.html?token=...). A button press rather than
// unsubscribing on load, so link scanners in mail systems don't unsubscribe people by opening the link.
import { unsubscribe } from "./api.js";

const token = new URLSearchParams(window.location.search).get("token");
history.replaceState(null, "", "unsubscribe.html");
const button = document.getElementById("unsubscribe-button");
const errorBox = document.querySelector(".form-error");
const successBox = document.querySelector(".form-success");

if (!token) {
  errorBox.textContent = "This page needs the link from a QuizVerse email.";
  errorBox.hidden = false;
  button.disabled = true;
}

button.addEventListener("click", async () => {
  button.disabled = true;
  errorBox.hidden = true;
  try {
    const { message } = await unsubscribe(token);
    successBox.textContent = message;
    successBox.hidden = false;
    button.hidden = true;
    document.getElementById("unsubscribe-text").hidden = true;
  } catch (error) {
    errorBox.textContent = error.message;
    errorBox.hidden = false;
    button.disabled = false;
  }
});
