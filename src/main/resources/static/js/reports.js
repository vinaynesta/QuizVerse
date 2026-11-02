// Admin view of question reports (usernames in quizverse.admin-usernames), and the question editor (#32):
// corrections are saved on the server by question id and applied to the data on the next build.
import {
  currentUser, discardQuestionEdit, openReports, questionEdits, resolveReport, saveQuestionEdit,
} from "./api.js";
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
      ${q ? `<button type="button" class="btn-edit" data-qid="${q.id}">Edit</button>` : ""}
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
  $("open-question").addEventListener("submit", (e) => {
    e.preventDefault();
    openEditor($("open-question-id").value.trim());
  });
  renderEdits();
  const reports = await openReports();
  if (reports.length === 0) {
    $("report-list").innerHTML = '<p class="history-empty history-card">No open reports.</p>';
    return;
  }
  const qs = await questionsById([...new Set(reports.map((r) => r.questionId))]);
  const byId = new Map(qs.map((q) => [q.id, q]));
  $("report-list").innerHTML = reports.map((r) => card(r, byId.get(r.questionId))).join("");
  $("report-list").querySelectorAll(".btn-edit").forEach((b) => b.addEventListener("click", () => openEditor(b.dataset.qid)));
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

// ---------- Question editor ----------

let edits = new Map(); // question id -> saved edit

async function renderEdits() {
  const list = await questionEdits();
  edits = new Map(list.map((e) => [e.questionId, e]));
  $("edit-list").innerHTML = list.length
    ? list.map((e) => `
      <article class="history-card report-card">
        <div class="review-head"><span><code>${escapeHtml(e.questionId)}</code>${e.hidden ? ' <span class="badge">Hidden</span>' : ""}</span>
          <span class="history-meta">${escapeHtml(e.editedBy)} &middot; ${new Date(e.updatedAt).toLocaleString()}</span></div>
        ${e.note ? `<p class="report-note-text">“${escapeHtml(e.note)}”</p>` : ""}
        <p class="history-meta">Changes: ${["question", "options", "explanation"].filter((k) => e[k] !== null).join(", ") || (e.hidden ? "hidden" : "none")}</p>
        <button type="button" class="btn-edit" data-qid="${e.questionId}">Edit</button>
        <button type="button" class="btn-discard" data-qid="${e.questionId}">Discard edit</button>
      </article>`).join("")
    : '<p class="history-empty history-card">No saved edits.</p>';
  $("edit-list").querySelectorAll(".btn-edit").forEach((b) => b.addEventListener("click", () => openEditor(b.dataset.qid)));
  $("edit-list").querySelectorAll(".btn-discard").forEach((b) => b.addEventListener("click", async () => {
    if (!window.confirm("Discard this edit? The question goes back to the bank's version on the next build.")) return;
    await discardQuestionEdit(b.dataset.qid);
    renderEdits();
  }));
}

/** The editor for one question: the bank's version, with any saved edit filled in over it. */
async function openEditor(questionId) {
  const panel = $("edit-panel");
  const [q] = await questionsById([questionId]);
  const saved = edits.get(questionId);
  if (!q && !saved) {
    panel.innerHTML = `<p class="history-empty history-card">No question with id ${escapeHtml(questionId)} in the practice pools.</p>`;
    return;
  }
  const base = q || { question: "", options: ["", "", "", ""], answer: 0, explanation: "" };
  const cur = {
    question: (saved && saved.question) || base.question,
    options: (saved && saved.options) || base.options,
    answer: saved && saved.answer !== null ? saved.answer : base.answer,
    explanation: (saved && saved.explanation) || base.explanation || "",
  };
  panel.innerHTML = `
    <form class="history-card report-card edit-form" id="edit-form">
      <div class="review-head"><strong>Edit <code>${escapeHtml(questionId)}</code></strong>
        ${q ? `<span class="history-meta">${escapeHtml(q.subject)} &middot; ${escapeHtml(q.topic)} &middot; ${escapeHtml(q.level)}</span>` : ""}</div>
      ${q && q.group ? '<p class="history-meta">Part of a shared set-up (table, puzzle or passage); edit the set-up in the bank files.</p>' : ""}
      <label>Question<textarea id="edit-question" rows="3" maxlength="4000" required>${escapeHtml(cur.question)}</textarea></label>
      <fieldset class="edit-options"><legend>Options (select the correct one)</legend>
        ${cur.options.map((o, i) => `<label class="edit-option"><input type="radio" name="edit-answer" value="${i}"${i === cur.answer ? " checked" : ""}/>
          <input type="text" class="edit-option-text" maxlength="300" required value="${escapeHtml(o)}"/></label>`).join("")}
      </fieldset>
      <label>Solution<textarea id="edit-explanation" rows="4" maxlength="4000">${escapeHtml(cur.explanation)}</textarea></label>
      <label class="edit-hidden"><input type="checkbox" id="edit-hidden"${saved && saved.hidden ? " checked" : ""}/> Hide from practice (the question is broken beyond a quick fix)</label>
      <label>Note for other admins<input type="text" id="edit-note" maxlength="500" value="${escapeHtml((saved && saved.note) || "")}"/></label>
      <p class="history-meta">The question keeps its id, so users' history and bookmarks stay attached.</p>
      <button type="submit" class="btn-resolve">Save edit</button>
      <button type="button" class="btn-discard" id="edit-cancel">Close</button>
      <p class="history-meta" id="edit-status" role="status"></p>
    </form>`;
  $("edit-cancel").addEventListener("click", () => { panel.innerHTML = ""; });
  $("edit-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const options = [...panel.querySelectorAll(".edit-option-text")].map((i) => i.value.trim());
    const answer = Number(panel.querySelector('input[name="edit-answer"]:checked').value);
    const question = $("edit-question").value.trim();
    const explanation = $("edit-explanation").value.trim();
    const sameOptions = q && options.join("\u001f") === q.options.join("\u001f") && answer === q.answer;
    const body = {
      question: q && question === q.question ? null : question,
      options: sameOptions ? null : options,
      answer: sameOptions ? null : answer,
      explanation: q && explanation === (q.explanation || "") ? null : explanation,
      hidden: $("edit-hidden").checked,
      note: $("edit-note").value.trim() || null,
    };
    try {
      await saveQuestionEdit(questionId, body);
      $("edit-status").textContent = "Saved. It goes live on the next build.";
      renderEdits();
    } catch (error) {
      $("edit-status").textContent = error.message;
    }
  });
  panel.scrollIntoView({ behavior: "smooth", block: "start" });
}

init().catch((error) => {
  console.error(error);
  $("report-list").innerHTML = `<p class="history-empty history-card">Could not load reports: ${escapeHtml(error.message)}</p>`;
});
