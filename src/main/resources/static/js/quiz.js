import {
  addBookmark, currentUser, listBookmarks, mistakes, removeBookmark, reportQuestion, saveAttempt,
} from "./api.js";
import { storePendingAttempt } from "./pending.js";
import { loadTopicPool, questionsById, shuffled } from "./questions.js";

// Two kinds of quiz share this page:
//  - exam quizzes from index.json: quiz.html?exam=..&stage=..&section=..&level=..&quiz=N
//  - practice sets: quiz.html?practice=revise | bookmarks | custom (custom also has topics, level, count, minutes)
const params = new URLSearchParams(window.location.search);
const practiceKind = params.get("practice");
const examId = params.get("exam");
const stageId = params.get("stage");
const sectionId = params.get("section");
const levelId = params.get("level");
let quizIdx = Number(params.get("quiz") || 0);

let index = null;
let exam = null;
let stage = null;
let section = null;
let level = null;
// For practice sets: { title, description, minutes (0 = untimed), questions }
let practice = null;

let questions = [];
let answers = []; // selected option index per question, or null
let marked = []; // "mark for review" flags (exam mode)
let visited = [];
let seconds = []; // time spent on each question
let current = 0;
let timerId = null;
let remaining = 0;
let endAt = 0; // epoch ms when the timer runs out
let shownAt = 0; // when the current question was shown, for seconds[]
let finished = false;
let bookmarked = new Set();
let user = null;

const $ = (id) => document.getElementById(id);
const minutes = () => (practice ? practice.minutes : section && section.minutes) || 0;
const timed = () => minutes() > 0;

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

let backgrounds = [];
let lastBackground = null;

// Quiz pages get a random backdrop; the home and exam pages keep the default image.
function pickBackground() {
  if (backgrounds.length === 0) return;
  const choices = backgrounds.length > 1 ? backgrounds.filter((b) => b !== lastBackground) : backgrounds;
  lastBackground = choices[Math.floor(Math.random() * choices.length)];
  const href = new URL(`images/${encodeURIComponent(lastBackground)}`, document.baseURI).href;
  document.documentElement.style.setProperty("--bg-image", `url("${href}")`);
}

async function loadBackgrounds() {
  try {
    backgrounds = await (await fetch("data/backgrounds.json")).json();
    pickBackground();
  } catch (error) {
    console.error("Backgrounds unavailable, using the default:", error);
  }
}

// ---------- Saving progress in this browser, so a refresh doesn't lose a running quiz ----------

const quizKey = () =>
  practice ? `practice:${practiceKind}:${params.toString()}` : `${examId}/${stageId}/${sectionId}/${levelId}/${quizIdx}`;
const PROGRESS_KEY = "quizverse.inProgress";

function saveProgress() {
  if (finished) return;
  try {
    localStorage.setItem(PROGRESS_KEY, JSON.stringify({
      key: quizKey(), endAt, current, answers, marked, visited, seconds,
      // Practice sets are random, so keep the exact questions to resume the same set.
      questionIds: practice ? questions.map((q) => q.id) : null,
    }));
  } catch {
    // Storage blocked: the quiz still works, it just can't be resumed after a refresh.
  }
}

function loadProgress() {
  try {
    const saved = JSON.parse(localStorage.getItem(PROGRESS_KEY));
    return saved && saved.key === quizKey() ? saved : null;
  } catch {
    return null;
  }
}

function clearProgress() {
  try {
    localStorage.removeItem(PROGRESS_KEY);
  } catch {
    // ignore
  }
}

// ---------- Loading ----------

async function init() {
  loadBackgrounds();
  user = await currentUser();
  if (user) {
    listBookmarks()
      .then((list) => { bookmarked = new Set(list.map((b) => b.questionId)); })
      .catch((error) => console.error("Could not load bookmarks:", error));
  }
  try {
    if (practiceKind) {
      practice = await loadPractice();
      if (!practice) return;
    } else {
      index = await (await fetch("data/index.json")).json();
      exam = index.exams.find((e) => e.id === examId);
      stage = exam && exam.stages.find((st) => st.id === stageId);
      section = stage && stage.sections.find((s) => s.id === sectionId);
      level = section && section.levels.find((l) => l.id === levelId);
      if (!level || !level.quizzes[quizIdx]) {
        showStartError("Quiz not found");
        return;
      }
      negativeMarking = index.negativeMarking;
    }
    renderStart();
  } catch (error) {
    console.error("Error loading quiz:", error);
    showStartError("Could not load this quiz. Please try again.");
  }
}

