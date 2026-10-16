# QuizVerse

QuizVerse is a web-based multiple-choice quiz app. Pick a subject, answer a set of questions one at a time, get instant feedback with explanations, and see your score at the end.

## Features

- **Multiple subjects:** Java, Python, C, DBMS, ML, General Knowledge, English, Aptitude and Reasoning.
- **Multiple quizzes per subject:** each subject has numbered quizzes, and "New Quiz" loads the next one.
- **Instant feedback:** after each answer you see whether it was correct,
 the right answer and a short explanation.
- **Score summary:** a final screen shows your score and percentage.
- **Accounts:** sign up and log in to keep a history of every quiz you finish, with your past scores on the "My scores" page. The profile page (click your name) edits your name and email, changes your password and deletes your account.
- **Real exam screen:** question palette (answered / not answered / not visited / marked), mark for review, previous/next, keyboard shortcuts (1–4 answer, N next, P previous, M mark, B bookmark, S submit). The timer counts down to a fixed end time and a running quiz survives a page refresh.
- **Progress:** best score per quiz on the exam page, score trend chart, accuracy and average time per topic, and your five weakest topics.
- **Practice:** revise questions you got wrong or skipped, bookmark questions, build custom quizzes by topic/difficulty/length/time, and report broken questions.

## How it works

1. The home page (`index.html`) lists the subjects from `data/subjects.json`.
2. Choosing a subject opens the quiz page (`quiz.html?subject=<id>`).
3. The browser fetches that subject's quiz file (e.g. `data/java/easy.json`) and runs the quiz entirely client-side. "New Quiz" loads the next quiz file.

The questions are static JSON files served with the page, so **correct answers are visible to anyone who opens the files**. That is fine for practice quizzes. Scoring also happens in the browser, so saved scores are self-reported: good for personal history, not for a competitive leaderboard.

## Accounts and quiz history

The Spring Boot app has a small JSON API backed by a database (`app_user` and `quiz_attempt` tables, created by Hibernate):

| Endpoint | Purpose |
|---|---|
| `POST /api/auth/register` | Create an account (username, email, name, password) and log in |
| `POST /api/auth/login` | Log in with username or email |
| `POST /api/auth/logout` | Log out |
| `GET /api/me` | The logged-in user, or 401 |
| `PUT /api/me` | Change name and email (page `profile.html`) |
| `POST /api/me/password` | Change password (needs the current one); logs out the user's other sessions |
| `DELETE /api/me` | Delete the account and all its data (needs the password) |
| `GET /api/attempts` | The user's finished quizzes, newest first |
| `POST /api/attempts` | Save a finished quiz, with the answer to each question |
| `GET /api/attempts/best` | Best score per quiz (exam page badges) |
| `GET /api/me/topics` | Accuracy and average time per topic |
| `GET /api/me/mistakes` | Questions whose latest answer was wrong or skipped |
| `GET /api/me/bookmarks`, `PUT`/`DELETE /api/me/bookmarks/{questionId}` | Bookmarks |
| `POST /api/reports` | Report a question |
| `GET /api/admin/reports`, `POST /api/admin/reports/{id}/resolve` | Review reports (admins only, page `reports.html`) |

Login uses a session cookie (30 days). Passwords are stored as BCrypt hashes. Requests that change data need the CSRF token from the `XSRF-TOKEN` cookie in an `X-XSRF-TOKEN` header, which `static/js/api.js` adds. When you finish a quiz while logged out, the result is kept for that browser tab and saved once you log in or sign up.

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | Plain HTML, CSS and vanilla JavaScript |
| Question store | Static JSON files in `src/main/resources/static/data/` |
| Server | Spring Boot 3 (Java 21, Maven): serves the static files and the account API (Spring Security, Spring Data JPA) |
| Database | H2 file database locally (`./data/`), PostgreSQL in production |
| Hosting | Railway. CI (`.github/workflows/maven.yml`) builds, tests and deploys `main`; it needs the `RAILWAY_TOKEN` secret and `RAILWAY_SERVICE` variable. |

