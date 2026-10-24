# QuizVerse Architecture

How the app is put together, from the browser to the database. The diagrams render on GitHub (Mermaid).

**Keeping this up to date:** everything between `BEGIN GENERATED` and `END GENERATED` markers is built from the code by `scripts/build_architecture.py`:

| Section | Built from |
|---|---|
| Backend components | controllers' and helpers' constructor parameters, repositories' entity types |
| Endpoints | `@*Mapping` annotations and their javadoc, access from the rules in `SecurityConfig` |
| Database schema | the Flyway migrations, replayed in order |
| JPA entities | `model/*.java` fields and `@ManyToOne` / `@OneToOne` relations |
| Frontend | each page's `<script>` tags and each module's imports and `fetch` calls |
| Configuration | `${ENV_VAR}` references in `application*.properties` |
| Build and deploy | `.github/workflows/*.yml` |
| Question content | `static/data/index.json` and the quiz files |

It updates three ways:

1. **Every commit (automatic).** Run `git config core.hooksPath .githooks` once per clone. The pre-commit hook regenerates the docs from the staged files whenever a commit touches anything above, and adds them to the commit.
2. **CI (safety net).** `build_architecture.py --check` fails the build if the docs don't match the code, so a commit made without the hook can't merge.
3. **By hand:** `python3 -I scripts/build_architecture.py`.

Edit only the text outside the markers; it explains what the code can't show. The README's schema diagram is generated the same way.

## The big picture

```mermaid
flowchart LR
    user(["Phone or desktop browser"])
    sw["Service worker (sw.js)<br/>offline cache"]
    subgraph railway["Railway (Docker container)"]
        boot["Spring Boot 3 app<br/>static files + JSON API"]
    end
    pg[("PostgreSQL<br/>Aiven")]
    google(["Google Identity Services"])
    user --> sw --> boot
    boot -->|JDBC, Flyway on start| pg
    user -.->|sign-in popup| google
    boot -.->|ID token check| google
```

- **One deployable.** The Spring Boot jar serves the static site (`src/main/resources/static`) and the `/api` JSON endpoints. Railway builds it from the `Dockerfile` on every push to `main`.
- **Quizzes run in the browser.** Questions are static JSON files. `quiz.js` loads a quiz file, runs the timer, scores it and only then posts the result to `/api/attempts`. The server never sees questions or answer keys, so saved scores are self-reported.
- **Accounts and history live in the database.** Spring Security keeps the login in a server-side session (30-day cookie); CSRF uses the `XSRF-TOKEN` cookie echoed in a header by `api.js`.
- **Offline.** The service worker caches pages and every quiz file opened once. A result finished offline is queued in the browser (`pending.js`) and saved when the device is back online.
- **One instance.** Sessions are in memory, so the app must run as a single instance (see the roadmap before scaling out).

## Backend components

Controllers handle HTTP and validation, repositories (Spring Data JPA) talk to the database, and a few helpers sit in `security/` and `service/`. There is no separate service layer: controllers call repositories directly, each request in one transaction.

<!-- BEGIN GENERATED: components -->
```mermaid
flowchart LR
    browser(["Browser: static pages + JS modules"])
    filter["Spring Security filter chain<br/>(session cookie, CSRF, access rules)"]
    browser -->|JSON /api/*| filter
    subgraph controllers["Controllers"]
        AttemptController
        AuthController
        BookmarkController
        ChallengeController
        GoogleAuthController
        ProfileController
        ReportController
        StatsController
        StatusController
        StreakController
    end
    subgraph services["Security and services"]
        AppUserDetailsService
        GoogleTokenVerifier
        SessionSignIn
        SessionTracker
        StorageMode
        Streaks
    end
    subgraph repositories["Spring Data repositories"]
        AttemptAnswerRepository
        BookmarkRepository
        ChallengeRepository
        QuestionReportRepository
        QuizAttemptRepository
        UserRepository
    end
    db[("Database<br/>H2 locally, PostgreSQL in prod")]
    filter --> controllers
    AttemptController --> QuizAttemptRepository
    AttemptController --> AttemptAnswerRepository
    AttemptController --> UserRepository
    AttemptController --> ChallengeRepository
    AuthController --> UserRepository
    AuthController --> AppUserDetailsService
    AuthController --> SessionSignIn
    BookmarkController --> BookmarkRepository
    BookmarkController --> UserRepository
    ChallengeController --> ChallengeRepository
    ChallengeController --> QuizAttemptRepository
    ChallengeController --> UserRepository
    GoogleAuthController --> GoogleTokenVerifier
    GoogleAuthController --> UserRepository
    GoogleAuthController --> AppUserDetailsService
    GoogleAuthController --> SessionSignIn
    ProfileController --> UserRepository
    ProfileController --> AppUserDetailsService
    ProfileController --> SessionTracker
    ReportController --> QuestionReportRepository
    ReportController --> UserRepository
    StatsController --> AttemptAnswerRepository
    StatsController --> UserRepository
    StatusController --> StorageMode
    StatusController --> GoogleTokenVerifier
    StreakController --> QuizAttemptRepository
    StreakController --> UserRepository
    StreakController --> Streaks
    AppUserDetailsService --> UserRepository
    SessionSignIn --> SessionTracker
    AttemptAnswerRepository -->|attempt_answer| db
    BookmarkRepository -->|bookmark| db
    ChallengeRepository -->|challenge| db
    QuestionReportRepository -->|question_report| db
    QuizAttemptRepository -->|quiz_attempt| db
    UserRepository -->|app_user| db
    google(["Google JWKS keys"])
    GoogleTokenVerifier -.->|verify ID token| google
```
<!-- END GENERATED: components -->