function showStartError(message, html = "") {
  $("start-title").textContent = message;
  $("start-info").innerHTML = html;
  $("start-button").style.display = "none";
}

const PRACTICE_MAX = 50;

/** Builds a practice set, or shows why it can't and returns null. */
async function loadPractice() {
  if (practiceKind === "custom") return loadCustom();
  if (practiceKind !== "revise" && practiceKind !== "bookmarks") {
    showStartError("Quiz not found");
    return null;
  }
  if (!user) {
    const next = encodeURIComponent(`quiz.html?practice=${practiceKind}`);
    showStartError("Log in to practise", `<a href="login.html?next=${next}">Log in</a> to see your ${
      practiceKind === "revise" ? "mistakes" : "bookmarks"}.`);
    return null;
  }
  const resume = loadProgress();
  let ids;
  if (resume && resume.questionIds) {
    ids = resume.questionIds;
  } else if (practiceKind === "revise") {
    ids = shuffled((await mistakes()).map((m) => m.questionId)).slice(0, PRACTICE_MAX);
  } else {
    ids = shuffled((await listBookmarks()).map((b) => b.questionId)).slice(0, PRACTICE_MAX);
  }
  const qs = await questionsById(ids);
  if (qs.length === 0) {
    showStartError(practiceKind === "revise" ? "No mistakes to revise" : "No bookmarks yet",
      practiceKind === "revise"
        ? 'Questions you get wrong or skip show up here until you get them right. <a href="index.html">Take a quiz</a>.'
        : 'Tap the ☆ on any question to save it here. <a href="index.html">Take a quiz</a>.');
    return null;
  }
  return practiceKind === "revise"
    ? { title: "Revise mistakes", minutes: 0, questions: qs,
        description: `${qs.length} question${qs.length > 1 ? "s" : ""} you got wrong or skipped. Get one right and it leaves this list.` }
    : { title: "Bookmarked questions", minutes: 0, questions: qs,
        description: `${qs.length} saved question${qs.length > 1 ? "s" : ""}, untimed with instant feedback.` };
}

const LEVELS = ["easy", "medium", "hard", "very-hard", "mixed"];

async function loadCustom() {
  // topics=quant/trains,reasoning/syllogism  level=any|easy|medium|hard  count=1-50  minutes=0-120
  const topics = (params.get("topics") || "").split(",").filter((t) => /^[a-z]+\/[a-z0-9-]+$/.test(t)).slice(0, 20);
  const lvl = params.get("level") || "any";
  const count = Math.min(PRACTICE_MAX, Math.max(1, Number(params.get("count")) || 20));
  const mins = Math.min(120, Math.max(0, Number(params.get("minutes")) || 0));
  if (topics.length === 0 || (lvl !== "any" && !LEVELS.includes(lvl))) {
    showStartError("Quiz not found", '<a href="practice.html">Build a custom quiz</a>.');
    return null;
  }
  const resume = loadProgress();
  let qs;
  if (resume && resume.questionIds) {
    qs = await questionsById(resume.questionIds);
  } else {
    const pools = await Promise.all(topics.map((t) => loadTopicPool(...t.split("/")).catch(() => [])));
    const pool = pools.flat().filter((q) => lvl === "any" || q.level === lvl);
    qs = shuffled(pool).slice(0, count);
  }
  if (qs.length === 0) {
    showStartError("No questions match", '<a href="practice.html">Change your choices</a>.');
    return null;
  }
  return {
    title: "Custom quiz",
    minutes: mins,
    questions: qs,
    description: `${qs.length} question${qs.length > 1 ? "s" : ""}${lvl === "any" ? "" : `, ${lvl}`}${
      qs.length < count ? ` (only ${qs.length} matched)` : ""}.`,
  };
}

let negativeMarking = 0.25;

