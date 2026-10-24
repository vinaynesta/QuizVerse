// Challenge page (challenge.html?code=...): what the friend is challenged to, a button to take that quiz,
// and everyone's scores. Anyone with the link can view it; taking it needs an account so scores have names.
import { currentUser, getChallenge } from "./api.js";

const $ = (id) => document.getElementById(id);
const code = (new URLSearchParams(window.location.search).get("code") || "").toUpperCase();

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

const fmt = (n) => Number(n.toFixed(2));
const fmtTime = (secs) => `${Math.floor(secs / 60)}m ${secs % 60}s`;

/** quiz.html link for the same quiz, tagged with the challenge so the attempt joins the board. */
function quizLink(q) {
  const p = new URLSearchParams({ exam: q.examId, stage: q.stageId, level: q.levelId, challenge: code });
  if (q.kind === "mock") p.set("mock", q.quizIndex);
  else {
    p.set("section", q.sectionId);
    p.set("quiz", q.quizIndex);
  }
  return `quiz.html?${p}`;
}

function row(entry, rank) {
  const tags = [entry.creator ? "challenger" : "", entry.you ? "you" : ""].filter(Boolean)
    .map((t) => `<span class="challenge-tag">${t}</span>`).join("");
  return `<tr class="${entry.you ? "challenge-you" : ""}">
      <td class="num">${rank}</td>
      <td><span class="topic-name">${escapeHtml(entry.name)} ${tags}</span></td>
      <td class="num"><b>${fmt(entry.score)}</b> / ${entry.maxMarks}</td>
      <td class="num">${entry.correct}</td>
      <td class="num">${entry.wrong}</td>
      <td class="num">${fmtTime(entry.timeUsedSeconds)}</td>
    </tr>`;
}

async function init() {
  if (!/^[A-Z0-9]{1,10}$/.test(code)) {
    $("challenge-intro").textContent = "This challenge link is incomplete. Ask your friend to send it again.";
    return;
  }
  let challenge;
  try {
    challenge = await getChallenge(code);
  } catch (error) {
    $("challenge-intro").textContent = error.message;
    return;
  }
  const user = await currentUser();
  const { quiz, creator } = challenge;
  document.title = `QuizVerse - ${creator.name}'s challenge`;
  $("challenge-title").textContent = `${creator.name}'s challenge`;

  const mine = [creator, ...challenge.entries].find((e) => e.you);
  $("challenge-intro").innerHTML = creator.you
    ? `Your challenge: <b>${escapeHtml(quiz.quizTitle)}</b>. You scored <b>${fmt(creator.score)}</b> out of ${creator.maxMarks}. Send this page's link to friends; their scores show up below.`
    : `${escapeHtml(creator.name)} scored <b>${fmt(creator.score)}</b> out of ${creator.maxMarks} on <b>${escapeHtml(quiz.quizTitle)}</b>${
      quiz.timed ? " (timed, with negative marking)" : ""}. ${mine ? "You've taken it: see the scores below." : "Can you beat that?"}`;

  const take = $("challenge-take");
  if (creator.you) {
    take.hidden = true;
  } else if (user) {
    take.href = quizLink(quiz);
    take.textContent = mine ? "Try again (only your first try is ranked)" : "Take the challenge";
  } else {
    // Logged out: sign up first, then come straight back to the quiz.
    take.href = `register.html?next=${encodeURIComponent(quizLink(quiz))}`;
    take.textContent = "Sign up to take the challenge";
  }
  $("challenge-actions").hidden = false;
  $("challenge-copy").addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      $("challenge-status").textContent = "Link copied.";
    } catch {
      $("challenge-status").textContent = `Copy this link: ${window.location.href}`;
    }
  });

  // The challenger is on the board with the score they set; friends are ranked against it.
  const board = [creator, ...challenge.entries]
    .sort((a, b) => b.score - a.score || (a.creator ? -1 : b.creator ? 1 : 0));
  $("challenge-rows").innerHTML = board.map((e, i) => row(e, i + 1)).join("");
  $("challenge-board").hidden = false;
  if (!challenge.entries.length) {
    $("challenge-status").textContent = creator.you ? "No one has taken it yet." : "No one else has taken it yet. Be the first!";
  }
}

init();
