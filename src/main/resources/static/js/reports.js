// Admin view of question reports (usernames in quizverse.admin-usernames).
import { currentUser, openReports, resolveReport } from "./api.js";
import { questionsById } from "./questions.js";

const $ = (id) => document.getElementById(id);
const REASONS = { "wrong-answer": "Wrong answer", typo: "Typo", unclear: "Unclear", other: "Other" };

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

function card(report, q) {
  const when = new Date(report.createdAt).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
  const body = q
    ? `<p class="review-q">${escapeHtml(q.question)}</p>
       <ol class="report-options" type="A">${q.options
         .map((o, i) => `<li${i === q.answer ? ' class="is-answer"' : ""}>${escapeHtml(o)}${i === q.answer ? " ✓" : ""}</li>`)
         .join("")}</ol>
       <p class="history-meta">${escapeHtml(q.subject)} &middot; ${escapeHtml(q.topic)} &middot; ${escapeHtml(q.level)}</p>`
    : '<p class="history-meta">This question is no longer in the bank (it may already be fixed).</p>';
  return `
    <article class="history-card report-card" data-id="${report.id}">
      <div class="review-head">
        <span><span class="badge wrong">${REASONS[report.reason] || escapeHtml(report.reason)}</span>
          <code>${escapeHtml(report.questionId)}</code></span>
        <span class="history-meta">${escapeHtml(report.reportedBy)} &middot; ${when}</span>
      </div>
      ${report.note ? `<p class="report-note-text">“${escapeHtml(report.note)}”</p>` : ""}
      ${body}
      <button type="button" class="btn-resolve">Mark resolved</button>
    </article>`;
}

async function init() {
  const user = await currentUser();
  if (!user) {
    window.location.href = "login.html?next=reports.html";
    return;
  }
  if (!user.admin) {
    $("report-list").innerHTML = '<p class="history-empty history-card">Only admins can see question reports.</p>';
    return;
  }
  const reports = await openReports();
  if (reports.length === 0) {
    $("report-list").innerHTML = '<p class="history-empty history-card">No open reports.</p>';
    return;
  }
  const qs = await questionsById([...new Set(reports.map((r) => r.questionId))]);
  const byId = new Map(qs.map((q) => [q.id, q]));
  $("report-list").innerHTML = reports.map((r) => card(r, byId.get(r.questionId))).join("");
  $("report-list").querySelectorAll(".btn-resolve").forEach((b) =>
    b.addEventListener("click", async () => {
      const article = b.closest("article");
      b.disabled = true;
      try {
        await resolveReport(article.dataset.id);
        article.remove();
        if (!$("report-list").querySelector("article")) {
          $("report-list").innerHTML = '<p class="history-empty history-card">No open reports.</p>';
        }
      } catch (error) {
        window.alert(error.message);
        b.disabled = false;
      }
    }));
}

init().catch((error) => {
  console.error(error);
  $("report-list").innerHTML = `<p class="history-empty history-card">Could not load reports: ${escapeHtml(error.message)}</p>`;
});