function quizTitle() {
  if (practice) return practice.title;
  // Skip parts already contained in the earlier ones ("Java Practice" + "Practice" + "Java").
  const parts = [];
  [exam.name, stage.name, section.name, level.quizzes[quizIdx].title].forEach((part) => {
    if (!parts.join(" ").toLowerCase().includes(part.toLowerCase())) parts.push(part);
  });
  return parts.join(" - ");
}

function renderStart() {
  document.title = `QuizVerse - ${quizTitle()}`;
  $("start-title").textContent = quizTitle();
  let info;
  if (practice) {
    info = timed()
      ? `${practice.description} ${practice.minutes} minutes, ${negativeMarking} negative marking per wrong answer.`
      : `${practice.description}`;
  } else {
    info = timed()
      ? `${section.questions} questions, ${section.marks} marks, ${section.minutes} minutes. Each wrong answer costs ${negativeMarking} of the question's marks.`
      : "Untimed practice. You see the answer after each question.";
  }
  $("start-info").textContent = info;
  const resume = loadProgress();
  const resumable = resume && (!timed() || resume.endAt > Date.now());
  $("start-button").textContent = resumable ? "Resume Quiz" : "Start Quiz";
  $("start-button").style.display = "";
  $("start-screen").style.display = "block";
  $("quiz-screen").style.display = "none";
  // A timed quiz whose time ran out while the page was closed is submitted as it stood.
  if (resume && timed() && resume.endAt <= Date.now()) startQuiz();
}

// ---------- Running a quiz ----------

async function startQuiz() {
  $("loading-spinner").style.display = "block";
  try {
    questions = practice ? practice.questions : await (await fetch(`data/${level.quizzes[quizIdx].file}`)).json();
  } catch (error) {
    console.error("Error fetching questions:", error);
    $("loading-spinner").style.display = "none";
    return;
  }
  const resume = loadProgress();
  const ok = resume && resume.answers && resume.answers.length === questions.length;
  answers = ok ? resume.answers : questions.map(() => null);
  marked = ok && resume.marked ? resume.marked : questions.map(() => false);
  visited = ok && resume.visited ? resume.visited : questions.map(() => false);
  seconds = ok && resume.seconds ? resume.seconds : questions.map(() => 0);
  current = ok ? Math.min(resume.current || 0, questions.length - 1) : 0;
  finished = false;
  $("loading-spinner").style.display = "none";
  $("start-screen").style.display = "none";
  $("quiz-screen").style.display = "flex";
  $("new-quiz-button").style.display = "none";
  $("back-link-button").style.display = "none";
  $("quiz-label").textContent = quizTitle();
  $("palette-toggle").style.display = timed() ? "" : "none";
  clearInterval(timerId);
  if (timed()) {
    endAt = ok && resume.endAt ? resume.endAt : Date.now() + minutes() * 60 * 1000;
    if (endAt <= Date.now()) {
      finish();
      return;
    }
    updateTimer();
    timerId = setInterval(updateTimer, 500);
  } else {
    endAt = 0;
    $("timer").textContent = "";
  }
  saveProgress();
  showQuestion();
}

// The clock is worked out from endAt each tick, so slowed timers in background tabs can't stretch it.
function updateTimer() {
  remaining = Math.max(0, Math.ceil((endAt - Date.now()) / 1000));
  const m = String(Math.floor(remaining / 60)).padStart(2, "0");
  const s = String(remaining % 60).padStart(2, "0");
  const el = $("timer");
  el.textContent = `${m}:${s}`;
  el.classList.toggle("warning", remaining <= 60);
  if (remaining <= 0) finish();
}

/** Adds the time spent on the current question since it was shown (only while the tab is visible). */
function stopClock() {
  if (shownAt && !finished) seconds[current] += Math.round((Date.now() - shownAt) / 1000);
  shownAt = 0;
}

function startClock() {
  shownAt = document.hidden ? 0 : Date.now();
}

document.addEventListener("visibilitychange", () => {
  if (finished || !questions.length || $("quiz-screen").style.display === "none") return;
  if (document.hidden) {
    stopClock();
    saveProgress();
  } else {
    startClock();
    if (timed()) updateTimer();
  }
});

function optionsHtml(q, selected) {
  return q.options
    .map(
      (opt, i) =>
        `<div class="option${selected === i ? " selected" : ""}" data-index="${i}" role="button" tabindex="0">${escapeHtml(opt)}</div>`
    )
    .join("");
}

