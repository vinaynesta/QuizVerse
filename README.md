# QuizVerse

QuizVerse is a web-based multiple-choice quiz app. Pick a subject, answer a set of questions one at a time, get instant feedback with explanations, and see your score at the end.

## Features

- **Bank exams and tech interview prep:** IBPS Clerk, SBI Clerk, IBPS RRB Clerk, IBPS PO, SBI PO and RBI Assistant papers (English, Quant, Reasoning), plus a Tech Interview Prep track with DSA, Java, Python, Spring Boot, React, Vue and AI (120 hand-written questions each, Easy/Medium/Hard, with explanations).
- **Multiple quizzes per subject:** each subject has numbered quizzes, and "New Quiz" loads the next one.
- **Instant feedback:** after each answer you see whether it was correct,
 the right answer and a short explanation.
- **Score summary:** a final screen shows your score and percentage.
- **Accounts:** sign up and log in to keep a history of every quiz you finish, with your past scores on the "My scores" page. The profile page (click your name) edits your name and email, changes your password and deletes your account.
- **Real exam screen:** question palette (answered / not answered / not visited / marked), mark for review, previous/next, keyboard shortcuts (1–4 answer, N next, P previous, M mark, B bookmark, S submit, L English/Hindi). The timer counts down to a fixed end time and a running quiz survives a page refresh.
- **Progress:** best score per quiz on the exam page, score trend chart, accuracy and average time per topic, and your five weakest topics.
- **Full mock tests:** sit a whole Prelims or Mains paper from the exam page (Easy, Medium or Hard, 5 of each). Each section has its own timer and locks when its time is up or you submit it, as in IBPS exams. The result shows a score for each section and overall, and a refresh resumes the test.
- **Cutoffs:** Prelims mock results show the last published General-category cutoff range across states, a few large states, and where your score falls. The figures (in `scripts/build_exams.py`, with sources) come from coaching-site tables, so they're marked approximate.
- **Installable app and offline practice:** add QuizVerse to your phone's home screen from the browser. Pages and every quiz you've opened once work without internet; results finished offline are kept on the device and saved to your account when you're back online.
- **Daily quiz and streaks:** the same 10 mixed questions (4 quant, 3 reasoning, 3 English, 10 minutes) for everyone each day, picked in the browser from the date. "My scores" shows your day streak, best streak and progress towards a daily goal you choose. Days follow India time (IST).
- **Daily tech challenge and question of the day:** a second daily card with 5 tech questions in 5 minutes (one each from 5 of the 7 tech subjects), and one tech question to answer right on the home page. Both are the same for everyone that day.
- **Share and dark mode:** "Share result" on the score screen makes an image of your score for WhatsApp and other apps (or downloads it on desktop). Every page has a light/dark toggle; without a choice it follows the device setting.
- **Challenge a friend:** after an exam quiz or full mock, "Challenge a friend" makes a link (`challenge.html?code=...`). Friends see your score, take the same quiz through the link, and appear on that challenge's scoreboard (each friend's first try counts). Viewing is open to anyone; taking it needs an account. Scores are self-reported, so it's for friendly competition.
- **Google sign-in:** "Continue with Google" on the login and sign-up pages creates an account (no password needed) or signs you in. An existing account is never joined to Google automatically: log in with your password and connect Google on the profile page. Google-only accounts can set a password later. The button only appears when `GOOGLE_CLIENT_ID` is set.
- **Forgot password:** "Forgot password?" on the login page emails a reset link (valid 30 minutes, single use). Using it logs out every device. Needs email set up (`RESEND_API_KEY`, `MAIL_FROM`, `QUIZVERSE_PUBLIC_URL`); in local test mode the email is printed to the log instead.
- **Email reminders and weekly summary:** opt-in on the profile page. A reminder after 3 days without a quiz (once per quiet spell, 6:15 p.m. IST) and a Monday 8:10 a.m. IST summary of the week. Every email has a one-click unsubscribe link and `List-Unsubscribe` headers. Runs on one server instance only (a second one would send duplicates).
- **Server scoring and leaderboards:** timed exam quizzes and full mocks are published without answers. The server scores them (`POST /api/quiz/submit`), checks the time limit with a signed start token, and returns the answer key for the review. Results with a valid receipt are ranked on per-quiz and weekly leaderboards (`leaderboard.html`). Practice sets keep their answers on the device, so practice still works offline. Users can appear as "Anonymous".
- **Live contests:** admins schedule a contest on `contests.html` (any exam quiz or full mock, a start time and a duration). Users register, then sit the paper in the window: the server serves it with questions and options shuffled per person, keeps the time, and scores it. Ranks and percentiles are published when it ends. Designed for one server instance; a big contest at one start time needs a larger Railway plan and database connections.
- **Practice:** revise questions you got wrong or skipped, bookmark questions, build custom quizzes by topic/difficulty/length/time, and report broken questions.
- **Grouped questions:** medium and hard Quant quizzes end with a data interpretation table, Reasoning quizzes with a floor puzzle and English quizzes with a reading comprehension passage, each shared by 5 questions and shown above every one of them. Contests keep a set's questions together.
- **Step-by-step solutions:** generated Quant and Reasoning questions show the working line by line, with shortcuts; English solutions add a method line for the question type.
- **Hindi (draft):** easy Quant questions on percentages, simple interest, profit and speed have a Hindi version; the हिंदी button (or L) on the quiz screen switches. Machine-drafted and marked "Draft" until a fluent reviewer checks it.
- **Computer Knowledge and Banking Awareness (draft):** a starter set of static-fact practice questions (no current affairs), marked "Draft" until checked. Exam sections stay "coming soon" until there are enough.
- **Question editor (admins):** on the reports page, edit a question's text, options, answer or solution, or hide it. Edits are stored by question id and applied on the next data build (see Question data), so history and bookmarks stay attached.

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
| `POST /api/auth/google` | Sign in or sign up with a Google ID token |
| `POST`/`DELETE /api/me/google` | Connect or disconnect Google on the logged-in account (disconnecting needs a password set) |
| `GET /api/status` | Public: whether storage is temporary (test mode) and the Google client id |
| `POST /api/challenges/from-attempt/{attemptId}` | Make (or get) the challenge link for one of your exam quizzes or mocks |
| `GET /api/challenges/{code}` | Public: the challenge's quiz, the challenger's score and friends' scores |
| `GET /api/me` | The logged-in user, or 401 |
| `PUT /api/me` | Change name and email (page `profile.html`) |
| `POST /api/me/password` | Change password (needs the current one, except for a Google-only account's first password); logs out the user's other sessions |
| `DELETE /api/me` | Delete the account and all its data (needs the password, or the username typed out for a Google-only account) |
| `GET /api/attempts` | The user's finished quizzes, newest first |
| `POST /api/attempts` | Save a finished quiz, with the answer to each question |
| `GET /api/attempts/best` | Best score per quiz (exam page badges) |
| `GET /api/me/topics` | Accuracy and average time per topic |
| `GET /api/me/streak` | Day streak, quizzes finished today, daily goal, and whether today's daily quiz is done |
| `PUT /api/me/goal` | Set the daily goal (1-10 quizzes) |
| `GET /api/me/mistakes` | Questions whose latest answer was wrong or skipped |
| `GET /api/me/bookmarks`, `PUT`/`DELETE /api/me/bookmarks/{questionId}` | Bookmarks |
| `POST /api/reports` | Report a question |
| `GET /api/admin/reports`, `POST /api/admin/reports/{id}/resolve` | Review reports (admins only, page `reports.html`) |

Login uses a session cookie (30 days). Passwords are stored as BCrypt hashes. Requests that change data need the CSRF token from the `XSRF-TOKEN` cookie in an `X-XSRF-TOKEN` header, which `static/js/api.js` adds. When you finish a quiz while logged out, the result is kept for that browser tab and saved once you log in or sign up.

## Architecture

```mermaid
flowchart LR
    user(["Browser / installed app"]) --> sw["Service worker<br/>offline cache"]
    sw -->|pages, JS, quiz JSON| boot
    sw -->|JSON /api/*| boot
    subgraph railway["Railway container"]
        boot["Spring Boot 3<br/>static site + REST API<br/>Spring Security sessions"]
    end
    boot -->|JPA + Flyway| pg[("PostgreSQL (Aiven)<br/>H2 locally")]
    boot -.->|verify ID token| google(["Google sign-in"])
```

Quizzes run in the browser from static JSON; the server stores accounts, results and per-question answers. The schema below and everything in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) (components, endpoints with access rules, entities, frontend modules, configuration, CI/CD pipeline) is **generated from the code** by `scripts/build_architecture.py`:

