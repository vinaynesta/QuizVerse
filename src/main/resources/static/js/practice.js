// Custom quiz builder: pick topics, difficulty, length and time; quiz.js?practice=custom runs it.
// practice.html?topics=quant/trains preselects topics (the "Practise" links on My scores).
import { loadTopics } from "./questions.js";

const $ = (id) => document.getElementById(id);
const preselected = new Set((new URLSearchParams(window.location.search).get("topics") || "").split(","));
let listing = null;

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

function render() {
  $("topic-groups").innerHTML = listing.subjects
    .map((s) => `
      <fieldset class="topic-group">
        <legend>${escapeHtml(s.name)}
          <button type="button" class="link-button" data-all="${s.id}">All</button>
          <button type="button" class="link-button" data-none="${s.id}">None</button></legend>
        <div class="topic-chips">${s.topics
          .map((t) => {
            const value = `${s.id}/${t.id}`;
            const total = Object.values(t.levels).reduce((a, b) => a + b, 0);
            return `<label class="topic-chip"><input type="checkbox" name="topic" value="${value}"${
              preselected.has(value) ? " checked" : ""}/> ${escapeHtml(t.name)} <small>${total}</small></label>`;
          })
          .join("")}</div>
      </fieldset>`)
    .join("");
  $("topic-groups").querySelectorAll("[data-all],[data-none]").forEach((b) =>
    b.addEventListener("click", () => {
      const subject = b.dataset.all || b.dataset.none;
      $("topic-groups").querySelectorAll(`input[value^="${subject}/"]`).forEach((c) => { c.checked = Boolean(b.dataset.all); });
      update();
    }));
  $("builder").addEventListener("change", update);
  update();
}

function selected() {
  return [...document.querySelectorAll('input[name="topic"]:checked')].map((c) => c.value);
}

/** How many questions match the topics and difficulty, so the user knows before starting. */
function available(topics, level) {
  return topics.reduce((sum, value) => {
    const [subject, topic] = value.split("/");
    const t = listing.subjects.find((s) => s.id === subject).topics.find((x) => x.id === topic);
    return sum + (level === "any" ? Object.values(t.levels).reduce((a, b) => a + b, 0) : t.levels[level] || 0);
  }, 0);
}

function update() {
  const topics = selected();
  const level = $("level").value;
  const count = Number($("count").value);
  const n = topics.length ? available(topics, level) : 0;
  $("topic-picked").textContent = topics.length ? `${topics.length} selected` : "Pick at least one";
  $("available").textContent = !topics.length
    ? ""
    : n === 0
      ? "No questions match. Try another difficulty."
      : n < count
        ? `Only ${n} questions match, so the quiz will have ${n}.`
        : `${n} questions match; ${count} will be picked at random.`;
  $("build-button").disabled = n === 0 || topics.length > 20;
  if (topics.length > 20) $("available").textContent = "Pick at most 20 topics.";
}

$("builder").addEventListener("submit", (event) => {
  event.preventDefault();
  const q = new URLSearchParams({
    practice: "custom",
    topics: selected().join(","),
    level: $("level").value,
    count: $("count").value,
    minutes: $("minutes").value,
  });
  window.location.href = `quiz.html?${q}`;
});

loadTopics()
  .then((data) => {
    listing = data;
    render();
  })
  .catch((error) => {
    console.error(error);
    $("topic-groups").innerHTML = '<p class="history-empty">Could not load topics.</p>';
  });
