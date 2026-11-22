# QuizVerse Feature Roadmap

What to build next, with an estimate, dependencies, risks and the evidence for each feature. Written 22 Nov 2026 from research into Indian exam-prep platforms, aspirant reviews, exam candidate numbers and learning science (sources at the end).

**Status (22 Nov 2026): the first roadmap (phases 0–6) is complete.** Everything on it is built and live; it's kept, with its history and estimates, in [`docs/archive/ROADMAP-2026-10-phases-0-6.md`](archive/ROADMAP-2026-10-phases-0-6.md). Since that roadmap closed, content has grown well beyond its plan:

- **8 exams:** IBPS Clerk, SBI Clerk, IBPS RRB Clerk, **IBPS RRB PO** (added 22 Nov 2026), IBPS PO, SBI PO, RBI Assistant and **APPSC Group 1 Prelims** (two papers, 1/3 negative marking).
- **No "coming soon" bank-exam sections left:** Computer Knowledge and General/Financial/Banking Awareness have full quizzes and are in the mocks. APPSC Current Events stays "coming soon" on purpose: it goes out of date (see N16).
- **Deeper banks:** a second English bank and second generated Quant/Reasoning banks give the clerk exams 10 quizzes per level and every exam 10 English quizzes. APPSC Group 1 has 5 quizzes per level in every section.
- **Tech Interview Prep has 15 subjects**, 180 questions each.
- About 21,500 unique questions in about 400 practice topics. All hand-written GA, Computer and APPSC content is still marked **Draft** until a person checks it.

## How to read the estimates

- **Unit:** developer-days for one developer who knows this codebase, including tests and a browser check. Add about 25% for review, fixes and deployment.
- **Size:** S = 3 days or less, M = 1–2 weeks, L = more.
- **[C]** needs ongoing content work (writing or checking questions). **[$]** costs money to run.
- Content estimates count drafting with AI help **plus** a person's review; the review is the slow part.

## What the current code means for these features

| Fact | Why it matters |
|---|---|
| Every answer is stored in `attempt_answer` (question, topic, chosen option, correct, seconds). | Spaced repetition (N1), the attempt-strategy report (N3) and adaptive practice (N5) need no new data collection. |
| Timed exam quizzes and mocks are scored on the server from `answer-keys.json`; verified results carry a receipt. Contests already compute rank and percentile. | Percentile on every mock (N7) and a weekly all-India mock (N8) reuse `QuizCatalog`, `QuizTimer` and the contest code. |
| Exams are declared in `scripts/build_exams.py` (`EXAMS`): sections, counts, timers, per-exam negative marking, quizzes per level, and which generated slice each exam uses. Banks grow in separate blocks so published quizzes never change. | A new exam (N9–N13) is mostly configuration plus whatever content its syllabus adds. Generated Quant is close to its limit (about 775 new medium questions were left after the clerk slices), so new exams need new Quant templates (geometry, mensuration, algebra, trigonometry) in `gen_banks.py`. |
| Hand-written banks live in `scripts/ga_bank/<subject>/` and are validated at build time; `needsReview` drives the Draft badge. The admin editor stores edits by question id. | A review workflow (N22) extends what exists: who reviewed what, and when the badge comes off. |
| The site is static HTML plus JSON served by Spring Boot; pages have no meta descriptions, there's no sitemap, and only 4 pages use `aria-` attributes. | SEO (N23) and accessibility (N24) are real gaps, not polish. |
| Sessions are in server memory; scheduled jobs (email) assume one instance. Production uses Aiven's free PostgreSQL (1 GB, 20 connections). | Stay on one instance. A big weekly mock (N8) or many new users means a paid database plan and connection pooling first. |
| No analytics are installed. | Nobody can tell which features or exams are used. Add privacy-friendly analytics (N25) before choosing the phase after next. |

---

## Summary table