- **On every commit:** turn on the repo's hook once per clone with `git config core.hooksPath .githooks`. A commit that touches the code, migrations, pages, config or workflows then regenerates the diagrams and adds them to the same commit.
- **In CI:** the build fails if the docs don't match the code, so a commit made without the hook can't merge stale diagrams.
- **By hand:** `python3 -I scripts/build_architecture.py`.

### Database schema

<!-- BEGIN GENERATED: schema -->
```mermaid
erDiagram
    app_user ||--o{ quiz_attempt : "user_id, on delete cascade"
    challenge |o--o{ quiz_attempt : "challenge_id, on delete set null"
    quiz_attempt ||--o{ attempt_answer : "attempt_id, on delete cascade"
    app_user ||--o{ bookmark : "user_id, on delete cascade"
    app_user ||--o{ question_report : "user_id, on delete cascade"
    quiz_attempt ||--o| challenge : "attempt_id, on delete cascade"
    app_user ||--o{ password_reset_token : "user_id, on delete cascade"
    contest ||--o{ contest_entry : "contest_id, on delete cascade"
    app_user ||--o{ contest_entry : "user_id, on delete cascade"
    app_user {
        bigint id PK "not null"
        varchar username UK "30, not null"
        varchar email UK "254, not null"
        varchar display_name "60, not null"
        varchar password_hash "100, nullable"
        timestamptz created_at "not null"
        int daily_goal "not null"
        varchar google_subject UK "255, nullable"
        boolean email_reminders "not null"
        boolean email_weekly "not null"
        varchar email_token UK "43, nullable"
        timestamptz last_reminder_at "nullable"
        timestamptz last_weekly_at "nullable"
        boolean leaderboard_hidden "not null"
    }
    quiz_attempt {
        bigint id PK "not null"
        bigint user_id FK "not null"
        varchar exam_id "64, not null"
        varchar stage_id "64, not null"
        varchar section_id "64, not null"
        varchar level_id "64, not null"
        int quiz_index "not null"
        varchar quiz_title "200, not null"
        double score "not null"
        double max_marks "not null"
        int correct "not null"
        int wrong "not null"
        int skipped "not null"
        boolean timed "not null"
        int time_used_seconds "not null"
        timestamptz completed_at "not null"
        varchar kind "16, not null"
        bigint challenge_id FK "nullable"
        boolean verified "not null"
        varchar receipt_hash UK "64, nullable"
    }
    attempt_answer {
        bigint id PK "not null"
        bigint attempt_id FK "not null"
        varchar question_id "16, not null"
        varchar subject "32, not null"
        varchar topic "64, not null"
        int chosen "nullable"
        boolean correct "not null"
        int seconds "not null"
    }
    bookmark {
        bigint id PK "not null"
        bigint user_id FK "not null"
        varchar question_id "16, not null"
        timestamptz created_at "not null"
    }
    question_report {
        bigint id PK "not null"
        bigint user_id FK "not null"
        varchar question_id "16, not null"
        varchar reason "32, not null"
        varchar note "500, nullable"
        boolean resolved "not null"
        timestamptz created_at "not null"
    }
    challenge {
        bigint id PK "not null"
        varchar code UK "10, not null"
        bigint attempt_id FK,UK "not null"
        timestamptz created_at "not null"
    }
    password_reset_token {
        bigint id PK "not null"
        bigint user_id FK "not null"
        varchar token_hash UK "64, not null"
        timestamptz expires_at "not null"
        boolean used "not null"
        timestamptz created_at "not null"
    }
    contest {
        bigint id PK "not null"
        varchar title "120, not null"
        varchar quiz_key "200, not null"
        timestamptz starts_at "not null"
        int minutes "not null"
        timestamptz created_at "not null"
    }
    contest_entry {
        bigint id PK "not null"
        bigint contest_id FK "not null"
        bigint user_id FK "not null"
        timestamptz registered_at "not null"
        timestamptz started_at "nullable"
        timestamptz submitted_at "nullable"
        varchar chosen "1500, nullable"
        double score "nullable"
        int correct "nullable"
        int wrong "nullable"
        int skipped "nullable"
        int seconds_used "nullable"
    }
    question_edit {
        bigint id PK "not null"
        varchar question_id UK "16, not null"
        varchar question "4000, nullable"
        varchar options "2000, nullable"
        int answer "nullable"
        varchar explanation "4000, nullable"
        boolean hidden "not null"
        varchar note "500, nullable"
        varchar edited_by "50, not null"
        timestamptz updated_at "not null"
    }
```
<!-- END GENERATED: schema -->

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | Plain HTML, CSS and vanilla JavaScript |
| Question store | Static JSON files in `src/main/resources/static/data/` |
| Server | Spring Boot 3 (Java 21, Maven): serves the static files and the account API (Spring Security, Spring Data JPA) |
| Database | H2 file database locally (`./data/`), PostgreSQL in production (Aiven) |
| Hosting | Railway, built from the `Dockerfile` and deployed by Railway from `main` on GitHub (any Docker host works with the variables under Configuration). CI (`.github/workflows/maven.yml`) builds and tests every push and pull request. |

