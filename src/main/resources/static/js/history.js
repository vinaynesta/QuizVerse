import { currentUser, listAttempts, listBookmarks, mistakes, saveAttempt, setDailyGoal, streak, topicStats } from "./api.js";
import { flushOfflineAttempts } from "./pending.js";
import { loadTopics, topicName } from "./questions.js";

const $ = (id) => document.getElementById(id);
const MIN_ANSWERS = 10; // below this, a topic's accuracy is shown but not ranked

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

const pct = (a) => (a.maxMarks > 0 ? Math.max(0, (a.score / a.maxMarks) * 100) : 0);
const num = (x) => Number(x.toFixed(2));
const plural = (n, word) => `${n} ${word}${n === 1 ? "" : "s"}`;

function quizLink(a) {
  if (a.kind === "mock") {
    return `quiz.html?${new URLSearchParams({ exam: a.examId, stage: a.stageId, level: a.levelId, mock: a.quizIndex })}`;
  }
  if (a.kind && a.kind !== "exam") return a.kind === "custom" ? "practice.html" : `quiz.html?practice=${a.kind}`;
  const q = new URLSearchParams({
    exam: a.examId, stage: a.stageId, section: a.sectionId, level: a.levelId, quiz: a.quizIndex,
  });
  return `quiz.html?${q}`;
}

// ---------- Summary tiles ----------

function renderSummary(attempts) {
  const avg = attempts.reduce((sum, a) => sum + pct(a), 0) / attempts.length;
  const best = Math.max(...attempts.map(pct));
  const answered = attempts.reduce((s, a) => s + a.correct + a.wrong, 0);
  const correct = attempts.reduce((s, a) => s + a.correct, 0);
  const tiles = [
    [attempts.length, "Quizzes taken"],
    [`${avg.toFixed(0)}%`, "Average score"],
    [`${best.toFixed(0)}%`, "Best score"],
    [`${answered ? ((correct / answered) * 100).toFixed(0) : 0}%`, "Accuracy"],
  ];
  $("history-summary").innerHTML = tiles
    .map(([value, label]) => `<div class="stat-tile"><b>${value}</b><span>${label}</span></div>`)
    .join("");
}

// ---------- Streak and daily goal ----------

function renderStreak(s) {
  $("streak-card").hidden = false;
  $("streak-current").textContent = s.current ? `${plural(s.current, "day")} streak` : "No streak yet";
  $("streak-best").textContent = s.current === 0
    ? "Finish a quiz today to start one."
    : s.today > 0 ? `Best: ${plural(s.best, "day")}` : `Best: ${plural(s.best, "day")}. Finish a quiz today to keep it going.`;
  $("goal-text").textContent = s.today >= s.goal
    ? `Goal reached: ${s.today} of ${s.goal} today`
    : `${s.today} of ${s.goal} ${s.goal === 1 ? "quiz" : "quizzes"} today`;
  $("goal-fill").style.width = `${Math.min(100, (s.today / s.goal) * 100)}%`;
  $("goal-select").value = String(s.goal);
  $("daily-link").textContent = s.dailyDone ? "Today's daily quiz: done ✓" : "Take today's daily quiz →";
  $("daily-link").classList.toggle("done", s.dailyDone);
}

async function setupStreak() {
  const select = $("goal-select");
  select.innerHTML = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((n) => `<option value="${n}">${n}</option>`).join("");
  select.addEventListener("change", async () => {
    select.disabled = true;
    try {
      renderStreak(await setDailyGoal(Number(select.value)));
    } catch (error) {
      window.alert(error.message);
    } finally {
      select.disabled = false;
    }
  });
  renderStreak(await streak());
}

// ---------- Score trend (inline SVG, one series, hover tooltip) ----------

let trendAttempts = [];

function sectionKey(a) {
  if (a.kind === "mock") return `${a.examId}/${a.stageId}/mock`;
  return a.kind && a.kind !== "exam" ? "practice" : `${a.examId}/${a.stageId}/${a.sectionId}`;
}

function sectionLabel(a) {
  if (a.kind === "mock") return a.quizTitle.replace(/ - [^-]+$/, "");
  if (a.kind && a.kind !== "exam") return "Practice sets";
  // Titles are "Exam - Stage - Section - Level Quiz n"; drop the quiz part.
  return a.quizTitle.split(" - ").slice(0, -1).join(" - ") || a.quizTitle;
}

function setupTrend(attempts) {
  trendAttempts = attempts.slice().reverse(); // oldest first
  const sections = new Map();
  trendAttempts.forEach((a) => {
    const k = sectionKey(a);
    if (!sections.has(k)) sections.set(k, { label: sectionLabel(a), n: 0 });
    sections.get(k).n += 1;
  });
  $("trend-filter").innerHTML = `<option value="all">All quizzes (${trendAttempts.length})</option>` +
    [...sections.entries()]
      .sort((x, y) => y[1].n - x[1].n)
      .map(([k, s]) => `<option value="${escapeHtml(k)}">${escapeHtml(s.label)} (${s.n})</option>`)
      .join("");
  $("trend-filter").addEventListener("change", drawTrend);
  window.addEventListener("resize", drawTrend);
  $("trend-card").hidden = false;
  drawTrend();
}

