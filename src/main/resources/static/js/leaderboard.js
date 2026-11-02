// Leaderboards: leaderboard.html for this week's totals, leaderboard.html?quiz=<exam/stage/section/level/n>
// for one exam quiz or mock (section "mock"). Only server-verified results are ranked.
import { quizLeaderboard, weeklyLeaderboard } from "./api.js";

const $ = (id) => document.getElementById(id);
const quiz = new URLSearchParams(window.location.search).get("quiz");

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

const fmt = (n) => Number(n.toFixed(2));
const fmtTime = (secs) => `${Math.floor(secs / 60)}m ${secs % 60}s`;

function row(r) {
  return `<tr class="${r.you ? "challenge-you" : ""}">
      <td class="num">${r.rank}</td>
      <td><span class="topic-name">${escapeHtml(r.name)}${r.you ? ' <span class="challenge-tag">you</span>' : ""}</span></td>
      <td class="num"><b>${fmt(r.score)}</b> / ${fmt(r.maxMarks)}</td>
      <td class="num">${quiz ? fmtTime(r.timeUsedSeconds) : r.quizzes}</td>
    </tr>`;
}

async function init() {
  let board;
  try {
    board = await (quiz ? quizLeaderboard(quiz) : weeklyLeaderboard());
  } catch (error) {
    $("board-intro").textContent = error.message;
    return;
  }
  $("board-heading").textContent = board.title;
  if (quiz) {
    $("board-title").textContent = "Quiz leaderboard";
    $("board-extra").textContent = "Time";
    $("board-switch").hidden = false;
  } else {
    $("board-extra").textContent = "Quizzes";
  }
  if (board.players === 0) {
    $("board-intro").textContent = quiz
      ? "No ranked results yet. Finish this quiz within the time limit to be the first."
      : "No ranked results this week yet. Finish an exam quiz or full mock within its time limit to get on the board.";
    return;
  }
  $("board-intro").textContent = quiz
    ? `${board.players} player${board.players > 1 ? "s" : ""}, best score each.`
    : `${board.players} player${board.players > 1 ? "s" : ""}. Total of each player's best score on every quiz this week (Monday to Sunday, India time).`;
  $("board-rows").innerHTML = board.top.map(row).join("");
  $("board-wrap").hidden = false;
  if (board.you && !board.top.some((r) => r.you)) {
    $("board-you").textContent = `You're #${board.you.rank} of ${board.players} with ${fmt(board.you.score)}.`;
    $("board-you").hidden = false;
  }
}

init();