## Exams

Each exam has a **Prelims** and a **Mains** stage. Every section has Easy, Medium and Hard levels with 5 quizzes each. A quiz has the real section's question count, marks and time limit, auto-submits at zero, and applies 0.25 negative marking per wrong answer, scaled to the question's marks.

| Exam | Prelims | Mains |
|---|---|---|
| IBPS Clerk | English 30q/20m, Numerical 35q/20m, Reasoning 35q/20m | Reasoning 40q/60 marks/35m, English 40q/35m, Quant 40q/50 marks/35m |
| SBI Clerk | English 30q/20m, Quant 35q/20m, Reasoning 35q/20m | English 40q/35m, Quant 50q/45m, Reasoning 50q/60 marks/45m |
| IBPS RRB Clerk | Reasoning 40q/25m, Numerical 40q/20m | Reasoning 40q/50 marks/30m, English 40q/30m, Numerical 40q/50 marks/30m |

General/Financial Awareness, Computer Knowledge and the Hindi language option have no question bank yet. They are listed as "coming soon". SBI's Reasoning & Computer Aptitude section covers the reasoning part only. A Java practice set (untimed, instant feedback) is also included.

The patterns come from a published summary of the 2026 patterns plus IBPS pages seen in search results. IBPS's own handout PDFs could not be opened, so check the notification for your cycle.

## Question data

Everything lives in `src/main/resources/static/data/`. `index.json` lists exams, sections, levels and quiz files. Each quiz file is an array of:

```json
{ "id": "...", "topic": "...", "question": "...", "options": ["A", "B", "C", "D"], "answer": 1, "explanation": "..." }
```

`answer` is the zero-based index into `options`. `id` is a hash of the subject, question text and options, so it stays the same when the data is rebuilt; saved answers, bookmarks and reports point at it. Each question also has `subject` and `level`. The data is generated by scripts, in this order:

```bash
python3 -I scripts/gen_banks.py scripts/banks                      # numerical + reasoning banks (answers computed)
python3 -I scripts/build_questions.py scripts/legacy/java_questions.sql src/main/resources/static/data   # Java quizzes
python3 -I scripts/build_exams.py scripts/banks scripts/english_bank src/main/resources/static/data
python3 -I scripts/build_topics.py src/main/resources/static/data  # topic pools for practice sets
```

CI rebuilds the data and fails if the committed files differ, so always commit the regenerated output. Changing a question's text or options changes its id; any bookmarks or history on the old id stop finding it (history totals are kept).

Quiz pages show a random background from `static/images/` (every image except `feels like we just met yesterday.jpeg`, which is the home and exam page backdrop). After adding or removing images, run:

```bash
python3 -I scripts/build_backgrounds.py src/main/resources/static
```

English questions are hand-written in `scripts/english_bank/`. Each English bank holds 200 questions per level (5 quizzes of up to 40); the generated numerical and reasoning banks hold 250 (5 quizzes of up to 50). Questions do not repeat within a section and level.

## Running locally

Requirements: JDK 21.

```bash
./mvnw spring-boot:run
```

Then open http://localhost:8080. Accounts are stored in an H2 database file under `./data/` (git-ignored); delete that folder to start fresh. Run the tests with `./mvnw test`.

The database schema is managed by Flyway (`src/main/resources/db/migration`). To change it, add a new `V<n>__description.sql` file; never edit a migration that has already run.

## Configuration

| Variable | Purpose |
|---|---|
| `PORT` | HTTP port. Defaults to 8080 and is set automatically on Railway. |
| `QUIZVERSE_ADMIN_USERNAMES` | Comma-separated usernames that can review question reports. |
| `SPRING_PROFILES_ACTIVE` | Set to `prod` in production to use PostgreSQL and Secure cookies. |
| `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` | PostgreSQL connection (prod profile). Railway's Postgres plugin provides these. |

## License

See [LICENSE](LICENSE).