### Endpoints and who can call them

"Access" comes from the rules in `SecurityConfig`: *public* needs nothing, *login* needs a session, *role ADMIN* needs a username listed in `QUIZVERSE_ADMIN_USERNAMES`. Every request that changes data also needs the CSRF header.

<!-- BEGIN GENERATED: endpoints -->
| Method | Path | Access | Controller | What it does |
|---|---|---|---|---|
| GET | `/api/admin/reports` | role ADMIN | ReportController |  |
| POST | `/api/admin/reports/{id}/resolve` | role ADMIN | ReportController |  |
| GET | `/api/attempts` | login | AttemptController | Newest first. |
| POST | `/api/attempts` | login | AttemptController |  |
| GET | `/api/attempts/best` | login | AttemptController |  |
| GET | `/api/auth/csrf` | public | AuthController | Hands out the CSRF token; also sets the XSRF-TOKEN cookie if the page has none yet. |
| POST | `/api/auth/google` | public | GoogleAuthController |  |
| POST | `/api/auth/login` | public | AuthController |  |
| POST | `/api/auth/logout` | public | AuthController |  |
| POST | `/api/auth/register` | public | AuthController |  |
| POST | `/api/challenges/from-attempt/{attemptId}` | login | ChallengeController | Creates the challenge for one of your attempts, or returns the existing one (one code per attempt). |
| GET | `/api/challenges/{code}` | public | ChallengeController | Public, so a link opened by someone logged out still shows what they're being challenged to. |
| GET | `/api/me` | login | AuthController | The logged-in user, or 401 (the frontend uses this to decide what to show). |
| PUT | `/api/me` | login | ProfileController |  |
| DELETE | `/api/me` | login | ProfileController | Removes the user and, through ON DELETE CASCADE, their attempts, answers, bookmarks, reports and challenges. |
| GET | `/api/me/bookmarks` | login | BookmarkController | Newest first. |
| PUT | `/api/me/bookmarks/{questionId}` | login | BookmarkController |  |
| DELETE | `/api/me/bookmarks/{questionId}` | login | BookmarkController |  |
| PUT | `/api/me/goal` | login | StreakController |  |
| POST | `/api/me/google` | login | GoogleAuthController | Connects a Google account to the logged-in user, so they can sign in with either. |
| DELETE | `/api/me/google` | login | GoogleAuthController | Disconnects Google. |
| GET | `/api/me/mistakes` | login | StatsController | Questions last answered wrong or skipped, newest first. |
| POST | `/api/me/password` | login | ProfileController | Keeps this session logged in and logs out every other one, e.g. a phone someone else has. |
| GET | `/api/me/streak` | login | StreakController |  |
| GET | `/api/me/topics` | login | StatsController |  |
| POST | `/api/reports` | login | ReportController | A second report of the same question by the same user, while the first is open, is a no-op. |
| GET | `/api/status` | public | StatusController |  |
<!-- END GENERATED: endpoints -->

## Database schema

Flyway owns the schema (`src/main/resources/db/migration`) and Hibernate only validates it. Migrations must run on both H2 (local and tests) and PostgreSQL. Never edit one that has run; add `V<n>__name.sql`.