## Exams

Each exam has a **Prelims** and a **Mains** stage. Every section has Easy, Medium and Hard levels with 5 quizzes each. A quiz has the real section's question count, marks and time limit, auto-submits at zero, and applies 0.25 negative marking per wrong answer, scaled to the question's marks.

| Exam | Prelims | Mains |
|---|---|---|
| IBPS Clerk | English 30q/20m, Numerical 35q/20m, Reasoning 35q/20m | Reasoning 40q/60 marks/35m, English 40q/35m, Quant 40q/50 marks/35m |
| SBI Clerk | English 30q/20m, Quant 35q/20m, Reasoning 35q/20m | English 40q/35m, Quant 50q/45m, Reasoning 50q/60 marks/45m |
| IBPS RRB Clerk | Reasoning 40q/25m, Numerical 40q/20m | Reasoning 40q/50 marks/30m, English 40q/30m, Numerical 40q/50 marks/30m |
| IBPS PO | English 30q/20m, Quant 35q/20m, Reasoning 35q/20m | Reasoning & Computer 45q/60 marks/60m, English 35q/40 marks/40m, Data Analysis 35q/60 marks/45m |
| SBI PO | English 40q/20m, Quant 30q/20m, Reasoning 30q/20m | Reasoning & Computer 40q/60 marks/50m, Data Analysis 30q/60 marks/45m, English 35q/40 marks/40m |
| RBI Assistant | English 30q/20m, Numerical 35q/20m, Reasoning 35q/20m | Reasoning 40q/30m, English 40q/30m, Numerical 40q/30m |