function questionToolsHtml(q) {
  const star = user
    ? `<button type="button" class="tool-button bookmark${bookmarked.has(q.id) ? " on" : ""}" id="bookmark-button"
         aria-pressed="${bookmarked.has(q.id)}" title="Bookmark (B)">${bookmarked.has(q.id) ? "★ Saved" : "☆ Save"}</button>`
    : "";
  return `<span class="question-tools">${star}
    <button type="button" class="tool-button" id="report-button" title="Report a problem with this question">⚑ Report</button></span>`;
}

function showQuestion() {
  stopClock();
  const q = questions[current];
  visited[current] = true;
  $("question-container").innerHTML = `
    <div class="question">
      <div class="question-head">
        <h3>Question ${current + 1} of ${questions.length}${marked[current] ? ' <span class="badge marked">Marked</span>' : ""}</h3>
        ${questionToolsHtml(q)}
      </div>
      <p>${escapeHtml(q.question)}</p>
      <div class="options">${optionsHtml(q, answers[current])}</div>
    </div>`;
  $("result-container").style.display = "none";
  $("explanation-container").style.display = "none";
  $("next-button").style.display = "none";
  $("prev-button").style.display = "none";
  $("mark-button").style.display = "none";
  $("finish-button").style.display = "none";
  document.querySelectorAll(".option").forEach((opt) => {
    opt.addEventListener("click", () => selectOption(Number(opt.dataset.index)));
    opt.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        selectOption(Number(opt.dataset.index));
      }
    });
  });
  const star = $("bookmark-button");
  if (star) star.addEventListener("click", toggleBookmark);
  $("report-button").addEventListener("click", openReport);
  if (timed()) {
    showNav();
  } else if (answers[current] !== null) {
    revealPractice();
  }
  renderPalette();
  startClock();
  saveProgress();
}

function showNav() {
  const last = current === questions.length - 1;
  $("prev-button").style.display = current > 0 ? "block" : "none";
  $("mark-button").textContent = marked[current] ? "Unmark" : "Mark for Review & Next";
  $("mark-button").style.display = "block";
  $("next-button").textContent = "Save & Next";
  $("next-button").style.display = last ? "none" : "block";
  $("finish-button").textContent = "Submit Quiz";
  $("finish-button").style.display = "block";
}

// ---------- Question palette (exam mode) ----------

function paletteState(i) {
  const answered = answers[i] !== null;
  if (marked[i]) return answered ? "marked-answered" : "marked";
  if (answered) return "answered";
  return visited[i] ? "skipped" : "not-visited";
}

function renderPalette() {
  const panel = $("palette");
  if (!timed()) {
    panel.style.display = "none";
    return;
  }
  panel.style.display = "";
  const counts = { answered: 0, skipped: 0, "not-visited": 0, marked: 0, "marked-answered": 0 };
  const buttons = questions.map((_, i) => {
    const state = paletteState(i);
    counts[state] += 1;
    return `<button type="button" class="pal ${state}${i === current ? " current" : ""}" data-q="${i}"
      aria-label="Question ${i + 1}, ${state.replace("-", " ")}">${i + 1}</button>`;
  });
  $("palette-grid").innerHTML = buttons.join("");
  $("palette-legend").innerHTML = [
    ["answered", "Answered"], ["skipped", "Not answered"], ["not-visited", "Not visited"],
    ["marked", "Marked"], ["marked-answered", "Answered & marked"],
  ].map(([k, label]) => `<span><i class="pal ${k}"></i>${counts[k]} ${label}</span>`).join("");
  $("palette-grid").querySelectorAll(".pal").forEach((b) =>
    b.addEventListener("click", () => {
      goTo(Number(b.dataset.q));
      document.body.classList.remove("palette-open");
    })
  );
}

function goTo(i) {
  if (finished || i < 0 || i >= questions.length || i === current) return;
  stopClock();
  current = i;
  showQuestion();
}

// ---------- Answering ----------