| # | Feature | Area | Value | Size (days) | Depends on | Risk |
|---|---|---|---|---|---|---|
| N1 | Spaced-repetition review queue | Learning | ★★★ | M (5–7) | none | Low |
| N2 | Interleaved "mixed" practice mode | Learning | ★★ | S (1–2) | none | Low |
| N3 | Attempt-strategy report after mocks | Analytics | ★★★ | S (2–3) | none | Low |
| N4 | Re-attempt wrong answers before the key | Learning | ★★ | S (1–2) | none | Low |
| N5 | Adaptive topic mastery (difficulty rating) | Learning | ★★ | M (6–9) | N1 | Medium |
| N6 | Study plan to an exam date | Learning | ★★ | M (5–8) | N1, N17 | Medium |
| N7 | Percentile and rank on every verified mock | Benchmarking | ★★★ | S (2–3) | none | Low (small user base) |
| N8 | Weekly free all-India mock | Benchmarking | ★★ | S (2–3) | N7 | Medium (DB load) |
| N9 | SSC CGL Tier 1 | New exam | ★★★ | M (8–12) [C] | Quant templates | Medium |
| N10 | SSC CHSL | New exam | ★★ | S (2–3) [C] | N9 | Low |
| N11 | RRB NTPC CBT 1 | New exam | ★★★ | M (6–10) [C] | N9 templates | Medium |
| N12 | LIC AAO, IBPS SO, NABARD Grade A | New exam | ★★ | M (4–6 each) [C] | none | Medium |
| N13 | TGPSC Group 2 and APPSC Group 2 | New exam | ★★ | M (8–12) [C] | Telangana reviewer | High (accuracy) |
| N14 | UPSC CSE Prelims | New exam | ★★ | L (25+) [C] | N16, N22 | High |
| N15 | Official previous-year papers as mocks | Content | ★★★ | M (2–3 per paper) [C] | none | Medium (copyright, transcription) |
| N16 | Weekly current-affairs quiz | Content | ★★ | M (4–5) + weekly [C] | N22 | High (needs an owner) |
| N17 | Exam calendar and alerts | Engagement | ★★ | S–M (3–5) [C] | none | Low |
| N18 | Placement-aptitude track (TCS NQT, Infosys) | Tech | ★★ | S (2–3) | none | Low |
| N19 | Curated tech study paths | Tech | ★ | S (1–2) | none | Low |
| N20 | Code-reading / output-prediction MCQs | Tech | ★ | S (2–3) [C] | none | Low |
| N21 | Hindi review and expansion; Telugu for AP/TS | Language | ★★ | L (20–30) [C][$] | N22 | High (reviewers) |
| N22 | Content review workflow | Quality | ★★★ | M (5–7) | none | Low |
| N23 | SEO: indexable topic pages, meta, sitemap | Growth | ★★★ | M (5–8) | none | Medium |
| N24 | Accessibility (WCAG 2.2) incl. extra-time option | Quality | ★★★ | S–M (3–6) | none | Low |
| N25 | Privacy-friendly analytics (Umami) | Growth | ★★ | S (1) | none | Low |
| N26 | DPDP Act compliance | Legal | ★★ | S–M (3–5) | none | Medium (deadline 13 May 2027) |
| N27 | Keep-it-free pledge, donations, optional AI explainer | Sustainability | ★ | S (1–2), AI M [$] | N25 | Medium |

**Value key:** ★★★ = clearly helps users pass or brings them back; ★★ = useful; ★ = nice to have.

---

## Detailed analysis

### A. Learning engine (uses data already stored, no new content)

#### N1. Spaced-repetition review queue (5–7 days)
Turn "Revise mistakes" into a daily **due queue**: each wrong, skipped or bookmarked question gets a next-review date that grows each time you answer it right (an SM-2 or FSRS schedule), and slips back when you miss it. Home page shows "12 questions due today".
- **Evidence:** Dunlosky et al. (2013) rated practice testing and distributed practice the only two "high utility" study techniques; Cepeda et al. (2006, 839 assessments) found spacing gains grow with the retention interval. FSRS is open source with a JavaScript port.
- **Build:** a `review_schedule` table (user, question id, due date, interval, ease) via a Flyway migration; update it when answers are saved; a due-queue endpoint and page. Works offline for already-cached questions.
- **Risk:** low. Keep the first version simple (SM-2); FSRS can replace it later.