function drawTrend() {
  const filter = $("trend-filter").value;
  const data = trendAttempts.filter((a) => filter === "all" || sectionKey(a) === filter);
  const box = $("trend-chart");
  if (data.length < 2) {
    box.innerHTML = '<p class="history-empty">Finish at least two quizzes here to see a trend.</p>';
    return;
  }
  const width = Math.max(280, box.clientWidth);
  const height = 220;
  const m = { top: 12, right: 16, bottom: 28, left: 40 };
  const w = width - m.left - m.right;
  const h = height - m.top - m.bottom;
  const x = (i) => m.left + (data.length === 1 ? w / 2 : (i / (data.length - 1)) * w);
  const y = (v) => m.top + h - (v / 100) * h;
  const points = data.map((a, i) => [x(i), y(pct(a))]);
  const path = points.map(([px, py], i) => `${i ? "L" : "M"}${px.toFixed(1)},${py.toFixed(1)}`).join("");
  const area = `${path}L${points[points.length - 1][0].toFixed(1)},${y(0)}L${points[0][0].toFixed(1)},${y(0)}Z`;
  const grid = [0, 25, 50, 75, 100]
    .map((v) => `<line class="grid" x1="${m.left}" x2="${width - m.right}" y1="${y(v)}" y2="${y(v)}"/>
      <text class="axis" x="${m.left - 8}" y="${y(v) + 4}" text-anchor="end">${v}%</text>`)
    .join("");
  // Label first and last attempt number on the x axis only; the tooltip carries the rest.
  const xLabels = [0, data.length - 1]
    .map((i) => `<text class="axis" x="${x(i)}" y="${height - 8}" text-anchor="${i ? "end" : "start"}">#${i + 1}</text>`)
    .join("");
  const last = points[points.length - 1];
  const dots = data.length <= 40
    ? points.map(([px, py]) => `<circle class="dot" cx="${px}" cy="${py}" r="4"/>`).join("")
    : `<circle class="dot" cx="${last[0]}" cy="${last[1]}" r="4"/>`;
  box.innerHTML = `
    <svg viewBox="0 0 ${width} ${height}" width="${width}" height="${height}" role="img"
         aria-label="Score trend over ${data.length} quizzes, latest ${pct(data[data.length - 1]).toFixed(0)}%">
      ${grid}${xLabels}
      <path class="area" d="${area}"/>
      <path class="line" d="${path}"/>
      ${dots}
      <line class="crosshair" id="trend-cross" y1="${m.top}" y2="${m.top + h}" visibility="hidden"/>
      <circle class="dot focus" id="trend-focus" r="6" visibility="hidden"/>
      <text class="end-label" x="${Math.min(last[0], width - m.right)}" y="${Math.max(last[1] - 10, 12)}" text-anchor="end">${pct(data[data.length - 1]).toFixed(0)}%</text>
      <rect class="hit" x="${m.left}" y="${m.top}" width="${w}" height="${h}"/>
    </svg>
    <div class="chart-tip" id="trend-tip" hidden></div>`;

  const svg = box.querySelector("svg");
  const tip = $("trend-tip");
  const hide = () => {
    tip.hidden = true;
    $("trend-cross").setAttribute("visibility", "hidden");
    $("trend-focus").setAttribute("visibility", "hidden");
  };
  svg.querySelector(".hit").addEventListener("pointermove", (e) => {
    const rect = svg.getBoundingClientRect();
    const px = ((e.clientX - rect.left) / rect.width) * width;
    const i = Math.max(0, Math.min(data.length - 1, Math.round(((px - m.left) / w) * (data.length - 1))));
    const a = data[i];
    const [cx, cy] = points[i];
    $("trend-cross").setAttribute("x1", cx);
    $("trend-cross").setAttribute("x2", cx);
    $("trend-cross").setAttribute("visibility", "visible");
    $("trend-focus").setAttribute("cx", cx);
    $("trend-focus").setAttribute("cy", cy);
    $("trend-focus").setAttribute("visibility", "visible");
    const when = new Date(a.completedAt).toLocaleDateString(undefined, { day: "numeric", month: "short" });
    tip.innerHTML = `<b>${pct(a).toFixed(0)}%</b> &middot; ${num(a.score)}/${num(a.maxMarks)}<br>
      <span>${escapeHtml(a.quizTitle)}</span><br><span>#${i + 1} &middot; ${when}</span>`;
    tip.hidden = false;
    const left = (cx / width) * rect.width;
    tip.style.left = `${Math.min(Math.max(left, 90), rect.width - 90)}px`;
    tip.style.top = `${(cy / height) * rect.height}px`;
  });
  svg.querySelector(".hit").addEventListener("pointerleave", hide);
}

// ---------- Topics ----------

let topics = [];
let listing = null;