Questions are not in the database. `question_id` columns hold the question's content hash (`q` + 11 hex), which points into the static JSON. `attempt_answer` copies the subject and topic so stats keep working after a question is removed.

<!-- BEGIN GENERATED: schema -->
```mermaid
erDiagram
    app_user ||--o{ quiz_attempt : "user_id, on delete cascade"
    challenge |o--o{ quiz_attempt : "challenge_id, on delete set null"
    quiz_attempt ||--o{ attempt_answer : "attempt_id, on delete cascade"
    app_user ||--o{ bookmark : "user_id, on delete cascade"
    app_user ||--o{ question_report : "user_id, on delete cascade"
    quiz_attempt ||--o| challenge : "attempt_id, on delete cascade"
    app_user {
        bigint id PK "not null"
        varchar username UK "30, not null"
        varchar email UK "254, not null"
        varchar display_name "60, not null"
        varchar password_hash "100, nullable"
        timestamptz created_at "not null"
        int daily_goal "not null"
        varchar google_subject UK "255, nullable"
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
```
<!-- END GENERATED: schema -->

<!-- BEGIN GENERATED: schema-notes -->
Built from 6 migrations (V1__accounts_and_attempts.sql … V6__challenges.sql).

| Table | Unique together | Indexes |
|---|---|---|
| `app_user` | - | - |
| `quiz_attempt` | - | `idx_attempt_user` (user_id, completed_at); `idx_attempt_challenge` (challenge_id) |
| `attempt_answer` | - | `idx_answer_attempt` (attempt_id); `idx_answer_question` (question_id) |
| `bookmark` | (user_id, question_id) | - |
| `question_report` | - | `idx_report_open` (resolved, created_at) |
| `challenge` | - | - |
<!-- END GENERATED: schema-notes -->

### JPA entities

The same tables as Hibernate sees them. Relations are navigable only in the direction drawn (there are no collection fields), and deletes cascade in the database, not through JPA.

<!-- BEGIN GENERATED: entities -->
```mermaid
classDiagram
    direction LR
    class AttemptAnswer {
        <<table attempt_answer>>
        Long id
        String questionId
        String subject
        String topic
        Integer chosen
        boolean correct
        int seconds
    }
    class Bookmark {
        <<table bookmark>>
        Long id
        String questionId
        Instant createdAt
    }
    class Challenge {
        <<table challenge>>
        Long id
        String code
        Instant createdAt
    }
    class QuestionReport {
        <<table question_report>>
        Long id
        String questionId
        String reason
        String note
        boolean resolved
        Instant createdAt
    }
    class QuizAttempt {
        <<table quiz_attempt>>
        Long id
        String kind
        String examId
        String stageId
        String sectionId
        String levelId
        int quizIndex
        String quizTitle
        double score
        double maxMarks
        int correct
        int wrong
        int skipped
        boolean timed
        int timeUsedSeconds
        Instant completedAt
    }
    class User {
        <<table app_user>>
        Long id
        String username
        String email
        String displayName
        String passwordHash
        String googleSubject
        Instant createdAt
        int dailyGoal
    }
    AttemptAnswer "*" --> "1" QuizAttempt : attempt
    Bookmark "*" --> "1" User : user
    Challenge "1" --> "1" QuizAttempt : attempt
    QuestionReport "*" --> "1" User : user
    QuizAttempt "*" --> "1" User : user
    QuizAttempt "*" --> "0..1" Challenge : challenge
```
<!-- END GENERATED: entities -->

### What a quiz attempt row means

`quiz_attempt.kind` says what was taken, and decides how the other key columns are used:

| kind | exam_id | stage_id | section_id | level_id / quiz_index |
|---|---|---|---|---|
| `exam` | exam (e.g. `ibps-clerk`, `tech`) | stage | section (`java`, `dsa`, …) | level and quiz number |
| `mock` | exam | stage | `mock` | level and mock number |
| `daily` | `daily` | `practice` | the IST date `yyyy-mm-dd` | `practice` / 0 |
| `tech-daily` | `tech-daily` | `practice` | the IST date `yyyy-mm-dd` | `practice` / 0 |
| `revise`, `bookmarks`, `custom` | the kind | `practice` | `practice` | `practice` / 0 |

## Frontend

Plain HTML pages, each loading one or two ES modules. `api.js` wraps `fetch` for the JSON API (CSRF header, 401 handling); `questions.js` reads the static question data and picks the daily sets. `theme.js` (light/dark) is on every page and left out of the chart.

