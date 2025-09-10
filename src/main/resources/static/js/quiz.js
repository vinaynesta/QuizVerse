let currentQuestions = [];
let currentQuestionIndex = 0;
let totalNoQuestions = 0;
let quizNo = 0;
let score = 0;

document.getElementById("start-button").addEventListener("click", startQuiz);
document
  .getElementById("next-button")
  .addEventListener("click", showNextQuestion);
document
  .getElementById("finish-button")
  .addEventListener("click", showFinalResult);
document.getElementById("new-quiz-button").addEventListener("click", startQuiz);
// document.getElementById('history-button').addEventListener('click', showHistory);

function getQueryParam(param) {
  const urlParams = new URLSearchParams(window.location.search);
  return urlParams.get(param);
}

async function startQuiz() {
  try {
    const subject = getQueryParam("subject");
    document.getElementById("loading-spinner").style.display = "block";
    quizNo+=1;
    if (!subject) {
      console.error("No subject specified in query params.");
      return;
    }
    const response = await fetch("/api/quiz/questions", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        questionType: subject,
        quizNo: quizNo,
      }),
    });
    const responseBody = await response.json();
    console.log("Fetched questions responseBody:", responseBody);

    currentQuestions = responseBody || []; // adjust based on actual API response structure
    totalNoQuestions = currentQuestions.length;

    if (!Array.isArray(currentQuestions)) {
      console.error("Expected array of questions but got:", currentQuestions);
      currentQuestions = [];
    }

    console.log("Questions:", currentQuestions);

    currentQuestionIndex = 0;
    score = 0;

    document.getElementById("start-screen").style.display = "none";
    document.getElementById("quiz-screen").style.display = "block";
    document.getElementById("new-quiz-button").style.display = "none";
    // document.getElementById('history-button').style.display = 'none';
    document.getElementById("loading-spinner").style.display = "none";

    showQuestion();
  } catch (error) {
    console.error("Error fetching questions:", error);
  }
}

function showQuestion() {
  const question = currentQuestions[currentQuestionIndex];
  const container = document.getElementById("question-container");

  container.innerHTML = `
        <div class="question">
            <h3>Question ${currentQuestionIndex + 1} of ${totalNoQuestions}</h3>
            <p>${question.question_text}</p>
            <div class="options">
                <div class="option" data-answer="${encodeURIComponent(
                  question.option_a
                )}">${question.option_a}</div>
                <div class="option" data-answer="${encodeURIComponent(
                  question.option_b
                )}">${question.option_b}</div>
                <div class="option" data-answer="${encodeURIComponent(
                  question.option_c
                )}">${question.option_c}</div>
                <div class="option" data-answer="${encodeURIComponent(
                  question.option_d
                )}">${question.option_d}</div>
            </div>
        </div>
    `;

  document.getElementById("result-container").style.display = "none";
  document.getElementById("explanation-container").style.display = "none";
  document.getElementById("next-button").style.display = "none";
  document.getElementById("finish-button").style.display = "none";

  // Attach event listeners
  const optionElements = document.querySelectorAll(".option");
  optionElements.forEach((opt) => {
    opt.addEventListener("click", () =>
      selectOption(opt, opt.getAttribute("data-answer"))
    );
  });
}

async function selectOption(element, selectedAnswerEncoded) {
  const selectedAnswer = decodeURIComponent(selectedAnswerEncoded);

  // Prevent re-selection if already selected
  if (element.classList.contains("selected")) return;

  const options = document.querySelectorAll(".option");

  // Mark the selected option
  element.classList.add("selected");

  const question = currentQuestions[currentQuestionIndex];

  try {
    const response = await fetch("/api/quiz/verify", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        currentQuestionNo: currentQuestionIndex,
        questionId: question._id,
        selectedAnswer: selectedAnswer,
      }),
    });

    const result = await response.json();
    console.log("verified questions responseBody:", result);

    if (result.correct) {
      score++;
      element.classList.add("correct");
      showResult("Correct!", true);
    } else {
      element.classList.add("incorrect");

      const correctOption = Array.from(options).find(
        (opt) => opt.textContent.trim() === result.correctAnswer.trim()
      );
      if (correctOption) correctOption.classList.add("correct");

      showResult(
        `Incorrect. The correct answer is: ${result.correctAnswer}`,
        false
      );
    }

    showExplanation(result.explanation);

    // Disable all options after selection
    options.forEach((opt) => {
      opt.style.pointerEvents = "none";  // Disable clicking
      opt.classList.add("disabled");     // Optional: Add a CSS class for styling disabled state
    });

    if (currentQuestionIndex < currentQuestions.length - 1) {
      document.getElementById("next-button").style.display = "block";
    } else {
      document.getElementById("finish-button").style.display = "block";
    }
  } catch (error) {
    console.error("Error verifying answer:", error);
  }
}


function showResult(message, isCorrect) {
  const resultContainer = document.getElementById("result-container");
  resultContainer.textContent = message;
  resultContainer.className =
    "result " + (isCorrect ? "correct-answer" : "incorrect-answer");
  resultContainer.style.display = "block";
}

function showExplanation(message) {
  const explanationContainer = document.getElementById("explanation-container");
  explanationContainer.textContent = `Explanation : ${message}`;
  explanationContainer.style.display = "block";
}

function showNextQuestion() {
  currentQuestionIndex++;

  if (currentQuestionIndex >= currentQuestions.length) {
    console.warn("No more questions left.");
    showFinalResult();
    return;
  }

  showQuestion();
  // document.getElementById('history-button').style.display = 'block';
}

function showFinalResult() {
  const container = document.getElementById("question-container");
  container.innerHTML = `
        <div class="question">
            <h2>Quiz Complete!</h2>
            <p>Your score: ${score} out of ${totalNoQuestions}</p>
            <p>Percentage: ${((score / totalNoQuestions) * 100).toFixed(2)}%</p>
        </div>
    `;

  document.getElementById("result-container").style.display = "none";
  document.getElementById("explanation-container").style.display = "none";
  document.getElementById("finish-button").style.display = "none";
  document.getElementById("new-quiz-button").style.display = "block";
  // document.getElementById('history-button').style.display = 'block';
}

async function showHistory() {
  const container = document.getElementById("question-container");

  const response = await fetch("/api/quiz/history");
  const history = await response.json(); // assuming this is an array or string

  console.log("show history:", history);

  // Format as HTML if it's an array
  const formatted = Array.isArray(history)
    ? history
        .map(
          (item, index) =>
            `<p>${index + 1}. ${item.questionText} <br> &rarr; ${
              item.correctAnswer
            }</p>`
        )
        .join("")
    : `<p>${history}</p>`;

  container.innerHTML = `
        <div class="question">
            <h3>History</h3>
            ${formatted}
        </div>
    `;
}