#### N2. Interleaved "mixed" practice mode (1–2 days)
An option in the custom quiz builder that mixes similar topics (simple interest, compound interest, profit and loss) question by question instead of in blocks.
- **Evidence:** Brunmair & Richter (2019) meta-analysis: g = 0.42 overall, 0.34 for maths, strongest when categories look alike; no benefit for word lists, so keep vocabulary blocked.
- **Build:** a shuffle mode in the builder; group "similar" topics with a small mapping.

#### N3. Attempt-strategy report after mocks (2–3 days)
After a mock, sort questions into **correct-fast, correct-slow, wrong, skipped**; show time sunk into wrong answers, and questions you skipped that most others got right. One line of advice per section ("you spent 9 minutes on 6 wrong DI questions").
- **Evidence:** Oliveboard's analysis shows speed vs accuracy and time on correct/wrong/unattempted questions; mock-strategy guides tell aspirants to sort questions exactly this way.
- **Build:** all inputs are in `attempt_answer` and the server score; "others got right" needs an aggregate query per question (cache it).

#### N4. Re-attempt wrong answers before the key (1–2 days)
After submitting, retry only the questions you got wrong before answers and explanations show, then see both attempts.
- **Evidence:** Oliveboard's "Reattempt" and Testbook's reattempt mode; a Quora review named Oliveboard's clumsy re-attempt flow as its main weakness.
- **Build:** a result-screen mode in `quiz.js`; the server already returns the key only on request.

#### N5. Adaptive topic mastery (6–9 days)
Give each question an Elo-style difficulty from everyone's answers and each user a per-topic rating; "Practise this topic" serves questions near the user's level and shows a mastery bar.
- **Evidence:** Embibe's adaptive practice is its headline feature.
- **Risk:** medium: ratings are noisy until each question has enough answers; fall back to the authored level.

#### N6. Study plan to an exam date (5–8 days)
Pick an exam and its date (from N17); get a daily plan: topics to cover, the day's due reviews (N1) and a mock every few days, adjusting to weak topics.
- **Evidence:** LeetCode study plans; IBPS publishes an annual exam calendar that fixes the dates.

### B. Benchmarking

#### N7. Percentile and rank on every verified mock (2–3 days)
Show "you scored better than 72% of 340 attempts" and topper/average comparisons on every server-verified quiz and mock, not just contests.
- **Evidence:** Oliveboard, PracticeMock and ixamBee lead with all-India rank; their own advice is to watch percentile, not raw marks.
- **Build:** the contest code already computes percentiles; run it over verified attempts per quiz key. Say "among N attempts" honestly while numbers are small.

#### N8. Weekly free all-India mock (2–3 days)
A recurring Sunday contest on a rotating exam's full mock, created automatically, with results the same evening.
- **Evidence:** Testbook runs daily live tests with rank; it's the strongest habit-forming feature competitors have.
- **Risk:** medium: a single start time is a load spike; needs the paid database plan and pooling noted above.

### C. New exams (ranked by candidates)

All reuse the existing mock engine. "Coverage" is how much of the paper existing banks can fill.

#### N9. SSC CGL Tier 1 (8–12 days) [C]
About 28 lakh applicants. Four sections of 25 questions (Reasoning, GA, Quant, English), 0.5 negative marking, and 15-minute section timers from 2026, which the engine already supports.
- **Coverage:** about 70% (reasoning, arithmetic, English, static GK). **Missing:** geometry, trigonometry, algebra and mensuration templates in `gen_banks.py`; SSC-style English (cloze, spotting) and non-verbal reasoning (figure series need images, so start with verbal-only).

#### N10. SSC CHSL (2–3 days once N9 exists) [C]
Same sections as CGL at an easier level.

