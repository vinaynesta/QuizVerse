// Profile page: edit name and email, change password, delete the account.
import { changePassword, currentUser, deleteAccount, updateProfile } from "./api.js";

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

  const passwordForm = $("password-form");
  handle(passwordForm, async (values) => {
    await changePassword(values.currentPassword, values.newPassword);
    passwordForm.reset();
    return "Password changed. Other devices have been logged out.";
  });

  const deleteForm = $("delete-form");
  handle(deleteForm, async (values) => {
    if (!window.confirm("Delete your account and all your quiz history? This can't be undone.")) return null;
    await deleteAccount(values.password);
    window.location.href = "index.html";
    return null;
  });
}

init();