<!-- BEGIN GENERATED: frontend -->
```mermaid
flowchart LR
    subgraph pages["Pages"]
        page_challenge["challenge.html"]
        page_exam["exam.html"]
        page_history["history.html"]
        page_index["index.html"]
        page_login["login.html"]
        page_practice["practice.html"]
        page_profile["profile.html"]
        page_quiz["quiz.html"]
        page_register["register.html"]
        page_reports["reports.html"]
    end
    subgraph modules["JS modules"]
        js_account["account.js"]
        js_api["api.js"]
        js_auth["auth.js"]
        js_challenge["challenge.js"]
        js_exam["exam.js"]
        js_google["google.js"]
        js_history["history.js"]
        js_home["home.js"]
        js_pending["pending.js"]
        js_practice["practice.js"]
        js_profile["profile.js"]
        js_questions["questions.js"]
        js_quiz["quiz.js"]
        js_reports["reports.js"]
        js_share["share.js"]
    end
    data[("static/data/*.json<br/>question bank")]
    apiserver(["/api/* (Spring Boot)"])
    page_challenge --> js_account
    page_challenge --> js_challenge
    page_exam --> js_exam
    page_exam --> js_account
    page_history --> js_account
    page_history --> js_history
    page_index --> js_home
    page_index --> js_account
    page_login --> js_account
    page_login --> js_auth
    page_practice --> js_account
    page_practice --> js_practice
    page_profile --> js_account
    page_profile --> js_profile
    page_quiz --> js_account
    page_quiz --> js_quiz
    page_register --> js_account
    page_register --> js_auth
    page_reports --> js_account
    page_reports --> js_reports
    js_account --> js_api
    js_account --> js_pending
    js_api -.-> apiserver
    js_auth --> js_api
    js_auth --> js_google
    js_auth --> js_pending
    js_challenge --> js_api
    js_exam --> js_api
    js_exam -.-> data
    js_google --> js_api
    js_history --> js_api
    js_history --> js_pending
    js_history --> js_questions
    js_home --> js_api
    js_home --> js_questions
    js_home -.-> data
    js_practice --> js_questions
    js_profile --> js_api
    js_profile --> js_google
    js_questions -.-> data
    js_quiz --> js_api
    js_quiz --> js_pending
    js_quiz --> js_questions
    js_quiz --> js_share
    js_quiz -.-> data
    js_reports --> js_api
    js_reports --> js_questions
```
<!-- END GENERATED: frontend -->

## Request flows

Two flows that touch most of the stack.

**Finishing a quiz** (scoring stays in the browser; the server stores the result):

```mermaid
sequenceDiagram
    participant P as quiz.js
    participant L as localStorage / pending.js
    participant S as Spring Security
    participant A as AttemptController
    participant DB as Database
    P->>P: score answers, apply negative marking
    alt logged in and online
        P->>S: POST /api/attempts + X-XSRF-TOKEN
        S->>S: check session cookie and CSRF token
        S->>A: AttemptRequest (validated)
        A->>DB: insert quiz_attempt, attempt_answer rows
        A-->>P: 201 AttemptResponse (id for "Challenge a friend")
    else offline
        P->>L: queue result, account.js saves it when back online
    else logged out
        P->>L: keep result for this tab, saved after log in or sign up
    end
```

**Signing in with Google:**

```mermaid
sequenceDiagram
    participant B as login.html (google.js)
    participant G as Google Identity Services
    participant C as GoogleAuthController
    participant V as GoogleTokenVerifier
    participant DB as Database
    B->>G: popup, user picks account
    G-->>B: ID token (JWT)
    B->>C: POST /api/auth/google {credential}
    C->>V: verify signature (Google JWKS), iss, aud, exp
    C->>DB: find app_user by google_subject
    alt not found and email unused
        C->>DB: create user (no password)
    else email belongs to another account
        C-->>B: 409, log in with password and connect Google on the profile page
    end
    C-->>B: session cookie (SessionSignIn), user JSON
```

## Configuration

Environment variables the app reads. Real values live in Railway (or your shell), never in the repo.