General/Financial Awareness and Computer Knowledge exam sections are listed as "coming soon" (a small draft practice set exists). Reasoning & Computer Aptitude sections cover the reasoning part only. PO Mains also has a descriptive paper (letter and essay) that can't be marked as a quiz, so it's left out. The PO and RBI Assistant patterns are from the last notifications; check each new one. A Java practice set and the Tech Interview Prep track (both untimed, instant feedback) are also included.

The patterns come from a published summary of the 2026 patterns plus IBPS pages seen in search results. IBPS's own handout PDFs could not be opened, so check the notification for your cycle.

## Question data

Everything lives in `src/main/resources/static/data/`. `index.json` lists exams, sections, levels and quiz files. Each quiz file is an array of:

```json
{ "id": "...", "topic": "...", "question": "...", "options": ["A", "B", "C", "D"], "answer": 1, "explanation": "..." }
```

`answer` is the zero-based index into `options`. **Timed exam quiz files have no `answer` or `explanation`:** `build_exams.py` writes those to `src/main/resources/answer-keys.json` (on the classpath, never served), and the server scores exam quizzes and mocks from it. The practice topic pools (`data/topics/`) keep the answers so practice works offline. `id` is a hash of the subject, question text and options, so it stays the same when the data is rebuilt; saved answers, bookmarks and reports point at it. Each question also has `subject` and `level`. The data is generated by scripts, in this order:

```bash
python3 -I scripts/gen_banks.py scripts/banks                      # numerical + reasoning banks (answers computed)
python3 -I scripts/gen_groups.py scripts/banks                     # DI tables and puzzles: 5 questions per set-up
python3 -I scripts/build_questions.py scripts/legacy/java_questions.sql src/main/resources/static/data   # Java quizzes
python3 -I scripts/build_exams.py scripts/banks scripts/english_bank src/main/resources/static/data src/main/resources/answer-keys.json
python3 -I scripts/build_tech.py scripts/tech_bank src/main/resources/static/data   # Tech Interview Prep
python3 -I scripts/build_topics.py src/main/resources/static/data src/main/resources/answer-keys.json scripts/ga_bank  # topic pools for practice sets
```

