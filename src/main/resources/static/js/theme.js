// Light/dark theme. Loaded as a plain script in <head> so the saved choice applies before the page
// draws (no flash of the wrong theme). With no saved choice the device setting decides (style.css).
(function () {
  const KEY = "quizverse.theme";
  const root = document.documentElement;

  function saved() {
    try {
      const value = localStorage.getItem(KEY);
      return value === "light" || value === "dark" ? value : null;
    } catch {
      return null;
    }
  }

  const choice = saved();
  if (choice) root.dataset.theme = choice;

  function isDark() {
    if (root.dataset.theme) return root.dataset.theme === "dark";
    return window.matchMedia("(prefers-color-scheme: dark)").matches;
  }

  /** Flips the theme and remembers it; account.js calls this from the button in the account bar. */
  window.toggleTheme = function () {
    const next = isDark() ? "light" : "dark";
    root.dataset.theme = next;
    try {
      localStorage.setItem(KEY, next);
    } catch {
      // Storage blocked: the theme still changes for this page.
    }
    return next;
  };
  window.isDarkTheme = isDark;
})();