function selectOption(i) {
  if (finished) return;
  const q = questions[current];
  if (i < 0 || i >= q.options.length) return;
  if (timed()) {
    // Exam mode: answers can be changed; clicking the chosen option again clears it.
    answers[current] = answers[current] === i ? null : i;
    document.querySelectorAll(".option").forEach((opt, k) =>
      opt.classList.toggle("selected", answers[current] === k)
    );
    renderPalette();
    saveProgress();
    return;
  }
  if (answers[current] !== null) return;
  answers[current] = i;
  revealPractice();
  saveProgress();
}

function revealPractice() {
  const q = questions[current];
  const chosen = answers[current];
  const opts = document.querySelectorAll(".option");
  opts.forEach((opt, k) => {
    opt.classList.add("disabled");
    opt.style.pointerEvents = "none";
    opt.tabIndex = -1;
    if (k === q.answer) opt.classList.add("correct");
    else if (k === chosen) opt.classList.add("incorrect");
  });
  const ok = chosen === q.answer;
  showResult(ok ? "Correct!" : `Incorrect. The correct answer is: ${q.options[q.answer]}`, ok);
  if (q.explanation) showExplanation(q.explanation);
  $("prev-button").style.display = current > 0 ? "block" : "none";
  if (current < questions.length - 1) {
    $("next-button").textContent = "Next Question";
    $("next-button").style.display = "block";
  } else {
    $("finish-button").textContent = "Finish Quiz";
    $("finish-button").style.display = "block";
  }
}

function showResult(message, isCorrect) {
  const el = $("result-container");
  el.textContent = message;
  el.className = "result " + (isCorrect ? "correct-answer" : "incorrect-answer");
  el.style.display = "block";
}

function showExplanation(message) {
  const el = $("explanation-container");
  el.textContent = `Explanation: ${message}`;
  el.style.display = "block";
}

function showNextQuestion() {
  if (current < questions.length - 1) goTo(current + 1);
}

function showPrevQuestion() {
  goTo(current - 1);
}

function toggleMark() {
  if (finished || !timed()) return;
  marked[current] = !marked[current];
  if (marked[current] && current < questions.length - 1) {
    goTo(current + 1);
  } else {
    showQuestion();
  }
}

// ---------- Bookmarks and reports ----------

async function toggleBookmark() {
  const q = questions[current];
  const button = $("bookmark-button");
  if (!button || button.disabled) return;
  const on = !bookmarked.has(q.id);
  button.disabled = true;
  try {
    if (on) {
      await addBookmark(q.id);
      bookmarked.add(q.id);
    } else {
      await removeBookmark(q.id);
      bookmarked.delete(q.id);
    }
  } catch (error) {
    window.alert(error.message);
  }
  // The user may have moved on while the request ran.
  const still = $("bookmark-button");
  if (still) {
    const has = bookmarked.has(questions[current].id);
    still.disabled = false;
    still.classList.toggle("on", has);
    still.setAttribute("aria-pressed", String(has));
    still.textContent = has ? "★ Saved" : "☆ Save";
  }
}

let reportQuestionId = null;

function openReport(questionId) {
  const id = typeof questionId === "string" ? questionId : questions[current].id;
  if (!user) {
    window.alert("Log in to report a question.");
    return;
  }
  reportQuestionId = id;
  $("report-form").reset();
  $("report-status").textContent = "";
  $("report-dialog").showModal();
}

async function submitReport(event) {
  event.preventDefault();
  if (event.submitter && event.submitter.value === "cancel") {
    $("report-dialog").close();
    return;
  }
  const form = new FormData($("report-form"));
  const send = $("report-send");
  send.disabled = true;
  try {
    await reportQuestion(reportQuestionId, form.get("reason"), form.get("note"));
    $("report-dialog").close();
  } catch (error) {
    $("report-status").textContent = error.message;
  } finally {
    send.disabled = false;
  }
}

// ---------- Finishing ----------

function onFinishClick() {
  if (timed()) {
    const left = answers.filter((a) => a === null).length;
    const flagged = marked.filter(Boolean).length;
    const notes = [];
    if (left > 0) notes.push(`${left} question(s) unanswered`);
    if (flagged > 0) notes.push(`${flagged} marked for review`);
    if (notes.length && !window.confirm(`${notes.join(", ")}. Submit anyway?`)) return;
  }
  finish();
}

