// Live contests. contests.html lists them; contests.html?id=N shows one contest: register before it starts,
// sit the paper while it's live (served by the server, shuffled for you), and see ranks once it ends.
import {
  contest, contestPaper, contestResults, createContest, currentUser, listContests, registerContest, submitContest,
  contestQuizzes,
} from "./api.js";
import { groupSetupHtml } from "./questions.js";

const $ = (id) => document.getElementById(id);
const id = Number(new URLSearchParams(window.location.search).get("id")) || null;
const PROGRESS_KEY = "quizverse.contestAnswers";

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

const fmt = (n) => Number(n.toFixed(2));
const fmtTime = (secs) => `${Math.floor(secs / 60)}m ${secs % 60}s`;
const when = (iso) => new Date(iso).toLocaleString(undefined, { weekday: "short", day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });
const STATE = { live: "Live now", upcoming: "Upcoming", finished: "Finished" };

function item(c) {
  const you = { registered: "Registered", started: "In progress", submitted: "Submitted" }[c.you] || "";
  return `<a class="practice-link contest-item ${c.state}" href="contests.html?id=${c.id}">
      <b>${escapeHtml(c.title)} <span class="challenge-tag">${STATE[c.state]}</span>${you ? ` <span class="challenge-tag">${you}</span>` : ""}</b>
      <span>${when(c.startsAt)} · ${c.questions} questions, ${fmt(c.maxMarks)} marks, ${c.minutes} min · ${c.registered} registered</span>
    </a>`;
}

async function showList(user) {
  const list = await listContests();
  $("contest-items").innerHTML = list.length ? list.map(item).join("")
    : '<p class="card-sub">No contests scheduled right now. Check back soon.</p>';
  if (user && user.admin) setupAdmin();
}

async function setupAdmin() {
  const form = $("contest-admin");
  form.hidden = false;
  const keys = await contestQuizzes();
  form.elements.quizKey.innerHTML = keys.map((k) => `<option>${escapeHtml(k)}</option>`).join("");
  const errorBox = form.querySelector(".form-error");
  const successBox = form.querySelector(".form-success");
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!form.checkValidity()) {
      form.reportValidity();
      return;
    }
    errorBox.hidden = true;
    successBox.hidden = true;
    try {
      const v = Object.fromEntries(new FormData(form));
      await createContest({ title: v.title, quizKey: v.quizKey, startsAt: new Date(v.startsAt).toISOString(), minutes: Number(v.minutes) || 0 });
      successBox.textContent = "Scheduled.";
      successBox.hidden = false;
      form.reset();
      showList({ admin: false });
    } catch (error) {
      errorBox.textContent = error.message;
      errorBox.hidden = false;
    }
  });
}

// ---------------------------------------------------------------- one contest

let paper = null;
let chosen = [];
let timerId = null;

function say(message) {
  $("contest-error").textContent = message;
  $("contest-error").hidden = !message;
}

async function showContest(user) {
  $("contest-list").hidden = true;
  $("contest-detail").hidden = false;
  let c;
  try {
    c = await contest(id);
  } catch (error) {
    $("contest-info").textContent = error.message;
    return;
  }
  document.title = `QuizVerse - ${c.title}`;
  $("contest-title").textContent = c.title;
  $("contest-info").innerHTML = `<b>${STATE[c.state]}</b> · ${when(c.startsAt)} to ${when(c.endsAt)}<br>
    ${c.questions} questions, ${fmt(c.maxMarks)} marks, ${c.minutes} minutes from when you open the paper (ends at the contest's end at the latest).
    Each wrong answer costs a quarter of the question's marks. ${c.registered} registered.`;

  if (c.state === "finished") {
    showResults();
    return;
  }
  if (!user) {
    $("contest-status").innerHTML = `<a href="login.html?next=${encodeURIComponent(`contests.html?id=${id}`)}">Log in</a> to take part.`;
    return;
  }
  if (c.you === "submitted") {
    $("contest-status").textContent = "You've submitted. Results and your rank appear here when the contest ends.";
    return;
  }
  if (!c.you) {
    const button = $("contest-register");
    button.hidden = false;
    button.addEventListener("click", async () => {
      button.disabled = true;
      try {
        await registerContest(id);
        showContest(user);
        button.hidden = true;
      } catch (error) {
        say(error.message);
        button.disabled = false;
      }
    });
    return;
  }
  if (c.state === "upcoming") {
    $("contest-status").textContent = "You're registered. Come back at the start time; this page starts the countdown.";
    const ms = new Date(c.startsAt) - Date.now();
    if (ms < 6 * 60 * 60 * 1000) setTimeout(() => window.location.reload(), Math.max(1000, ms + 1000));
    return;
  }
  const start = $("contest-start");
  start.hidden = false;
  start.textContent = c.you === "started" ? "Continue the paper" : "Start the paper";
  start.addEventListener("click", openPaper);
}