CI rebuilds the data and fails if the committed files differ, so always commit the regenerated output. Changing a question's text or options in a bank changes its id; any bookmarks or history on the old id stop finding it (history totals are kept). To fix a question without that, use the admin editor: download `question_edits.json` from the reports page into `scripts/`, rerun the scripts and commit. Edits keep the original id.

Other inputs: `scripts/banks/groups_*.json` (DI and puzzle sets, `gen_groups.py`), `scripts/english_bank/rc_*.json` (reading passages), `scripts/ga_bank/` (Computer Knowledge and Banking Awareness). Items with `"needsReview": true` show a "Draft" badge; remove the flag once a person has checked the facts. Each exam takes its own 250-question slice of the generated Quant and Reasoning banks (`EXAM_POOLS` in `gen_banks.py`, one per exam in `EXAMS`; raise it when adding an exam). English is one shared bank, so English questions repeat across exams until more are written.

Prelims cutoffs live in `CUTOFFS` in `scripts/build_exams.py`, each with its source URLs. Update them after each result (only with a source), rebuild, and commit `index.json`.

The service worker (`static/sw.js`) caches pages, scripts and quiz data for offline use. It fetches pages, CSS and JS from the network first, so deploys show up straight away; bump its `VERSION` only to force-clear old caches. It never caches `/api/` responses.

Quiz pages show a random background from `static/images/` (every image except `feels like we just met yesterday.jpeg`, which is the home and exam page backdrop). After adding or removing images, run:

```bash
python3 -I scripts/build_backgrounds.py src/main/resources/static
```

English questions are hand-written in `scripts/english_bank/`. Each English bank holds 200 questions per level (5 quizzes of up to 40); the generated numerical and reasoning banks hold 250 (5 quizzes of up to 50). Questions do not repeat within a section and level.

Tech questions are hand-written in `scripts/tech_bank/<subject>/{easy,medium,hard}.json` (40 per level, made into quizzes of 10). `build_tech.py` checks each question (4 distinct options, an answer, an explanation, no duplicates). To add a subject, create its folder and add it to `SUBJECTS` in `build_tech.py`, `SUBJECTS` in `build_topics.py` and `TECH_SUBJECTS` in `static/js/questions.js`.

## Running locally

Requirements: JDK 21.

```bash
./mvnw spring-boot:run
```

Then open http://localhost:8080. Accounts are stored in an H2 database file under `./data/` (git-ignored); delete that folder to start fresh. Run the tests with `./mvnw test`.

While the app uses H2, every page shows a small "Test mode" strip above the account bar, because H2 data is only meant to be temporary. On Railway (detected by its `RAILWAY_ENVIRONMENT_NAME` variable) the app refuses to start with H2 at all, since every deploy wipes the container's disk: set the prod profile and the `PG*` variables (see Configuration).

The database schema is managed by Flyway (`src/main/resources/db/migration`). To change it, add a new `V<n>__description.sql` file; never edit a migration that has already run.

## Configuration

**Local secrets.** Copy `.env.example` to `.env` and fill in values; Spring Boot loads `./.env` automatically. `.env` is gitignored and never committed. In production, set the same names as Railway service variables. The `application*.properties` files only reference these as `${VAR}` and must never hold real values.

**Leak guard.** Enable the pre-commit hook once per clone with `git config core.hooksPath .githooks`. It blocks commits that stage `.env`, key files or `application-local.properties`, or that add lines that look like API keys, passwords or database URLs with credentials.

