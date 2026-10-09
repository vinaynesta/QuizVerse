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