async function openPaper() {
  say("");
  try {
    paper = await contestPaper(id);
  } catch (error) {
    say(error.message);
    return;
  }
  $("contest-detail").hidden = true;
  $("contest-paper").hidden = false;
  $("paper-title").textContent = paper.title;
  chosen = loadAnswers() || paper.questions.map(() => null);
  let section = null;
  $("paper-questions").innerHTML = paper.questions.map((q, n) => {
    const head = q.section !== section ? `<li class="contest-section">${escapeHtml((section = q.section))}</li>` : "";
    const setup = q.group && (n === 0 || !paper.questions[n - 1].group || paper.questions[n - 1].group.id !== q.group.id)
      ? groupSetupHtml(q) : "";
    return `${head}<li class="contest-q" data-n="${n}">
        ${setup}<p><b>${n + 1}.</b> ${escapeHtml(q.question)}${q.needsReview
          ? ' <span class="badge needs-review" title="New content, still being checked">Draft</span>' : ""}</p>
        <div class="options">${q.options.map((o, k) =>
          `<div class="option${chosen[n] === k ? " selected" : ""}" data-k="${k}" role="button" tabindex="0">${escapeHtml(o)}</div>`).join("")}</div>
      </li>`;
  }).join("");
  $("paper-questions").querySelectorAll(".contest-q").forEach((li) => {
    const n = Number(li.dataset.n);
    li.querySelectorAll(".option").forEach((opt) => {
      const pick = () => {
        const k = Number(opt.dataset.k);
        chosen[n] = chosen[n] === k ? null : k;
        li.querySelectorAll(".option").forEach((o, j) => o.classList.toggle("selected", chosen[n] === j));
        saveAnswers();
      };
      opt.addEventListener("click", pick);
      opt.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); pick(); } });
    });
  });
  $("paper-submit").onclick = () => {
    const left = chosen.filter((x) => x === null).length;
    if (window.confirm(`${left ? `${left} unanswered. ` : ""}Submit? You can't change your answers afterwards.`)) submit();
  };
  tick();
  timerId = setInterval(tick, 500);
}

function tick() {
  const left = Math.max(0, Math.ceil((new Date(paper.deadline) - Date.now()) / 1000));
  $("paper-timer").textContent = `${String(Math.floor(left / 60)).padStart(2, "0")}:${String(left % 60).padStart(2, "0")}`;
  if (left === 0) submit();
}

let submitting = false;
async function submit() {
  if (submitting) return;
  submitting = true;
  clearInterval(timerId);
  try {
    const res = await submitContest(id, chosen);
    clearAnswers();
    $("contest-paper").hidden = true;
    $("contest-detail").hidden = false;
    $("contest-start").hidden = true;
    $("contest-status").textContent = res.message;
  } catch (error) {
    submitting = false;
    window.alert(`${error.message} Your answers are kept on this device; try submitting again.`);
    if (new Date(paper.deadline) > Date.now()) timerId = setInterval(tick, 500);
  }
}

function saveAnswers() {
  try { localStorage.setItem(PROGRESS_KEY, JSON.stringify({ id, chosen })); } catch { /* storage blocked */ }
}
function loadAnswers() {
  try {
    const saved = JSON.parse(localStorage.getItem(PROGRESS_KEY));
    return saved && saved.id === id && saved.chosen.length === paper.questions.length ? saved.chosen : null;
  } catch { return null; }
}
function clearAnswers() {
  try { localStorage.removeItem(PROGRESS_KEY); } catch { /* ignore */ }
}

async function showResults() {
  let r;
  try {
    r = await contestResults(id);
  } catch (error) {
    $("contest-status").textContent = error.message;
    return;
  }
  $("contest-results").hidden = false;
  $("results-you").innerHTML = r.you
    ? `You ranked <b>#${r.you.rank}</b> of ${r.participants} with <b>${fmt(r.you.score)}</b> / ${fmt(r.contest.maxMarks)}: better than <b>${r.yourPercentile}%</b> of participants.`
    : `${r.participants} participant${r.participants === 1 ? "" : "s"}.`;
  $("results-rows").innerHTML = r.top.map((x) => `<tr class="${x.you ? "challenge-you" : ""}">
      <td class="num">${x.rank}</td><td>${escapeHtml(x.name)}${x.you ? ' <span class="challenge-tag">you</span>' : ""}</td>
      <td class="num"><b>${fmt(x.score)}</b></td><td class="num">${x.correct}</td><td class="num">${x.wrong}</td>
      <td class="num">${fmtTime(x.secondsUsed)}</td></tr>`).join("");
}

async function init() {
  const user = await currentUser();
  if (id) showContest(user); else showList(user);
}

init();
