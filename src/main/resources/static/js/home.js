import { currentUser, streak } from "./api.js";

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

// Logged-in users see their streak and whether today's daily quiz is done.
currentUser().then(async (user) => {
  if (!user) return;
  const s = await streak();
  const parts = [s.dailyDone ? "Done for today ✓" : "Not done yet today"];
  if (s.current > 0) parts.push(`🔥 ${s.current}-day streak`);
  document.getElementById("daily-status").textContent = parts.join(" · ");
  document.getElementById("daily-card").classList.toggle("done", s.dailyDone);
}).catch((error) => console.error("Could not load streak:", error));
