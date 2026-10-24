import { currentUser, streak } from "./api.js";
import { istToday, loadTopics, questionOfTheDay } from "./questions.js";

function renderExams(index) {
  const container = document.getElementById("subject-list");
  container.innerHTML = index.exams
    .map(
      (exam) => `
        <div class="subject-card">
            <h3>${exam.name}</h3>
            <p class="card-desc">${exam.description}</p>
            <a class="start-quiz-button" href="exam.html?exam=${encodeURIComponent(exam.id)}">Open</a>
        </div>
    `
    )
    .join("");
}

fetch("data/index.json")
  .then((response) => response.json())
  .then(renderExams)
  .catch((error) => console.error("Error loading exams:", error));

// Logged-in users see their streak and whether today's daily quiz and tech challenge are done.
currentUser().then(async (user) => {
  if (!user) return;
  const s = await streak();
  const parts = [s.dailyDone ? "Done for today ✓" : "Not done yet today"];
  if (s.current > 0) parts.push(`🔥 ${s.current}-day streak`);
  document.getElementById("daily-status").textContent = parts.join(" · ");
  document.getElementById("daily-card").classList.toggle("done", s.dailyDone);
  if (s.techDailyDone) document.getElementById("tech-daily-status").textContent = "Done for today ✓";
  document.getElementById("tech-daily-card").classList.toggle("done", s.techDailyDone);
}).catch((error) => console.error("Could not load streak:", error));

// ---------- Question of the day ----------
// One tech question, the same for everyone, answered right here. The answer is remembered in this browser
// for the day, so a reload shows the result instead of a fresh question. Not saved to the account.

const QOTD_KEY = "quizverse.qotd";
const qotdDate = istToday();

function savedChoice(questionId) {
  try {
    const saved = JSON.parse(localStorage.getItem(QOTD_KEY) || "null");
    return saved && saved.date === qotdDate && saved.id === questionId ? saved.chosen : null;
  } catch {
    return null;
  }
}

function showQotdResult(q, chosen) {
  const buttons = document.querySelectorAll("#qotd-options .qotd-option");
  buttons.forEach((b, i) => {
    b.disabled = true;
    b.classList.toggle("correct", i === q.answer);
    b.classList.toggle("incorrect", i === chosen && chosen !== q.answer);
  });
  const result = document.getElementById("qotd-result");
  result.textContent = `${chosen === q.answer ? "✓ Correct!" : "✗ Not quite."} ${q.explanation || ""} Come back tomorrow for a new one.`;
  result.hidden = false;
}

async function renderQotd() {
  const [q, topics] = await Promise.all([questionOfTheDay(qotdDate), loadTopics()]);
  if (!q) return;
  const subject = topics.subjects.find((s) => s.id === q.subject);
  document.getElementById("qotd-subject").textContent = subject ? subject.name : q.subject;
  document.getElementById("qotd-question").textContent = q.question;
  const options = document.getElementById("qotd-options");
  options.replaceChildren(...q.options.map((text, i) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "qotd-option";
    button.textContent = text;
    button.addEventListener("click", () => {
      try {
        localStorage.setItem(QOTD_KEY, JSON.stringify({ date: qotdDate, id: q.id, chosen: i }));
      } catch {
        // Private mode: the result just isn't remembered.
      }
      showQotdResult(q, i);
    });
    return button;
  }));
  const chosen = savedChoice(q.id);
  if (chosen !== null) showQotdResult(q, chosen);
  document.getElementById("qotd").hidden = false;
}

renderQotd().catch((error) => console.error("Question of the day unavailable:", error));