function renderTopics() {
  const sort = $("topic-sort").value;
  const rows = topics.slice().sort((p, q) => {
    const pReady = p.answered >= MIN_ANSWERS;
    const qReady = q.answered >= MIN_ANSWERS;
    if (pReady !== qReady) return pReady ? -1 : 1;
    if (sort === "slow") return q.avgSeconds - p.avgSeconds;
    if (sort === "most") return q.answered - p.answered;
    return p.accuracy - q.accuracy;
  });
  $("topic-rows").innerHTML = rows
    .map((t) => {
      const ready = t.answered >= MIN_ANSWERS;
      const acc = Math.round(t.accuracy * 100);
      const grade = acc >= 70 ? "good" : acc >= 40 ? "ok" : "low";
      const link = `practice.html?topics=${encodeURIComponent(`${t.subject}/${t.topic}`)}`;
      return `<tr class="${ready ? "" : "few"}">
        <td><span class="topic-name">${escapeHtml(t.name)}</span><span class="topic-subject">${escapeHtml(t.subjectName)}</span></td>
        <td class="num">${t.answered}</td>
        <td>${t.answered ? `<span class="meter" role="img" aria-label="${acc}% accuracy"><span class="meter-fill ${ready ? grade : ""}" style="width:${acc}%"></span></span>
            <span class="meter-value">${acc}%</span>` : '<span class="meter-value">— <small>all skipped</small></span>'}</td>
        <td class="num">${t.answered ? `${Math.round(t.avgSeconds)}s` : "—"}</td>
        <td class="num">${t.skipped}</td>
        <td><a class="row-action" href="${link}">Practise</a></td>
      </tr>`;
    })
    .join("");

  const weakest = topics
    .filter((t) => t.answered >= MIN_ANSWERS)
    .sort((p, q) => p.accuracy - q.accuracy)
    .slice(0, 5);
  $("weak-topics").innerHTML = weakest.length
    ? `<h3>Focus on these</h3><div class="weak-list">${weakest
      .map((t) => `<a class="weak-chip" href="practice.html?topics=${encodeURIComponent(`${t.subject}/${t.topic}`)}">
        <b>${escapeHtml(t.name)}</b><span>${Math.round(t.accuracy * 100)}% &middot; ${Math.round(t.avgSeconds)}s avg</span></a>`)
      .join("")}</div>`
    : `<p class="history-empty">Answer at least ${MIN_ANSWERS} questions in a topic to see your weak areas.</p>`;
}

async function setupTopics() {
  const [stats, list] = await Promise.all([topicStats(), loadTopics()]);
  if (stats.length === 0) return;
  listing = list;
  topics = stats.map((t) => {
    const s = listing.subjects.find((x) => x.id === t.subject);
    return { ...t, name: topicName(listing, t.subject, t.topic), subjectName: s ? s.name : t.subject };
  });
  $("min-answers").textContent = MIN_ANSWERS;
  $("topic-sort").addEventListener("change", renderTopics);
  $("topics-card").hidden = false;
  renderTopics();
}

// ---------- Attempt list ----------

function renderList(attempts) {
  $("history-list").innerHTML = attempts
    .map((a) => {
      const p = pct(a);
      const grade = p >= 70 ? "good" : p >= 40 ? "ok" : "low";
      const when = new Date(a.completedAt).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
      const time = a.timed ? ` &middot; ${Math.floor(a.timeUsedSeconds / 60)}m ${a.timeUsedSeconds % 60}s` : "";
      return `
      <a class="history-row" href="${quizLink(a)}" title="Practise again">
        <span class="history-main">
          <span class="history-quiz">${escapeHtml(a.quizTitle)}</span>
          <span class="history-meta">${when} &middot; ${a.correct} correct, ${a.wrong} wrong, ${a.skipped} skipped${time}</span>
        </span>
        <span class="history-score ${grade}">${num(a.score)}<small>/${num(a.maxMarks)}</small></span>
      </a>`;
    })
    .join("");
}

async function init() {
  const user = await currentUser();
  if (!user) {
    window.location.href = "login.html?next=history.html";
    return;
  }
  $("history-title").textContent = `${user.displayName}'s scores`;
  // Results finished offline go up first, so they're in the list below.
  if (!user.offline) await flushOfflineAttempts(user.username, saveAttempt).catch(() => 0);
  setupStreak().catch((error) => console.error("Could not load streak:", error));
  mistakes()
    .then((list) => { $("mistake-count").textContent = list.length ? `${plural(list.length, "question")} to revise` : "Nothing to revise yet"; })
    .catch(() => {});
  listBookmarks()
    .then((list) => { $("bookmark-count").textContent = list.length ? `${plural(list.length, "saved question")}` : "Tap ☆ on a question to save it"; })
    .catch(() => {});
  try {
    const attempts = await listAttempts();
    if (attempts.length === 0) {
      $("history-list").innerHTML =
        '<p class="history-empty">No quizzes yet. <a href="index.html">Pick an exam</a> and your scores will show up here.</p>';
      return;
    }
    renderSummary(attempts);
    renderList(attempts);
    setupTrend(attempts);
    await setupTopics();
  } catch (error) {
    console.error(error);
    $("history-list").innerHTML = `<p class="history-empty">Could not load your history: ${escapeHtml(error.message)}</p>`;
  }
}

init();
