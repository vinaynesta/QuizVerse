const params = new URLSearchParams(window.location.search);
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
let questions = [];
let answers = []; // selected option index per question, or null
let current = 0;
let timerId = null;
let remaining = 0;
let finished = false;

const $ = (id) => document.getElementById(id);
const timed = () => Boolean(section && section.minutes);

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

async function init() {
  loadBackgrounds();
  try {
    index = await (await fetch("data/index.json")).json();
    exam = index.exams.find((e) => e.id === examId);
    stage = exam && exam.stages.find((st) => st.id === stageId);
    section = stage && stage.sections.find((s) => s.id === sectionId);
    level = section && section.levels.find((l) => l.id === levelId);
    if (!level || !level.quizzes[quizIdx]) {
      $("start-title").textContent = "Quiz not found";
      $("start-button").style.display = "none";
      return;
    }
    renderStart();
  } catch (error) {
    console.error("Error loading quiz index:", error);
  }
}

function quizTitle() {
  // Skip parts already contained in the earlier ones ("Java Practice" + "Practice" + "Java").
  const parts = [];
  [exam.name, stage.name, section.name, level.quizzes[quizIdx].title].forEach((part) => {
    if (!parts.join(" ").toLowerCase().includes(part.toLowerCase())) parts.push(part);
  });
  return parts.join(" - ");
}

function renderStart() {
  $("start-title").textContent = quizTitle();
  $("start-info").textContent = timed()
    ? `${section.questions} questions, ${section.marks} marks, ${section.minutes} minutes. Each wrong answer costs ${index.negativeMarking} of the question's marks.`
    : "Untimed practice. You see the answer after each question.";
  $("start-screen").style.display = "block";
  $("quiz-screen").style.display = "none";
}

async function startQuiz() {
  $("loading-spinner").style.display = "block";
  try {
    questions = await (await fetch(`data/${level.quizzes[quizIdx].file}`)).json();
  } catch (error) {
    console.error("Error fetching questions:", error);
    $("loading-spinner").style.display = "none";
    return;
  }
  answers = questions.map(() => null);
  current = 0;
  finished = false;
  $("loading-spinner").style.display = "none";
  $("start-screen").style.display = "none";
  $("quiz-screen").style.display = "flex";
  $("new-quiz-button").style.display = "none";
  $("back-link-button").style.display = "none";
  $("quiz-label").textContent = quizTitle();
  clearInterval(timerId);
  if (timed()) {
    remaining = section.minutes * 60;
    updateTimer();
    timerId = setInterval(tick, 1000);
  } else {
    $("timer").textContent = "";
  }
  showQuestion();
}

function tick() {
  remaining -= 1;
  updateTimer();
  if (remaining <= 0) finish();
}

function updateTimer() {
  const m = String(Math.floor(remaining / 60)).padStart(2, "0");
  const s = String(remaining % 60).padStart(2, "0");
  const el = $("timer");
  el.textContent = `${m}:${s}`;
  el.classList.toggle("warning", remaining <= 60);
}

function optionsHtml(q, selected) {
  return q.options
    .map(
      (opt, i) =>
        `<div class="option${selected === i ? " selected" : ""}" data-index="${i}">${escapeHtml(opt)}</div>`
    )
    .join("");
}

function showQuestion() {
  const q = questions[current];
  $("question-container").innerHTML = `
    <div class="question">
      <h3>Question ${current + 1} of ${questions.length}</h3>
      <p>${escapeHtml(q.question)}</p>
      <div class="options">${optionsHtml(q, answers[current])}</div>
    </div>`;
  $("result-container").style.display = "none";
  $("explanation-container").style.display = "none";
  $("next-button").style.display = "none";
  $("finish-button").style.display = "none";
  document.querySelectorAll(".option").forEach((opt) =>
    opt.addEventListener("click", () => selectOption(opt, Number(opt.dataset.index)))
  );
  if (timed()) {
    showNav();
  } else if (answers[current] !== null) {
    revealPractice();
  }
}

function showNav() {
  const last = current === questions.length - 1;
  $("next-button").textContent = "Save & Next";
  $("next-button").style.display = last ? "none" : "block";
  $("finish-button").textContent = "Submit Quiz";
  $("finish-button").style.display = "block";
}