| Variable | Purpose |
|---|---|
| `PORT` | HTTP port. Defaults to 8080 and is set automatically on Railway. |
| `QUIZVERSE_ADMIN_USERNAMES` | Comma-separated usernames that can review question reports. |
| `GOOGLE_CLIENT_ID` | OAuth 2.0 web client id from Google Cloud Console (ends in `.apps.googleusercontent.com`). Turns on Google sign-in; leave unset to hide it. It's public (the page sends it to Google), so it isn't a secret. |
| `RESEND_API_KEY` | API key from [Resend](https://resend.com) for sending email (forgot password, reminders). **Secret**: set it only as a Railway variable. Leave unset to turn email off. |
| `MAIL_FROM` | Sender, e.g. `QuizVerse <no-reply@yourdomain>`. The domain must be verified in Resend (SPF and DKIM DNS records). |
| `QUIZVERSE_PUBLIC_URL` | The site's address for links in emails, e.g. `https://thequizverse.up.railway.app`. Required for reset emails in production. |
| `QUIZVERSE_SIGNING_KEY` | **Secret.** Signs quiz start tokens and receipts for leaderboards. A long random string (e.g. `openssl rand -base64 48`). Unset, a new key is made at each start, so quizzes running during a redeploy aren't ranked. |
| `SPRING_PROFILES_ACTIVE` | Set to `prod` in production to use PostgreSQL and Secure cookies. |
| `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` | PostgreSQL connection (prod profile). Use Railway's Postgres plugin (reference its variables from the app service, e.g. `${{Postgres.PGHOST}}`) or the values from an external host such as Neon or Supabase. |
| `PGSSLMODE` | SSL mode for PostgreSQL. Defaults to `require`, which hosted databases need; set `disable` only for a server without SSL. |

## Deployment

Railway builds the `Dockerfile` and deploys every push to `main`; the GitHub workflow only builds and tests. `.github/workflows/review-app-deploy.yml` is a manual deploy of any branch and needs a Railway **project** token in the `RAILWAY_TOKEN` secret and the service name in the `RAILWAY_SERVICE` variable.

On the Railway app service, set `SPRING_PROFILES_ACTIVE=prod` and the `PG*` variables for the PostgreSQL database (currently a free Aiven service; Aiven's ports aren't 5432, so copy `PGPORT` too). Flyway creates or updates the tables on start.

### Google sign-in setup

1. In [Google Cloud Console](https://console.cloud.google.com/), create a project, then **Google Auth Platform** (APIs & Services → OAuth consent screen): app name QuizVerse, your support email, audience **External**. Scopes: the default `openid`, `email` and `profile` are all it uses.
2. **Clients → Create client → Web application.** Under **Authorized JavaScript origins** add `https://thequizverse.up.railway.app` (and `http://localhost:8080` for local testing). No redirect URI is needed: the button uses a popup and posts the ID token to `/api/auth/google`.
3. Copy the client id into `GOOGLE_CLIENT_ID` on the Railway service (and as `GOOGLE_CLIENT_ID=...` in `.env` for local runs).
4. While the consent screen is in **Testing**, only test users you add can sign in. **Publish** it for everyone; for the basic scopes this doesn't need Google's verification.

### Email setup

1. Sign up at [Resend](https://resend.com), add your domain under **Domains** and create the DNS records it shows (SPF, DKIM, and ideally a DMARC record). Wait for it to show **Verified**.
2. Create an API key with **Sending access** only, and set `RESEND_API_KEY`, `MAIL_FROM` and `QUIZVERSE_PUBLIC_URL` on the Railway service.
3. Try "Forgot password?" with your own account. Without a verified domain, Resend only delivers to your own address.

### Backups

Aiven's free plan lists backups as included but doesn't say how long they're kept or offer point-in-time recovery, and it powers off a service that has no activity for a while (it emails first; power it back on in the console). Free and Developer plans are capped at 20 connections. So keep your own copies with `pg_dump`, which needs the PostgreSQL client tools (`brew install libpq`, then use `$(brew --prefix libpq)/bin/pg_dump`, or `brew install postgresql@17`). Use a client version at least as new as the server's:

```bash
# The Service URI is on the service's Overview page in the Aiven console. Keep it out of shell history and the repo.
read -rs AIVEN_URI    # paste postgres://avnadmin:...@...aivencloud.com:PORT/defaultdb?sslmode=require
pg_dump "$AIVEN_URI" --format=custom --no-owner --file "quizverse-$(date +%F).dump"
```

Store the dump outside the repo (`*.dump` is git-ignored as well). To restore into an empty database, for example a new Aiven service:

```bash
pg_restore --dbname "$NEW_URI" --no-owner --clean --if-exists quizverse-2026-10-23.dump
```

`flyway_schema_history` is in the dump, so the app starts against a restored database without re-running migrations. Take a dump before any migration you add and every week or so; it's small (accounts, attempts and answers).

## License

See [LICENSE](LICENSE).
