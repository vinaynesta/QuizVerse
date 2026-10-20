# QuizVerse Feature Roadmap

A list of features that could be built next, with an estimate, dependencies and risks for each. It reflects the code as of October 2026, after the accounts and quiz-history work.

**Status (17 Oct 2026):** phases 0, 1 and 2 are built: F1, F2, #2, #3, #5, #6, #7, #8, #9, #11, #12, #13, #31, #33, #34, #35, #36. From phase 3, #27 (profile page), #10 (streaks and daily goals), #14 (daily quiz), #23 (share result card) and #30 (dark mode) are built. #25 (forgot password) is the only phase 3 item left; it needs an email provider first. Phase 4 is built: #1 (full mock tests), #4 (section cutoffs, as a state range for Prelims) and #29 (installable app with offline practice). Several facts in the next table describe the code before that work.

## How to read the estimates

- **Unit:** developer-days. One day is a full working day for one developer who already knows this codebase. Each estimate includes the code, tests and a manual check in the browser.
- **Ranges:** the low end assumes nothing goes wrong. The high end allows for the risks listed for that feature.
- **Content vs code:** some features are mostly writing or checking questions, not code. Their estimates depend on how many questions you want and who reviews them. AI tools can speed up drafting, but every question still needs a person to check it.
- **Buffer:** add about 25% on top of any total for code review, bug fixes and deployment.

## What the current code means for these features

These facts about the code drive many of the estimates below.