function finish() {
  if (finished) return;
  stopClock();
  finished = true;
  clearInterval(timerId);
  clearProgress();
  document.body.classList.remove("palette-open");
  const total = questions.length;
  const correct = questions.filter((q, i) => answers[i] === q.answer).length;
  const attempted = answers.filter((a) => a !== null).length;
  const wrong = attempted - correct;
  // Marks can differ from the question count (e.g. 40 questions carrying 60 marks).
  const perQ = timed() && section ? section.marks / total : 1;
  const maxMarks = timed() && section ? section.marks : total;
  const score = correct * perQ - (timed() ? wrong * perQ * negativeMarking : 0);
  const usedSecs = timed() ? Math.min(minutes() * 60, Math.max(0, minutes() * 60 - remaining)) : 0;
  const review = questions
    .map((q, i) => {
      const a = answers[i];
      const kind = a === null ? "skipped" : a === q.answer ? "right" : "wrong";
      const label = { skipped: "Not attempted", right: "Correct", wrong: "Wrong" }[kind];
      const star = user
        ? `<button type="button" class="tool-button bookmark review-bookmark${bookmarked.has(q.id) ? " on" : ""}" data-id="${q.id}">${
          bookmarked.has(q.id) ? "★" : "☆"}</button>`
        : "";
      return `
      <div class="review-item ${kind}">
        <div class="review-head"><strong>Q${i + 1}</strong>
          <span class="review-tools"><span class="review-time">${seconds[i]}s</span>${star}
            <button type="button" class="tool-button review-report" data-id="${q.id}" title="Report">⚑</button>
            <span class="badge ${kind}">${label}</span></span></div>
        <p class="review-q">${escapeHtml(q.question)}</p>
        <p><span class="review-key">Your answer</span> ${a === null ? "&mdash;" : escapeHtml(q.options[a])}</p>
        <p><span class="review-key">Correct answer</span> ${escapeHtml(q.options[q.answer])}</p>
        ${q.explanation ? `<p class="review-expl">${escapeHtml(q.explanation)}</p>` : ""}
      </div>`;
    })
    .join("");
  const pct = Math.max(0, Math.min(100, (score / maxMarks) * 100));
  const mins = Math.floor(usedSecs / 60);
  const avgSecs = attempted ? Math.round(
    questions.reduce((sum, _, i) => sum + (answers[i] !== null ? seconds[i] : 0), 0) / attempted) : 0;
  $("question-container").innerHTML = `
    <div class="result-card">
      <h2>Quiz Complete!</h2>
      <div class="score-ring" style="--pct:${pct.toFixed(1)}">
        <div class="score-inner"><span class="score-num">${Number(score.toFixed(2))}</span><span class="score-max">out of ${maxMarks}</span></div>
      </div>
      ${timed() ? '<p class="score-note">Negative marking applied</p>' : ""}
      <div class="stat-row">
        <div class="stat right"><b>${correct}</b><span>Correct</span></div>
        <div class="stat wrong"><b>${wrong}</b><span>Wrong</span></div>
        <div class="stat skipped"><b>${total - attempted}</b><span>Not attempted</span></div>
        <div class="stat"><b>${attempted ? ((correct / attempted) * 100).toFixed(0) : 0}%</b><span>Accuracy</span></div>
        ${timed() ? `<div class="stat"><b>${mins}m ${usedSecs % 60}s</b><span>Time used</span></div>` : ""}
        <div class="stat"><b>${avgSecs}s</b><span>Avg per answer</span></div>
      </div>
      <p id="save-status" class="save-status"></p>
      <details class="review"><summary>Review answers</summary>${review}</details>
    </div>`;
  $("question-container").querySelectorAll(".review-bookmark").forEach((b) =>
    b.addEventListener("click", () => toggleReviewBookmark(b)));
  $("question-container").querySelectorAll(".review-report").forEach((b) =>
    b.addEventListener("click", () => openReport(b.dataset.id)));
  $("result-container").style.display = "none";
  $("explanation-container").style.display = "none";
  ["next-button", "prev-button", "mark-button", "finish-button"].forEach((id) => { $(id).style.display = "none"; });
  $("palette").style.display = "none";
  $("palette-toggle").style.display = "none";
  $("timer").textContent = "";
  const nextQuiz = !practice && level.quizzes[quizIdx + 1];
  $("new-quiz-button").style.display = nextQuiz ? "block" : "none";
  $("back-link-button").textContent = practice ? (practiceKind === "custom" ? "Build another quiz" : "My scores") : "Back to quizzes";
  $("back-link-button").style.display = "block";
  recordAttempt({
    kind: practice ? practiceKind : "exam",
    examId: practice ? practiceKind : examId,
    stageId: practice ? "practice" : stageId,
    sectionId: practice ? "practice" : sectionId,
    levelId: practice ? "practice" : levelId,
    quizIndex: practice ? 0 : quizIdx,
    quizTitle: quizTitle(),
    score: Number(score.toFixed(2)), maxMarks, correct, wrong, skipped: total - attempted,
    timed: timed(), timeUsedSeconds: usedSecs,
    answers: questions.map((q, i) => ({
      questionId: q.id, subject: q.subject, topic: q.topic,
      chosen: answers[i], correct: answers[i] === q.answer, seconds: Math.min(seconds[i], 36000),
    })),
  });
}