function selectOption(element, i) {
  if (finished) return;
  const q = questions[current];
  if (timed()) {
    // Exam mode: answers can be changed; clicking the chosen option again clears it.
    answers[current] = answers[current] === i ? null : i;
    document.querySelectorAll(".option").forEach((opt, k) =>
      opt.classList.toggle("selected", answers[current] === k)
    );
    return;
  }
  if (answers[current] !== null) return;
  answers[current] = i;
  revealPractice();
}

function revealPractice() {
  const q = questions[current];
  const chosen = answers[current];
  const opts = document.querySelectorAll(".option");
  opts.forEach((opt, k) => {
    opt.classList.add("disabled");
    opt.style.pointerEvents = "none";
    if (k === q.answer) opt.classList.add("correct");
    else if (k === chosen) opt.classList.add("incorrect");
  });
  const ok = chosen === q.answer;
  showResult(ok ? "Correct!" : `Incorrect. The correct answer is: ${q.options[q.answer]}`, ok);
  if (q.explanation) showExplanation(q.explanation);
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
  if (current < questions.length - 1) {
    current += 1;
    showQuestion();
  }
}

function onFinishClick() {
  if (timed()) {
    const left = answers.filter((a) => a === null).length;
    if (left > 0 && !window.confirm(`${left} question(s) unanswered. Submit anyway?`)) return;
  }
  finish();
}

function finish() {
  if (finished) return;
  finished = true;
  clearInterval(timerId);
  const total = questions.length;
  const correct = questions.filter((q, i) => answers[i] === q.answer).length;
  const attempted = answers.filter((a) => a !== null).length;
  const wrong = attempted - correct;
  // Marks can differ from the question count (e.g. 40 questions carrying 60 marks).
  const perQ = timed() ? section.marks / total : 1;
  const maxMarks = timed() ? section.marks : total;
  const score = correct * perQ - (timed() ? wrong * perQ * index.negativeMarking : 0);
  const usedSecs = timed() ? section.minutes * 60 - Math.max(remaining, 0) : 0;
  const review = questions
    .map((q, i) => {
      const a = answers[i];
      const kind = a === null ? "skipped" : a === q.answer ? "right" : "wrong";
      const label = { skipped: "Not attempted", right: "Correct", wrong: "Wrong" }[kind];
      return `
      <div class="review-item ${kind}">
        <div class="review-head"><strong>Q${i + 1}</strong><span class="badge ${kind}">${label}</span></div>
        <p class="review-q">${escapeHtml(q.question)}</p>
        <p><span class="review-key">Your answer</span> ${a === null ? "&mdash;" : escapeHtml(q.options[a])}</p>
        <p><span class="review-key">Correct answer</span> ${escapeHtml(q.options[q.answer])}</p>
        ${q.explanation ? `<p class="review-expl">${escapeHtml(q.explanation)}</p>` : ""}
      </div>`;
    })
    .join("");
  const pct = Math.max(0, Math.min(100, (score / maxMarks) * 100));
  const mins = Math.floor(usedSecs / 60);
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
      </div>
      <details class="review"><summary>Review answers</summary>${review}</details>
    </div>`;
  $("result-container").style.display = "none";
  $("explanation-container").style.display = "none";
  $("next-button").style.display = "none";
  $("finish-button").style.display = "none";
  $("timer").textContent = "";
  const nextQuiz = level.quizzes[quizIdx + 1];
  $("new-quiz-button").style.display = nextQuiz ? "block" : "none";
  $("back-link-button").style.display = "block";
}

function goNextQuiz() {
  quizIdx += 1;
  pickBackground();
  renderStart();
  startQuiz();
}

$("start-button").addEventListener("click", startQuiz);
$("next-button").addEventListener("click", showNextQuestion);
$("finish-button").addEventListener("click", onFinishClick);
$("new-quiz-button").addEventListener("click", goNextQuiz);
$("back-link-button").addEventListener("click", () => {
  window.location.href = `exam.html?exam=${encodeURIComponent(examId)}`;
});

init();
