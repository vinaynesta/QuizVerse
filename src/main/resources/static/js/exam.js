import { bestScores, currentUser } from "./api.js";

const examId = new URLSearchParams(window.location.search).get("exam");
// "exam/stage/section/level/quizIndex" -> { bestScore, maxMarks, attempts }, for logged-in users.
let best = new Map();

function renderExam(index) {
  const exam = index.exams.find((e) => e.id === examId);
  const list = document.getElementById("section-list");
  if (!exam) {
    list.textContent = "Exam not found.";
    return;
  }
  document.title = `QuizVerse - ${exam.name}`;
  document.getElementById("exam-title").textContent = exam.name;
  const timed = exam.stages.some((st) => st.sections.some((sec) => sec.minutes));
  document.getElementById("exam-info").textContent = timed
    ? `${exam.description} Each quiz uses the real section's question count, marks and time limit. Wrong answers cost ${index.negativeMarking} of a question's marks.`
    : exam.description;

  list.innerHTML = exam.stages
    .map((stage) => {
      const sections = stage.sections.map((section) => renderSection(exam, stage, section)).join("");
      const soon = stage.unavailable
        .map(
          (u) => `<div class="section-card coming-soon"><h3>${u.name}</h3>
            <p class="section-meta">${u.questions} questions &middot; ${u.marks} marks &middot; ${u.minutes} minutes</p>
            <p class="section-meta">Coming soon</p></div>`
        )
        .join("");
      return `<h2 class="stage-title">${stage.name}</h2>${renderMocks(exam, stage)}<div class="section-grid">${sections}${soon}</div>`;
    })
    .join("");
}

/** Full mock tests: quiz N of every section in a row. Only for stages whose sections are all timed. */
function renderMocks(exam, stage) {
  const timedSections = stage.sections.filter((s) => s.minutes);
  if (stage.sections.length < 2 || timedSections.length !== stage.sections.length) return "";
  const sum = (k) => stage.sections.reduce((n, s) => n + s[k], 0);
  const levels = stage.sections[0].levels
    .map((level) => {
      const count = Math.min(...stage.sections.map((s) => (s.levels.find((l) => l.id === level.id) || { quizzes: [] }).quizzes.length));
      const links = Array.from({ length: count }, (_, i) => {
        const done = best.get(`${exam.id}/${stage.id}/mock/${level.id}/${i}`);
        const pct = done && done.maxMarks > 0 ? Math.max(0, Math.round((done.bestScore / done.maxMarks) * 100)) : 0;
        const href = `quiz.html?exam=${encodeURIComponent(exam.id)}&stage=${encodeURIComponent(stage.id)}&level=${encodeURIComponent(level.id)}&mock=${i}`;
        return `<a class="quiz-link${done ? " done" : ""}" title="${level.name} mock ${i + 1}${done ? `: best ${pct}%` : ""}" href="${href}">${i + 1}${
          done ? `<span class="quiz-best">✓ ${pct}%</span>` : ""}</a>`;
      }).join("");
      return count ? `<div class="level-row lvl-${level.id}"><span class="level-name">${level.name}</span>${links}</div>` : "";
    })
    .join("");
  const c = stage.cutoff;
  const cutoff = c
    ? `<p class="section-meta">Last cutoff (${c.cycle}, General): ${c.low}–${c.high} out of ${c.outOf} depending on state. Your mock result shows where you'd stand.</p>`
    : "";
  return `<div class="section-card mock-card"><h3>Full mock tests</h3>
    <p class="section-meta">The whole ${stage.name} paper: ${sum("questions")} questions &middot; ${sum("marks")} marks &middot; ${sum("minutes")} minutes. Each section is timed separately and locks when its time is up.</p>
    ${cutoff}${levels}</div>`;
}

function renderSection(exam, stage, section) {
  const meta = section.questions
    ? `${section.questions} questions &middot; ${section.marks} marks &middot; ${section.minutes} minutes`
    : "Untimed practice";
  const note = section.note ? `<p class="section-meta">${section.note}</p>` : "";
  const levels = section.levels
    .map(
      (level) => `
      <div class="level-row lvl-${level.id}">
        <span class="level-name">${level.name}</span>
        ${level.quizzes
          .map(
            (quiz, i) => {
              const done = best.get(`${exam.id}/${stage.id}/${section.id}/${level.id}/${i}`);
              const pct = done && done.maxMarks > 0 ? Math.max(0, Math.round((done.bestScore / done.maxMarks) * 100)) : 0;
              const title = done
                ? `${quiz.title}: best ${pct}% over ${done.attempts} attempt${done.attempts > 1 ? "s" : ""}`
                : quiz.title;
              return `<a class="quiz-link${done ? " done" : ""}" title="${title}" href="quiz.html?exam=${encodeURIComponent(exam.id)}&stage=${encodeURIComponent(stage.id)}&section=${encodeURIComponent(section.id)}&level=${encodeURIComponent(level.id)}&quiz=${i}">${
                level.quizzes.length > 1 ? i + 1 : "Start"
              }${done ? `<span class="quiz-best">✓ ${pct}%</span>` : ""}</a>`;
            }
          )
          .join("")}
      </div>`
    )
    .join("");
  const longNames = section.levels.some((l) => l.name.length > 8) ? " long-names" : "";
  return `<div class="section-card${longNames}"><h3>${section.name}</h3><p class="section-meta">${meta}</p>${note}${levels}</div>`;
}

async function loadBest() {
  if (!(await currentUser())) return;
  try {
    best = new Map((await bestScores()).map((b) => [b.key, b]));
  } catch (error) {
    console.error("Could not load your best scores:", error);
  }
}

Promise.all([fetch("data/index.json").then((response) => response.json()), loadBest()])
  .then(([index]) => renderExam(index))
  .catch((error) => console.error("Error loading exam:", error));