<!-- BEGIN GENERATED: config -->
| Variable | Default | Used by | Profile |
|---|---|---|---|
| `PORT` | `8080` | `server.port` | all, prod |
| `QUIZVERSE_ADMIN_USERNAMES` | (none) | `quizverse.admin-usernames` | all |
| `GOOGLE_CLIENT_ID` | (none) | `quizverse.google-client-id` | all |
| `PGHOST` | (none) | `spring.datasource.url` | prod |
| `PGPORT` | `5432` | `spring.datasource.url` | prod |
| `PGDATABASE` | (none) | `spring.datasource.url` | prod |
| `PGSSLMODE` | `require` | `spring.datasource.url` | prod |
| `PGUSER` | (none) | `spring.datasource.username` | prod |
| `PGPASSWORD` | (none) | `spring.datasource.password` | prod |
<!-- END GENERATED: config -->

## Build, test and deploy

<!-- BEGIN GENERATED: pipeline -->
```mermaid
flowchart LR
    dev(["git push / pull request"])
    subgraph maven["Java CI with Maven (maven.yml)"]
        maven_build["<b>build</b><br/>• Set up JDK 21<br/>• Set up Python<br/>• Check question data is up to date<br/>• Check architecture doc is up to date<br/>• Build and test"]
    dev -->|push / pull request to main| maven_build
        maven_deploy["<b>deploy</b><br/>• Install Railway CLI<br/>• Deploy to Railway"]
        maven_build -->|main only| maven_deploy
    end
    maven_deploy --> railway
    subgraph review_app_deploy["Deploy Review App (review-app-deploy.yml)"]
        review_app_deploy_deploy["<b>deploy</b><br/>• Install Railway CLI<br/>• Deploy to Railway"]
    dev -->|manual run| review_app_deploy_deploy
    end
    review_app_deploy_deploy --> railway
    railway[("Railway<br/>Dockerfile build → container")]
```
<!-- END GENERATED: pipeline -->

The Docker build compiles the jar in a JDK stage and runs it on a JRE image. Flyway migrates the database when the app starts.

## Question content

Question data is generated, never edited by hand. The scripts run in this order (CI checks the output is committed):

```mermaid
flowchart LR
    gen["gen_banks.py<br/>quant + reasoning"] --> banks[("scripts/banks")]
    sql[("legacy/java_questions.sql")] --> bq["build_questions.py"]
    eng[("scripts/english_bank")] --> be
    banks --> be["build_exams.py<br/>bank exams + index.json"]
    bq --> be
    tech[("scripts/tech_bank<br/>DSA, Java, Python, Spring Boot,<br/>React, Vue, AI")] --> bt["build_tech.py<br/>Tech Interview Prep"]
    be --> bt --> bto["build_topics.py<br/>topic pools"]
    bto --> data[("static/data/*.json")]
```

<!-- BEGIN GENERATED: content -->
| Exam / track | Stage | Sections | Quizzes | Questions |
|---|---|---|---|---|
| IBPS Clerk | Prelims | English Language, Numerical Ability, Reasoning Ability | 45 | 1500 |
| IBPS Clerk | Mains | Reasoning Ability, English Language, Quantitative Aptitude | 45 | 1800 |
| SBI Clerk | Prelims | English Language, Quantitative Aptitude, Reasoning Ability | 45 | 1500 |
| SBI Clerk | Mains | General English, Quantitative Aptitude, Reasoning Ability | 45 | 2100 |
| IBPS RRB Clerk | Prelims | Reasoning Ability, Numerical Ability | 30 | 1200 |
| IBPS RRB Clerk | Mains | Reasoning Ability, English Language, Numerical Ability | 45 | 1800 |
| Java Practice | Practice | Java | 9 | 142 |
| Tech Interview Prep | Practice | Data Structures & Algorithms, Java, Python, Spring Boot, React, Vue.js, Artificial Intelligence | 84 | 840 |

Quiz slots can reuse a question (the same bank feeds several exams): 3081 unique questions in 10 subjects.
<!-- END GENERATED: content -->

### Daily features

All three are picked in the browser from the IST date with a seeded random generator (`questions.js`), so everyone gets the same questions without any server work:

- **Daily quiz:** 10 bank-exam questions (4 quant, 3 reasoning, 3 English), 10 minutes. Saved as kind `daily`.
- **Daily tech challenge:** 5 questions from 5 of the 7 tech subjects, 5 minutes. Saved as kind `tech-daily`.
- **Question of the day:** one tech question answered on the home page, never one from today's tech challenge. The answer is kept in the browser for that day only; it isn't saved to the account.

`GET /api/me/streak` reports `dailyDone` and `techDailyDone`, and both count towards the day streak like any finished quiz.
