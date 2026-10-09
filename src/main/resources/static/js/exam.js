const examId = new URLSearchParams(window.location.search).get("exam");

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
      return `<h2 class="stage-title">${stage.name}</h2><div class="section-grid">${sections}${soon}</div>`;
    })
    .join("");
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
            (quiz, i) =>
              `<a class="quiz-link" href="quiz.html?exam=${encodeURIComponent(exam.id)}&stage=${encodeURIComponent(stage.id)}&section=${encodeURIComponent(section.id)}&level=${encodeURIComponent(level.id)}&quiz=${i}">${
                level.quizzes.length > 1 ? i + 1 : "Start"
              }</a>`
          )
          .join("")}
      </div>`
    )
    .join("");
  const longNames = section.levels.some((l) => l.name.length > 8) ? " long-names" : "";
  return `<div class="section-card${longNames}"><h3>${section.name}</h3><p class="section-meta">${meta}</p>${note}${levels}</div>`;
}

fetch("data/index.json")
  .then((response) => response.json())
  .then(renderExam)
  .catch((error) => console.error("Error loading exam:", error));