async function toggleReviewBookmark(button) {
  const id = button.dataset.id;
  const on = !bookmarked.has(id);
  button.disabled = true;
  try {
    if (on) await addBookmark(id); else await removeBookmark(id);
    if (on) bookmarked.add(id); else bookmarked.delete(id);
    button.classList.toggle("on", on);
    button.textContent = on ? "★" : "☆";
  } catch (error) {
    window.alert(error.message);
  } finally {
    button.disabled = false;
  }
}

// Logged in: save to the account. Logged out: keep it for this tab and offer to log in,
// and auth.js saves it once they do.
async function recordAttempt(attempt) {
  const status = $("save-status");
  if (await currentUser()) {
    try {
      await saveAttempt(attempt);
      status.innerHTML = 'Saved to <a href="history.html">your scores</a>.';
    } catch (error) {
      console.error("Could not save the result:", error);
      status.textContent = "Could not save this result to your account.";
    }
    return;
  }
  storePendingAttempt(attempt);
  const next = encodeURIComponent("history.html");
  status.innerHTML = `<a href="login.html?next=${next}">Log in</a> or <a href="register.html?next=${next}">sign up</a> to save this score.`;
}

function goNextQuiz() {
  quizIdx += 1;
  const url = new URL(window.location.href);
  url.searchParams.set("quiz", quizIdx);
  window.history.replaceState(null, "", url);
  pickBackground();
  renderStart();
  startQuiz();
}

// ---------- Keyboard shortcuts ----------
// 1-5 pick an option, N/→ next, P/← previous, M mark, B bookmark, S submit.
// (Numbers only for options: letter keys would clash with B for bookmark.)

document.addEventListener("keydown", (e) => {
  if (e.ctrlKey || e.metaKey || e.altKey) return;
  if (e.target.closest("input, textarea, select, dialog")) return;
  if (finished || $("quiz-screen").style.display === "none") return;
  const key = e.key.toLowerCase();
  const optionIndex = "12345".indexOf(key);
  const visible = (id) => $(id).style.display !== "none";
  if (optionIndex >= 0) selectOption(optionIndex);
  else if ((key === "n" || key === "arrowright") && visible("next-button")) showNextQuestion();
  else if ((key === "p" || key === "arrowleft") && visible("prev-button")) showPrevQuestion();
  else if (key === "m" && timed()) toggleMark();
  else if (key === "b" && $("bookmark-button")) toggleBookmark();
  else if (key === "s" && visible("finish-button")) onFinishClick();
  else return;
  e.preventDefault();
});

$("start-button").addEventListener("click", startQuiz);
$("next-button").addEventListener("click", showNextQuestion);
$("prev-button").addEventListener("click", showPrevQuestion);
$("mark-button").addEventListener("click", toggleMark);
$("finish-button").addEventListener("click", onFinishClick);
$("new-quiz-button").addEventListener("click", goNextQuiz);
$("palette-toggle").addEventListener("click", () => document.body.classList.toggle("palette-open"));
$("report-form").addEventListener("submit", submitReport);
$("back-link-button").addEventListener("click", () => {
  window.location.href = practice
    ? (practiceKind === "custom" ? "practice.html" : "history.html")
    : `exam.html?exam=${encodeURIComponent(examId)}`;
});

init();