| Fact | Why it matters |
|---|---|
| Question IDs depend on position, e.g. `ibps-clerk-prelims-reasoning-easy-1-1`. Re-running `build_exams.py` gives the same question a different ID. | Any feature that remembers a specific question (bookmarks, wrong answers, topic stats, reports) needs IDs that don't change. That's foundation item **F1**. |
| Saved attempts only store totals (score, correct, wrong, skipped), not which option was picked for each question. | Topic analysis, retrying wrong answers and time per question all need answers stored per question. That's foundation item **F2**. |
| Answers are in public JSON and scoring happens in the browser. | Any feature where scores are compared between users (leaderboards, contests, challenges) needs scoring moved to the server (#21). |
| The 600 English questions are hand-written, 200 per level. The largest section needs 200 per level (40 questions × 5 quizzes), so the English bank is fully used. | Separate question pools per exam and more exams need more hand-written English questions, not just a config change. |
| Quant and Reasoning come from 46 generator functions in `scripts/gen_banks.py`, with 250 questions per level. | Cheap to scale: more questions, better solutions and Hindi templates are code changes. The risk is near-identical repeats. |
| 45 distinct topics are tagged on exam questions. The Java questions have no `topic` field. | Topic analysis works for the exams straight away. Java would need tagging first. |
| `quiz.js` keeps quiz state in memory only. | Refreshing the page in the middle of a 45-minute section loses everything (fixed in #5). |
| Hibernate creates tables with `ddl-auto=update`, with no migration scripts. | Fine until there are real users. Before that, add Flyway (#36). |
| The mail starter is set up but points at Mailtrap's sandbox, which doesn't deliver real email. | Password reset and reminders need a real email provider, which takes setup time and can involve DNS records. |

---

## Summary table

| # | Feature | Area | Value | Effort (days) | Depends on | Risk |
|---|---|---|---|---|---|---|
| F1 | Stable question IDs | Foundation | Enabler | 1 | none | Low |
| F2 | Store each answer per question | Foundation | Enabler | 1.5–2 | F1 | Low |
| 1 | Full mock tests (whole paper) | Exam realism | ★★★ | 3–4 | 2, 5 | Medium |
| 2 | Question palette | Exam realism | ★★★ | 1–1.5 | none | Low |
| 3 | Mark for review | Exam realism | ★★ | 0.5 | 2 | Low |
| 4 | Section cutoffs next to score | Exam realism | ★★ | 1–2 | none | Medium (finding data) |
| 5 | Drift-proof timer + resume after refresh | Exam realism | ★★★ | 0.5–1 | none | Low |
| 6 | Topic-wise weak areas | Analytics | ★★★ | 2–3 | F1, F2 | Low |
| 7 | Score trend chart | Analytics | ★★ | 1 | none | Low |
| 8 | Time per question | Analytics | ★★ | 1–1.5 | F2 | Low |
| 9 | Completion badges / best score per quiz | Analytics | ★★★ | 1 | none | Low |
| 10 | Streaks and daily goals | Analytics | ★★ | 1–1.5 | none | Low |
| 11 | Retry wrong answers | Learning | ★★★ | 2–3 | F1, F2 | Medium |
| 12 | Bookmarks | Learning | ★★ | 1–1.5 | F1 | Low |
| 13 | Custom quiz builder | Learning | ★★ | 2.5–3.5 | F1 | Medium |
| 14 | Daily quiz | Learning | ★★ | 1–1.5 | none | Low |
| 15 | Step-by-step solutions | Learning | ★★★ | 5–8 | none | Medium |
| 16 | General/Financial Awareness and Computer Knowledge | Content | ★★★ | 15–25 | F1 | High (accuracy, goes out of date) |
| 17 | More exams (IBPS PO, SBI PO, RBI Assistant) | Content | ★★★ | 3–5 per exam | 19 for PO | Medium |
| 18 | Hindi language option | Content | ★★ | 20–30 | 15 recommended | High |
| 19 | New question types (DI, puzzles, RC) | Content | ★★★ | 8–12 | F1 | Medium |
| 20 | Separate question pools per exam | Content | ★ | 1.5–3 | more English writing | Medium |
| 21 | Server-side scoring + leaderboards | Competitive | ★★ | 6–8 | F1, F2 | High (big rework) |
| 22 | Live quiz contests with rank | Competitive | ★★ | 8–12 | 21 | High |
| 23 | Share result card | Competitive | ★ | 1–1.5 | none | Low |
| 24 | Challenge a friend | Competitive | ★ | 1.5–2 | 21 for fairness | Low |
| 25 | Forgot password by email | Accounts | ★★★ | 2–3 | email provider | Medium |
| 26 | Google sign-in | Accounts | ★★ | 2–3 | none | Medium |
| 27 | Profile page (edit, change password, delete) | Accounts | ★★ | 1.5–2 | none | Low |
| 28 | Email reminders and weekly summary | Accounts | ★ | 2–3 | 25 | Medium (deliverability) |
| 29 | Installable app with offline practice | Polish | ★★ | 3–4 | 5 | Medium |
| 30 | Dark mode | Polish | ★ | 1–1.5 | none | Low |
| 31 | Keyboard shortcuts | Polish | ★ | 0.5 | none | Low |
| 32 | Admin question editor | Polish | ★★ | 8–12 | F1, 36 | High (fights the generator pipeline) |
| 33 | Report a wrong question | Polish | ★★ | 1–1.5 | F1 | Low |
| 34 | Remove dead code and unused dependencies | Engineering | Hygiene | 0.5 | none | Low |
| 35 | Fix Maven wrapper + real CI deploy | Engineering | Hygiene | 0.5–1 | none | Low |
| 36 | Database migrations (Flyway) | Engineering | Hygiene | 1 | none | Low |

**Value key:** ★★★ = clearly helps users pass the exam or keeps them coming back; ★★ = useful; ★ = nice to have.

### Totals

| Group | Low | High |
|---|---|---|
| Foundations (F1, F2) | 2.5 | 3 |
| Exam realism (1–5) | 6 | 9 |
| Analytics (6–10) | 6 | 8 |
| Learning (11–15) | 11.5 | 17.5 |
| Content (16–20), counting one new exam | 47.5 | 75 |
| Competitive (21–24) | 16.5 | 23.5 |
| Accounts (25–28) | 7.5 | 11 |
| Polish (29–33) | 13.5 | 19.5 |
| Engineering (34–36) | 2 | 2.5 |
| **Everything** | **113** | **169** |
| Everything except content (16–20) | 65.5 | 94 |

With the 25% buffer, everything comes to roughly **140–210 developer-days** (about 7–10 months for one developer). Leaving out the content work, it's **about 82–118 days** (about 4–6 months).

---

## Detailed analysis

### Foundations

#### F1. Stable question IDs (1 day)
- **What:** give each question an ID that stays the same when banks are regenerated, e.g. a short hash of the question text and options, like `q-3fa9c1d2`.
- **Why:** bookmarks, wrong-answer tracking, topic stats and reports all point at a specific question. Today's IDs depend on position, so they'd point at the wrong question after the next run of `build_exams.py`.
- **How:** in `build_exams.py` and `build_questions.py`, compute the ID from the content and fail the build if two questions get the same ID. If a typo fix should keep the same ID, store the ID in the source banks instead.
- **Risk:** low. Do it before anything stores question IDs.

#### F2. Store each answer per question (1.5–2 days)
- **What:** save which option the user picked for every question, along with the attempt.
- **How:** add an `attempt_answer` table (attempt_id, question_id, topic, chosen, correct, seconds), or one JSON column on `quiz_attempt`. A separate table makes topic queries simple SQL; JSON is quicker to build. A table is recommended. Extend `POST /api/attempts` and `recordAttempt` in `quiz.js`.
- **Size:** about 50 rows per attempt is small, even with thousands of users.
- **Risk:** low.

### Exam realism

#### 1. Full mock tests (3–4 days)
- **What:** sit a whole Prelims or Mains paper. Prelims sections each have their own timer and lock when time is up, the way IBPS runs it. The result shows section-wise and overall scores.
- **How:** add a `mock` entry to `index.json` that lists a quiz from each section. `quiz.js` gains a section switcher, per-section timers and a combined results screen. That's a real refactor, because the current code assumes one section per quiz. A saved attempt either gets a `mockId` or becomes one parent attempt with child section attempts.
- **Depends on:** #2 (the palette is how you move around a long paper) and #5 (losing a 60-minute paper to a refresh is unacceptable).
- **Risk:** medium. Exam rules differ: Prelims has section timers, while some Mains sections allow moving between sections. Store a rule per exam.

#### 2. Question palette (1–1.5 days)
- **What:** a grid of numbered buttons coloured by state (not visited, answered, skipped, marked), with jumps to any question. This copies the real exam screen, so users practise on the same interface.
- **How:** in `quiz.js`, keep a `visited[]` array next to `answers[]` and render the grid next to the question (a drawer on mobile). Only in timed mode.
- **Risk:** low. The work is mostly making it fit on a phone screen.

#### 3. Mark for review (0.5 days)
- **What:** a "Mark for review & next" button. Marked questions show purple in the palette, and the submit confirmation counts them.
- **How:** a `marked[]` array plus a palette style. Follow IBPS's rule: a marked question that has an answer is still scored.

#### 4. Section cutoffs next to score (1–2 days)
- **What:** "Last year's sectional cutoff: 8.25. You: 12.5 ✓".
- **How:** add a `cutoffs` field per section in `build_exams.py`, passed through to `index.json`, and show it on the results screen.
- **Risk:** medium, because of the data. Cutoffs differ by state and category (GEN, OBC, SC/ST, EWS) and change every year. Show one reference figure marked "approximate", or add a state/category picker (+1 day). Most of the effort is finding and checking the numbers.

#### 5. Drift-proof timer and resume after refresh (0.5–1 day)
- **What:** (a) the timer counts down from a fixed end time, so throttled background tabs can't stretch it; (b) answers and the end time are saved in `localStorage` while the quiz runs, so a refresh resumes the quiz.
- **How:** `remaining = Math.round((endAt - Date.now()) / 1000)` on every tick, and save `{quizKey, endAt, answers, current}` on every change. If the end time has already passed when the user returns, submit automatically.
- **Risk:** low. This is a bug fix and should come early.

### Progress and analytics

#### 6. Topic-wise weak areas (2–3 days)
- **What:** on "My scores", accuracy per topic (e.g. Syllogism 82%, Boats & Streams 41%), the five weakest topics, and a practice link for each.
- **How:** a `GET /api/stats/topics` endpoint that groups `attempt_answer` rows by topic. Show topics as display names (`coding-decoding` → "Coding-Decoding") and hide topics with fewer than about 10 answers, because the percentages are meaningless on so few. The practice links work fully once #13 exists.
- **Depends on:** F1, F2. Java questions need topics tagged (+0.5 day) or are left out.
- **Risk:** low.

#### 7. Score trend chart (1 day)
- **What:** a line chart of score % over time, filterable by exam and section.
- **How:** uses data that already exists from `GET /api/attempts`. Use inline SVG or one small chart library from a CDN, keeping the site build-free.
- **Risk:** low.

#### 8. Time per question (1–1.5 days)
- **What:** average seconds per question per topic, and a "slow and wrong" list, which shows where speed or understanding is the bottleneck.
- **How:** `quiz.js` adds up the time spent on each question (going back and forth adds to it) and sends it in F2's `seconds` column.
- **Risk:** low. Time tracking stops while the tab is hidden, so idle time doesn't skew it.

#### 9. Completion badges and best score per quiz (1 day)
- **What:** on `exam.html`, each quiz button shows a tick and your best % (e.g. "3 ✓ 78%").
- **How:** a `GET /api/attempts/best` endpoint that groups by (exam, stage, section, level, quiz) and returns the best score, plus a style in `exam.js`.
- **Risk:** low. One of the cheapest ways to make progress visible.

#### 10. Streaks and daily goals (1–1.5 days)
- **What:** "🔥 6-day streak" and "2 of 3 quizzes today".
- **How:** worked out from attempt timestamps. Days must use India time (IST), not UTC, or a quiz at 1 a.m. lands on the wrong day. Store the goal on the user (default 2).
- **Risk:** low.

### Learning

#### 11. Retry wrong answers (2–3 days)
- **What:** a "Revise mistakes" quiz built from questions you got wrong and haven't since got right. It can later grow into proper spaced repetition (+2 days).
- **How:** an endpoint returns the IDs of questions you've got wrong. The client needs to find those questions, but they're spread across about 300 files (4.2 MB). The build script should write a lookup file (ID → question, or ID → file) so it doesn't need to fetch everything. `quiz.js` needs a mode that runs a list of questions instead of one file; that's the main refactor.
- **Depends on:** F1, F2.
- **Risk:** medium, because of the "list of questions" refactor, which #12, #13 and #14 also reuse. Build that refactor once.

#### 12. Bookmarks (1–1.5 days)
- **What:** a ☆ on each question and on the review screen, plus a "Bookmarked" list you can practise from.
- **How:** a `bookmark` table (user, question_id), add/remove endpoints, and the list-of-questions mode from #11.
- **Risk:** low.

#### 13. Custom quiz builder (2.5–3.5 days)
- **What:** pick topics, difficulty, number of questions and a time limit, e.g. "25 hard Seating Arrangement, 20 minutes".
- **How:** the build script writes topic pool files (`data/topics/<topic>-<level>.json`) so the browser fetches only what it needs. A builder page plus the #11 mode. The time limit is per quiz, not taken from a section.
- **Risk:** medium. Some topic/level combinations have few questions, so show how many are available before starting.

#### 14. Daily quiz (1–1.5 days)
- **What:** the same 10 mixed questions for everyone each day, with a streak tie-in.
- **How:** choose the questions from a fixed seed (e.g. today's date) in the browser, with no server job needed. The attempt is saved with `examId=daily`. A "today's ranking" for it would need #21.
- **Risk:** low.

#### 15. Step-by-step solutions (5–8 days)
- **What:** worked solutions instead of one-line explanations, plus shortcut tips (e.g. the alligation cross method).
- **How:** change the explanation text in each of the 46 generators in `gen_banks.py`. The numbers are already known when each question is generated, so solutions can show real working. Allow multiple lines in the explanation box. Reasoning questions (seating, syllogism) are the hardest to explain. English explanations are written by hand.
- **Risk:** medium. 46 generators × about 1 hour each, plus checking. It can be done one generator at a time.

### Content

#### 16. General/Financial Awareness and Computer Knowledge (15–25 days)
- **What:** fill the current "coming soon" sections.
- **Analysis:** these can't be generated. Computer Knowledge is fairly fixed (hardware, MS Office, networking, shortcuts), so 600 questions can be written once (about 6–8 days including review). General/Financial Awareness splits into fixed banking topics (RBI, monetary policy, banking terms: write once) and **current affairs, which go out of date every month**. Current affairs needs either an ongoing monthly job (~2 days a month) or has to be left out.
- **Risk:** high. Wrong facts hurt trust. Every question needs a source and a person to check it. AI-drafted questions especially need fact-checking, because confident mistakes on dates and figures are common.

#### 17. More exams: IBPS PO, SBI PO, RBI Assistant (3–5 days each)
- **What:** new exam entries with their patterns.
- **Analysis:** adding an exam to the `EXAMS` list in `build_exams.py` takes about half a day. The real work is difficulty: PO exams are harder than clerk exams (data interpretation, harder puzzles, reading comprehension), and the current banks don't include those question types. RBI Assistant is close to the clerk pattern (~3 days). The PO exams need #19 first. PO Mains also has a descriptive (essay/letter) paper that this quiz format can't mark.
- **Risk:** medium. Exam patterns change, so check each new notification.

#### 18. Hindi language option (20–30 days)
- **What:** an English/Hindi switch on each question, as in the real exam.
- **Analysis:** generated Quant and Reasoning questions can be translated by adding Hindi templates to the generators. That's cheap and reliable for numbers, harder for reasoning wording (~8–10 days). English Language stays English. Section #16 content would also need translating. The frontend needs a language field on each question and a font that renders Hindi script.
- **Risk:** high. Needs a fluent reviewer for exam-style Hindi terms; literal translation reads badly.

#### 19. New question types: DI, puzzles, reading comprehension (8–12 days)
- **What:** questions that share a set-up, such as a table or chart with 5 questions, a seating puzzle with 5 questions, or a passage with 6 questions.
- **How:** a schema change adds a `group` (passage/table/image) that several questions point at. `quiz.js` shows the set-up beside the question and keeps the group's questions together. Generators for DI tables and puzzles; hand-written RC passages. Section patterns need to say how many questions come from groups.
- **Risk:** medium. This is the biggest gap compared with real papers and unlocks the PO exams (#17).

#### 20. Separate question pools per exam (1.5–3 days, plus English writing)
- **What:** stop the ~2,000 questions that currently appear in more than one exam (e.g. IBPS and SBI Prelims Reasoning).
- **Analysis:** for generated sections, raise the per-level count in `gen_banks.py` and give each exam its own slice (1.5 days). The risk is near-duplicates: some generators have limited variety, so add a check for similar questions. **English:** the bank is already fully used (200 per level), so three separate pools mean writing about 1,200 more English questions, which is content work, not code.
- **Risk:** medium. Lower priority, because few users practise for several exams at once.

### Competitive

#### 21. Server-side scoring and leaderboards (6–8 days)
- **What:** fair scores that can be compared, which leaderboards need.
- **How:** move answers off public JSON. The server keeps the full banks. Questions are served without answers. Users submit their choices and the server scores them. Practice mode's instant feedback becomes a call to a "check this answer" endpoint. The static data folder keeps only questions without answers, so the site can't be hosted as purely static files anymore. Then add leaderboards per quiz and per week.
- **Risk:** high. This is the biggest architecture change in the list. Each exam question becomes an API call, so caching and rate limiting are needed. The time limit has to be checked on the server too (record the start time when questions are served).

#### 22. Live quiz contests with rank (8–12 days)
- **What:** a scheduled contest (e.g. Sunday 10 a.m.) where everyone sits the same paper, followed by an all-India rank and percentile.
- **How:** contest records and registration, questions released at the start time, server-side timing (from #21), ranking afterwards, and a results page. Sudden load at the start time affects how Railway is sized.
- **Risk:** high. Cheating (sharing answers during the contest) and load. Mitigate by shuffling question and option order per user.

#### 23. Share result card (1–1.5 days)
- **What:** a "Share" button that makes an image of your score ring and stats (good for WhatsApp, where this audience shares things).
- **How:** draw it on a `<canvas>` and use `navigator.share` with the image, falling back to a download.
- **Risk:** low.

#### 24. Challenge a friend (1.5–2 days)
- **What:** send a link to the same quiz. Both scores appear side by side.
- **How:** a challenge record (id, quiz, creator), a link `quiz.html?challenge=<id>`, and a comparison screen. Without #21 a score can be faked, which is acceptable between friends.
- **Risk:** low.

### Account extras

#### 25. Forgot password by email (2–3 days)
- **What:** reset your password with an email link.
- **How:** a reset-token table (store a hash of the token, 30-minute expiry, single use), request and confirm endpoints, two pages, and a real email provider such as SES, Resend or Brevo, replacing the Mailtrap sandbox. Return the same response whether or not the email exists, so it can't be used to check who has an account, and rate-limit requests.
- **Risk:** medium. Getting email delivered (domain setup, DNS records) and landing in inboxes rather than spam takes time outside the code.

#### 26. Google sign-in (2–3 days)
- **What:** "Continue with Google".
- **How:** Spring Security OAuth2 client, an `auth_provider` field on `User`, and linking to an existing account with the same verified email. Usernames would be generated automatically for Google sign-ups.
- **Risk:** medium. Account-linking edge cases, and a Google Cloud project with a consent screen to set up.

#### 27. Profile page (1.5–2 days)
- **What:** change display name or email, change password (asks for the current one), and delete your account and all its data.
- **How:** `PUT /api/me`, `POST /api/me/password`, `DELETE /api/me` (removes attempts too). Changing the password should log out other sessions.
- **Risk:** low. Account deletion is also good practice under India's data protection law (DPDP Act).

#### 28. Email reminders and weekly summary (2–3 days)
- **What:** "You haven't practised in 3 days" and a Monday progress email.
- **How:** `@Scheduled` jobs (the old commented-out scheduler showed this pattern), email templates, a one-click unsubscribe link (required for bulk email), and opt-in settings on the profile.
- **Depends on:** #25 (email provider).
- **Risk:** medium. Marked as spam if not done carefully. With more than one server running, a job could send duplicate emails.

### Polish

#### 29. Installable app with offline practice (3–4 days)
- **What:** install from the browser onto the phone home screen; practice works without internet; results finished offline sync later.
- **How:** a web app manifest and icons, plus a service worker that caches the pages and the quiz data. Caching all 4.2 MB of data plus 2.2 MB of images is acceptable, or cache only quizzes the user has opened. Results finished offline are kept on the device and uploaded on reconnect.
- **Risk:** medium. Old cached files can hang around after a deploy, so give cache names a version. This matters for users on patchy mobile data.

#### 30. Dark mode (1–1.5 days)
- **How:** `style.css` already uses colour variables, but `quiz.css` has many fixed colours (`#fff`, `#dcfce7`, `#eef2ff`, …) that need converting to variables, then a dark set under `prefers-color-scheme` plus a toggle.
- **Risk:** low. The photo backgrounds already suit a darker theme.

#### 31. Keyboard shortcuts (0.5 days)
- **What:** 1–4 or A–D to pick, Enter or N for next, M to mark, S to submit.
- **Risk:** low. Ignore keys while the user is typing in an input.

#### 32. Admin question editor (8–12 days)
- **What:** add, edit or remove questions in the browser, with admin-only access.
- **Analysis:** today the source of truth is generator code and bank files, built into static JSON. An editor fights that pipeline: a generated question edited by hand is overwritten on the next run. Options: (a) the database becomes the source for hand-written content (English, GA, Computer), the generators stay for Quant and Reasoning, and edits are stored as overrides by stable ID; or (b) the editor writes bank files and opens a PR. Option (a) is the realistic one. It needs admin roles, CRUD endpoints, an editor screen, and a publish step that rebuilds the JSON.
- **Risk:** high. Most useful once #16 or #33 produces a steady flow of hand-edited content. Until then, editing JSON files is cheaper.

#### 33. Report a wrong question (1–1.5 days)
- **What:** a "Report" link on each question and on the review screen, with a reason (wrong answer, typo, unclear).
- **How:** a `question_report` table and a simple admin list (or just an email to you at first).
- **Risk:** low. Cheap quality control for 10,000 generated questions.

### Engineering clean-up

#### 34. Remove dead code and unused dependencies (0.5 days)
Delete the commented-out scheduler, email service and old repository, the unused `Question` class, `MailConfig` (until #25 needs it), `WebConfig` (Spring Boot already does what it does), WebFlux, Lombok (nothing uses it, and it breaks compilation on JDK 27), and the committed `.DS_Store` files. This gives faster builds and less confusion.

#### 35. Fix Maven wrapper and real CI deploy (0.5–1 day)
Commit `.mvn/wrapper/`, make `mvnw` executable, and replace the placeholder Railway project ID in `review-app-deploy.yml`. Optionally deploy automatically when tests pass on `main`.

#### 36. Database migrations with Flyway (1 day)
Write the current `app_user` and `quiz_attempt` tables as `V1__init.sql`, switch to `ddl-auto=validate`, and make every later schema change (F2, #12, #21 …) a numbered migration. **Do this before real users sign up in production.** Changing schemas with `update` on a live database can silently fail to apply a change or leave the database inconsistent.

---

## Suggested order

| Phase | Features | Days (low–high) | Outcome |
|---|---|---|---|
| 0. Foundations | 34, 35, 36, F1, F2, 5 | 5–6.5 | Safe to launch accounts; timer bug fixed |
| 1. Real exam screen | 2, 3, 31, 9, 7 | 4–4.5 | Feels like the real exam; progress visible |
| 2. Learning loop | 6, 8, 11, 12, 33, 13 | 9.5–14 | Find weak topics, practise them, fix bad questions |
| 3. Engagement and accounts | 27, 25, 10, 14, 23, 30 | 7.5–11 | Users come back daily; accounts complete |
| 4. Full mocks and mobile | 1, 4, 29 | 7–10 | Full-paper practice, offline on phones |
| 5. Competitive | 21, 24, 22, 26, 28 | 19.5–28 | Fair rankings and contests |
| 6. Content (ongoing) | 19, 17, 15, 20, 16, 18, 32 | 60.5–95+ | PO exams, all sections, Hindi |

**Phases 0–2 (about 18–25 days)** give the biggest improvement for exam preparation using only the content that already exists. Phase 5 is the most expensive engineering work and only pays off once there are enough users for rankings to matter, so it's worth checking usage before starting it.