#### N11. RRB NTPC CBT 1 (6–10 days) [C]
1.21 crore applied in 2025. 100 questions: 40 GA, 30 Maths, 30 Reasoning, no English.
- **Coverage:** about 65%. Maths and Reasoning reuse the generators (with N9's new templates); GA can draw on the APPSC polity, history, geography and science banks. Needs Class 10 science and railways-style GK.

#### N12. LIC AAO, IBPS SO, NABARD Grade A (4–6 days each) [C]
Bank-PO-like sections, about 80% reuse. Add insurance awareness (LIC), professional-knowledge sections (SO, start with IT Officer) and agriculture/rural development (NABARD).

#### N13. TGPSC Group 2 and APPSC Group 2 (8–12 days) [C]
About 5.5 lakh TGPSC applicants. About 60% reuse of the APPSC Group 1 banks; Telangana history, movement and economy are new. **Needs a Telangana reviewer** before release.

#### N14. UPSC CSE Prelims (25+ days) [C]
9.4 lakh applicants. Existing GS banks cover perhaps 40%; the quality bar is very high and current affairs is a large share. Only after N16 and N22 exist.

### D. Authentic papers and current affairs

#### N15. Official previous-year papers as mocks (2–3 days per paper) [C]
UPSC, APPSC and SSC publish papers **and official answer keys**, so answers are verified, not AI-written. Load them as timed mocks labelled with the year.
- **Start with APPSC Group 1** (2026 notification is out) and SSC CGL.
- IBPS and SBI papers are only memory-based: leave them out or label them clearly.
- **Risk:** check reuse terms per commission; transcription needs a careful second read.

#### N16. Weekly current-affairs quiz (4–5 days of code, then about 2 hours a week) [C]
About 25 questions a week from PIB and RBI releases, plus a monthly revision test; fills APPSC's "coming soon" Current Events section and NTPC/CGL GA.
- **Evidence:** CA is 5–7 questions in CGL GA and 9–11 in NTPC; Testbook, Adda247, PracticeMock and ixamBee all give it away free.
- **Risk:** high unless someone owns the weekly writing and review. Questions must carry their date and expire from mocks after about 12 months.

#### N17. Exam calendar and alerts (3–5 days) [C]
A calendar page of upcoming notifications, admit cards and exam dates; users follow exams and get an email (existing `Mailer`) or web-push alert.
- **Example timing:** IBPS RRB Clerk Prelims 6–13 Dec 2026; IBPS Clerk Mains 27 Dec 2026.
- Small curation each month; only list dates from official notices.

### E. Tech track

#### N18. Placement-aptitude track (2–3 days)
TCS NQT, Infosys, Wipro-style aptitude sets built from the existing Quant, Reasoning and English banks, as a section of Tech Interview Prep.
- **Evidence:** the r/developersIndia wiki and placement guides point freshers to IndiaBix and PrepInsta for aptitude alongside DSA.

#### N19. Curated tech study paths (1–2 days)
"Backend in 4 weeks", "Frontend essentials", "Top 100 DSA" ordered sets across the 15 subjects, like LeetCode's study plans.

#### N20. Code-reading and output-prediction MCQs (2–3 days) [C]
"What does this print?" questions verified by actually running the code at build time. A full coding judge is **not** recommended: it needs a sandbox (cost, security) and LeetCode/GfG already own that space.

### F. Language

#### N21. Hindi review and expansion; Telugu for AP/TS exams (20–30 days) [C][$]
Get the existing Hindi drafts reviewed, extend Hindi to Reasoning and GA, and add Telugu for APPSC/TGPSC.
- **Evidence:** Adda247 markets itself as India's largest vernacular platform (12+ languages).
- **Risk:** high: needs paid or volunteer fluent reviewers; don't ship unreviewed translations.

### G. Platform, quality and growth

#### N22. Content review workflow (5–7 days)
A **reviewer** role; a queue of Draft questions sorted by report count and usage; "mark reviewed" records who and when and removes the Draft badge at the next build; automated consistency checks (duplicate answers, option-length giveaways, dated wording like "is being built").
- **Why now:** thousands of GA, Computer, APPSC and Hindi questions are Draft; every new exam adds more. Credibility is what a free app competes on; ixamBee advertises its review of reported questions.

#### N23. SEO: indexable topic and question pages (5–8 days)
Server-rendered topic pages with a few sample questions and full explanations, meta descriptions, canonical URLs and a sitemap. Google retired Practice Problem rich results in January 2026, so plain crawlable explanations are what ranks; don't rely on structured data.
- **Risk:** medium: keep exam-quiz answers private; only practice-pool questions get public pages.

#### N24. Accessibility, WCAG 2.2 (3–6 days)
- **Extra time:** a "compensatory time" option on timed quizzes (WCAG 2.2.1 "timing adjustable"; RRB gives eligible PwBD candidates 120 minutes instead of 90). Unranked when used.
- Time warnings in an `aria-live` region (at 5 and 1 minutes, not every second).
- Palette and options fully usable by keyboard and screen reader; contrast fixes in both themes.

#### N25. Privacy-friendly analytics (1 day)
Umami (MIT, runs on PostgreSQL, free self-hosted or a free cloud tier) or GoatCounter. Track sign-ups, quizzes finished, 7-day return rate and which exams are used. No cookies banner needed for these tools.

#### N26. DPDP Act compliance (3–5 days)
Privacy notice, recorded consent at sign-up, a parental-consent path for under-18 users, data export and deletion (deletion exists), and a breach-response note. Full obligations apply from **13 May 2027**.

#### N27. Keep-it-free pledge, donations, optional AI explainer (1–2 days; AI explainer M [$])
A public promise of no sales calls, no auto-pay and no ads; an optional donation/sponsor page. An "explain this differently" AI button would cost money per use, so only add it rate-limited and funded.
- **Evidence:** the top complaints about Testbook and Adda247 are UPI auto-pay debits, repeated sales calls and ads to paying users; Khan Academy runs mostly on donations.

---

## Suggested order

| Phase | Features | Days (low–high) | Outcome |
|---|---|---|---|
| 7. Learn smarter | N1, N2, N3, N4 | 9–14 | Daily review queue, mixed practice, mock analysis that changes how people attempt |
| 8. Benchmark and measure | N7, N8, N25, N24 | 8–13 | Percentile on every mock, a weekly habit, real usage data, accessible timers |
| 9. Biggest new audiences | Quant templates, N9, N10, N11 | 16–25 | SSC CGL, CHSL and RRB NTPC (about 1.5 crore aspirants a year) |
| 10. Trust and reach | N22, N15, N23, N26 | 15–23 | Reviewed content, official past papers, search traffic, DPDP-ready before May 2027 |
| 11. Breadth (ongoing) | N12, N17, N18, N19, N20, N5, N6 | 25–40 | More exams, exam calendar, placement prep, adaptive practice |
| 12. Needs an owner or funding | N16, N21, N13, N14, N27 | 60+ | Current affairs, Hindi/Telugu, state Group 2, UPSC |

**Phases 7–8 (about 17–27 days)** need no new content: they make the existing 20,000 questions work harder and give the usage data to decide phase 9 onwards. Phase 9 is the biggest growth step but adds a lot of Draft content, so start N22 (review workflow) alongside it.

### Deferred, and why

- **Weekly current affairs (N16):** a permanent weekly commitment; only start when someone owns it.
- **Hindi/Telugu (N21):** blocked on fluent reviewers.
- **Coding judge:** expensive to run safely, and the space is crowded; N20 gives most of the value.
- **UPSC (N14):** L-sized with a high quality bar; after N16 and N22.

---

## Sources

**Competitor features:** [Oliveboard mock analysis](https://www.oliveboard.in/blog/ai-based-mock-test-analysis/) · [Oliveboard live-test analysis help](https://oliveboard7078.zendesk.com/hc/en-us/articles/12771997194513-How-to-Analyse-My-Live-Test-Performance) · [Testbook Pass](https://testbook.com/pass) · [Testbook free live tests](https://testbook.com/free-live-tests-and-quizzes) · [PracticeMock](https://www.practicemock.com/ibps-po-test-series/) · [ixamBee free mocks](https://www.ixambee.com/free-mock-tests) · [Embibe adaptive practice](https://www.embibe.com/in-en/best-education-content/dynamic-and-adaptive-practice-to-increase-competency-levels/) · [Adda247](https://www.adda247.com/) · [LeetCode study plans](https://leetcode.com/studyplan/) · [GfG problem of the day](https://www.geeksforgeeks.org/problem-of-the-day)

**Aspirant feedback:** [Quora: Oliveboard vs Testbook](https://www.quora.com/Which-is-the-best-mock-test-series-for-banking-Oliveboard-or-Testbook) · [Adda247 Play Store](https://play.google.com/store/apps/details?id=com.adda247.app&hl=en_IN) · [Adda247 reviews](https://adda247.pissedconsumer.com/review.html) · [Testbook reviews](https://testbook.pissedconsumer.com/review.html) · [Testbook Trustpilot](https://www.trustpilot.com/review/testbook.com) · [Mock test strategy](https://prepmatic.in/exams/banking/mock-test-strategy/) · [r/developersIndia wiki](https://wiki.developersindia.in/faqs/tech-interviews) · [Placement prep sites 2026](https://placd.in/blog/best-placement-preparation-websites-india-2026)

**Exam numbers and patterns:** [SSC CGL 28 lakh applications](https://www.adda247.com/exams/ssc/ssc-received-28-lakh-applications-for-cgl-posts-against-14582-vacancies/) · [PIB release](https://www.pib.gov.in/PressReleasePage.aspx?PRID=2261451&reg=48&lang=2) · [SSC CGL 2026 pattern](https://www.adda247.com/jobs/ssc-cgl-syllabus-and-exam-pattern-2026/) · [RRB NTPC applicants](https://www.careerpower.in/blog/12167679-candidates-applied-for-rrb-ntpc-2025-exam) · [RRB NTPC syllabus](https://www.careerpower.in/rrb-ntpc-syllabus.html) · [UPSC applicants](https://bharatnama.substack.com/p/146-how-does-upsc-pick-958-from-nine) · [TGPSC Group 2](https://www.deccanchronicle.com/southern-states/telangana/telangana-group-ii-results-released-tgpsc-publishes-general-rankings-and-scores-1866230) · [LIC AAO applicants](https://www.bankersadda.com/516000-candidates-applied-for-lic-aao-and-ae-posts/) · [IBPS calendar 2026-27](https://www.ibps.in/wp-content/uploads/IBPS_CALENDAR_2026-27_final.pdf)

**Official papers and keys:** [UPSC previous papers](https://www.upsc.gov.in/examinations/previous-question-papers) · [APPSC question papers](https://portal-psc.ap.gov.in/HomePages/QuestionPapersofVariousNotifications) · [APPSC keys](https://portal-psc.ap.gov.in/HomePages/KeysToPapers) · [SSC answer keys](https://ssc.gov.in/home/answer-key)

**Learning science:** [Dunlosky et al. 2013](https://pubmed.ncbi.nlm.nih.gov/26173288/) · [Cepeda et al. 2006](https://pubmed.ncbi.nlm.nih.gov/16719566/) · [Brunmair & Richter 2019](https://www.psychologie.uni-wuerzburg.de/fileadmin/06020400/2019/Brunmair_Richter_in_press__2019_META-ANALYSIS_OF_INTERLEAVED_LEARNING.pdf) · [FSRS](https://github.com/open-spaced-repetition/free-spaced-repetition-scheduler)

**Platform, privacy, sustainability:** [Google practice-problem rich results](https://developers.google.com/search/docs/appearance/structured-data/practice-problems) · [WCAG timing adjustable](https://www.getstark.co/wcag-explained/operable/enough-time/timing-adjustable/) · [Umami and alternatives](https://umami.is/blog/best-self-hosted-web-analytics-tools) · [Plausible vs Umami vs GoatCounter](https://inimino.org/plausible-vs-umami-vs-goatcounter-privacy-first-analytics-compared-for-2026/) · [DPDP Rules timeline](https://legismith.com/dpdp-rules-2025-timeline/) · [Khan Academy funding](https://fourweekmba.com/how-does-khan-academy-make-money/)

Some figures come from coaching sites that also sell prep; only their factual claims (candidate counts, published patterns) are used here. Check each exam's official notification before building it.
