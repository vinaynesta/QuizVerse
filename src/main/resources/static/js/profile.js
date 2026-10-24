// Profile page: edit name and email, connect Google, change (or first set) the password, delete the account.
import { changePassword, currentUser, deleteAccount, linkGoogle, unlinkGoogle, updateProfile } from "./api.js";
import { renderGoogleButton } from "./google.js";

const $ = (id) => document.getElementById(id);

/** Wires a form: validates, disables the button while saving, and shows the error or success message. */
function handle(form, submit) {
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
    if (successBox) successBox.hidden = true;
    button.disabled = true;
    try {
      const message = await submit(Object.fromEntries(new FormData(form)));
      if (message && successBox) {
        successBox.textContent = message;
        successBox.hidden = false;
      }
    } catch (error) {
      errorBox.textContent = error.message;
      errorBox.hidden = false;
    } finally {
      button.disabled = false;
    }
  });
}

async function init() {
  const user = await currentUser();
  if (!user) {
    window.location.href = "login.html?next=profile.html";
    return;
  }
  const profileForm = $("profile-form");
  $("profile-username").value = user.username;
  profileForm.elements.displayName.value = user.displayName;
  profileForm.elements.email.value = user.email;

  handle(profileForm, async (values) => {
    const updated = await updateProfile(values);
    // The account bar shows the name too.
    const nameLink = document.querySelector(".account-name");
    if (nameLink) nameLink.textContent = updated.displayName;
    return "Saved.";
  });

  let hasPassword = user.hasPassword;
  const passwordForm = $("password-form");
  const showPasswordMode = () => {
    // A Google-only account sets its first password without a current one.
    $("password-title").textContent = hasPassword ? "Change password" : "Set a password";
    $("password-sub").hidden = hasPassword;
    $("current-password-field").hidden = !hasPassword;
    passwordForm.elements.currentPassword.required = hasPassword;
    $("password-button").textContent = hasPassword ? "Change password" : "Set password";
    $("delete-password-field").hidden = !hasPassword;
    deleteForm.elements.password.required = hasPassword;
    $("delete-username-field").hidden = hasPassword;
    deleteForm.elements.confirmUsername.required = !hasPassword;
  };
  handle(passwordForm, async (values) => {
    await changePassword(values.currentPassword || null, values.newPassword);
    passwordForm.reset();
    const first = !hasPassword;
    hasPassword = true;
    showPasswordMode();
    setupGoogle();
    return first ? "Password set. You can now also log in with it." : "Password changed. Other devices have been logged out.";
  });

  const deleteForm = $("delete-form");
  handle(deleteForm, async (values) => {
    if (!window.confirm("Delete your account and all your quiz history? This can't be undone.")) return null;
    await deleteAccount(values.password || null, values.confirmUsername || null);
    window.location.href = "index.html";
    return null;
  });
  showPasswordMode();

  // Google: connect with the button, or disconnect (only with a password, so the user can still log in).
  let googleLinked = user.googleLinked;
  const card = $("google-card");
  const errorBox = card.querySelector(".form-error");
  const successBox = card.querySelector(".form-success");
  const say = (box, message) => {
    errorBox.hidden = true;
    successBox.hidden = true;
    box.textContent = message;
    box.hidden = false;
  };
  let buttonShown = false;
  async function setupGoogle() {
    $("google-text").textContent = googleLinked
      ? hasPassword ? "Connected. You can log in with Google or your password."
        : "Connected. Google is how you log in; set a password below to be able to disconnect it."
      : "Connect your Google account to log in with one tap.";
    $("google-disconnect").hidden = !googleLinked || !hasPassword;
    $("google-connect").hidden = googleLinked;
    if (!googleLinked && !buttonShown) {
      buttonShown = await renderGoogleButton($("google-connect"), async (credential) => {
        try {
          await linkGoogle(credential);
          googleLinked = true;
          setupGoogle();
          say(successBox, "Google connected.");
        } catch (error) {
          say(errorBox, error.message);
        }
      }, { text: "continue_with" });
      // Google sign-in off on this server: the card only matters if the account already uses it.
      if (!buttonShown && !googleLinked) return;
    }
    card.hidden = false;
  }
  $("google-disconnect").addEventListener("click", async () => {
    try {
      await unlinkGoogle();
      googleLinked = false;
      await setupGoogle();
      say(successBox, "Google disconnected.");
    } catch (error) {
      say(errorBox, error.message);
    }
  });
  setupGoogle();
}

init();
