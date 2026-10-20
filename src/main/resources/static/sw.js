// Service worker: lets the site install as an app and keeps practice working without internet.
//  - Pages, CSS and JS: network first (so a deploy shows up straight away), cache if offline or slow.
//  - Question data and images: from the cache, refreshed in the background; quizzes opened once work offline.
//  - /api/: never cached. Account data must not stay on a shared phone after logout.
// Bump VERSION only to force-clear old caches; normal deploys don't need it.
const VERSION = "v1";
const CACHE = `quizverse-${VERSION}`;
const NETWORK_TIMEOUT_MS = 3000;

const SHELL = [
  "index.html", "exam.html", "quiz.html", "history.html", "practice.html", "login.html", "register.html",
  "profile.html", "reports.html",
  "css/style.css", "css/quiz.css",
  "js/theme.js", "js/api.js", "js/account.js", "js/auth.js", "js/exam.js", "js/history.js", "js/home.js",
  "js/pending.js", "js/practice.js", "js/profile.js", "js/questions.js", "js/quiz.js", "js/reports.js", "js/share.js",
  "manifest.webmanifest", "icons/icon.svg", "icons/icon-192.png", "icons/icon-512.png", "icons/apple-touch-icon.png",
  "data/index.json", "data/backgrounds.json", "data/topics.json", "data/question-topics.json",
  "images/feels like we just met yesterday.jpeg",
];

self.addEventListener("install", (event) => {
  // One missing file shouldn't stop the whole install, so files are added one by one.
  event.waitUntil(
    caches.open(CACHE).then((cache) => Promise.all(SHELL.map((path) => cache.add(path).catch(() => {}))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k.startsWith("quizverse-") && k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

function networkFirst(request, cacheKey) {
  return new Promise((resolve) => {
    let settled = false;
    const fromCache = () => caches.match(cacheKey, { ignoreSearch: true }).then((hit) => hit || caches.match("index.html"));
    const timer = setTimeout(() => {
      fromCache().then((hit) => {
        if (hit && !settled) {
          settled = true;
          resolve(hit);
        }
      });
    }, NETWORK_TIMEOUT_MS);
    fetch(request)
      .then((response) => {
        clearTimeout(timer);
        if (response.ok) {
          const copy = response.clone();
          caches.open(CACHE).then((cache) => cache.put(cacheKey, copy));
        }
        if (!settled) {
          settled = true;
          resolve(response);
        }
      })
      .catch(() => {
        clearTimeout(timer);
        fromCache().then((hit) => {
          if (!settled) {
            settled = true;
            resolve(hit || new Response("You're offline and this page hasn't been saved yet.", {
              status: 503, headers: { "Content-Type": "text/plain; charset=utf-8" },
            }));
          }
        });
      });
  });
}

function staleWhileRevalidate(request) {
  return caches.open(CACHE).then((cache) =>
    cache.match(request).then((hit) => {
      const update = fetch(request)
        .then((response) => {
          if (response.ok) cache.put(request, response.clone());
          return response;
        })
        .catch(() => hit || Response.error());
      return hit || update;
    })
  );
}

self.addEventListener("fetch", (event) => {
  const { request } = event;
  const url = new URL(request.url);
  if (request.method !== "GET" || url.origin !== self.location.origin) return;
  if (url.pathname.startsWith("/api/")) return; // straight to the network, never stored

  if (url.pathname.startsWith("/data/") || url.pathname.startsWith("/images/")) {
    event.respondWith(staleWhileRevalidate(request));
  } else if (request.mode === "navigate") {
    // quiz.html?exam=... and quiz.html share one cached copy of the page.
    event.respondWith(networkFirst(request, url.pathname === "/" ? "index.html" : url.pathname.slice(1)));
  } else {
    event.respondWith(networkFirst(request, url.pathname.slice(1)));
  }
});
