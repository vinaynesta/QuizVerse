// "Sign in with Google" button (Google Identity Services). Shown only when the server has a Google client
// id configured (GET /api/status); otherwise nothing renders and the page works as before.
import { serverStatus } from "./api.js";

const GIS_SRC = "https://accounts.google.com/gsi/client";
let gisPromise = null;

function loadGis() {
  if (!gisPromise) {
    gisPromise = new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = GIS_SRC;
      script.async = true;
      script.onload = () => resolve(window.google);
      script.onerror = () => reject(new Error("Could not load Google sign-in"));
      document.head.appendChild(script);
    });
  }
  return gisPromise;
}

/**
 * Renders the button into `container` and calls onCredential(idToken) after the user picks an account.
 * Resolves to false (and leaves the container hidden) when Google sign-in is off or can't load.
 */
export async function renderGoogleButton(container, onCredential, { text = "continue_with" } = {}) {
  const status = await serverStatus();
  if (!container || !status || !status.googleClientId) return false;
  try {
    const google = await loadGis();
    google.accounts.id.initialize({
      client_id: status.googleClientId,
      callback: (response) => onCredential(response.credential),
      ux_mode: "popup",
    });
    container.hidden = false;
    google.accounts.id.renderButton(container.querySelector(".google-button-slot") || container, {
      theme: window.isDarkTheme && window.isDarkTheme() ? "filled_black" : "outline",
      size: "large", shape: "pill", text, width: 280,
    });
    return true;
  } catch (error) {
    console.error(error);
    return false;
  }
}
