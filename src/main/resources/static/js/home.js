const subjects = [
  "Java",
  "Python",
  "C",
  "DBMS",
  "ML",
  "General Knowledge",
  "English",
  "Aptitude",
  "Reasoning",
];

function renderSubjects() {
  const container = document.getElementById("subject-list");
  container.innerHTML = subjects
    .map(
      (subject) => `
        <div class="subject-card">
            <h3>${subject}</h3>
            <button class="start-quiz-button" data-subject="${subject.toLowerCase()}">Start Quiz</button>
        </div>
    `
    )
    .join("");

  document.querySelectorAll(".start-quiz-button").forEach((button) => {
    button.addEventListener("click", () =>
      startQuizForSubject(button.dataset.subject)
    );
  });
}

async function startQuizForSubject(subject) {
  document.getElementById("loading-spinner").style.display = "block";

  try {
    // Redirect to quiz page with subject as query param
    window.location.href = `/quiz.html?subject=${encodeURIComponent(subject)}`;
  } catch (error) {
    console.error("Error starting quiz:", error);
  }
}

// Initialize
renderSubjects();
