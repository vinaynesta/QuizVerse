# QuizVerse

QuizVerse is a web-based multiple-choice quiz app. Pick a subject, answer a set of questions one at a time, get instant feedback with explanations, and see your score at the end.

## Features

- **Multiple subjects:** Java, Python, C, DBMS, ML, General Knowledge, English, Aptitude and Reasoning.
- **Multiple quizzes per subject:** each subject has numbered quizzes, and "New Quiz" loads the next one.
- **Instant feedback:** after each answer you see whether it was correct, the right answer and a short explanation.
- **Score summary:** a final screen shows your score and percentage.

## How it works

1. The home page (`index.html`) lists the subjects.
2. Choosing a subject opens the quiz page (`quiz.html?subject=<subject>`).
3. The frontend asks the backend for the questions for that subject and quiz number.
4. When you pick an option, the frontend sends it to the backend, which checks it and returns the result and explanation.

Correct answers and explanations are never sent to the browser with the questions. They are only returned after an answer is submitted.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3 (Java 21, Maven) |
| Question store | DataStax Astra DB (Data API) |
| Frontend | Plain HTML, CSS and vanilla JavaScript, served by Spring Boot |
| Hosting | Railway |

## API

| Endpoint | Description |
|---|---|
| `POST /api/quiz/questions` | Body `{ "questionType": "java", "quizNo": "1" }`. Returns the questions without answers. |
| `POST /api/quiz/verify` | Body `{ "questionId": "...", "selectedAnswer": "..." }`. Returns `correct`, `correctAnswer` and `explanation`. |

## Running locally

Requirements: JDK 21 and an Astra DB database with a `question` collection in the `quizverse_keyspace` keyspace.

```bash
export ASTRA_DATABASE_URL="<your Astra database API endpoint>"
export ASTRA_DB_TOKEN="<your Astra application token>"
./mvnw spring-boot:run
```

Then open http://localhost:8080.

Each question document has the fields `question_text`, `option_a` to `option_d`, `correct_answer`, `explanation`, `question_type` and `quiz_no`.

## Configuration

| Variable | Purpose |
|---|---|
| `ASTRA_DATABASE_URL` | Astra DB API endpoint |
| `ASTRA_DB_TOKEN` | Astra application token. Keep it secret and never commit it. |
| `PORT` | HTTP port. Defaults to 8080 and is set automatically on Railway. |

## License

See [LICENSE](LICENSE).
