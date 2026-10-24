// Question lookups for practice sets: find saved questions (bookmarks, mistakes) by id,
// pick the daily quiz, the daily tech challenge and the question of the day, and load topic pools for the
// custom quiz builder. Files come from scripts/build_topics.py.

const cache = new Map();

function fetchJson(path) {
  if (!cache.has(path)) {
    cache.set(path, fetch(path).then((res) => {
      if (!res.ok) throw new Error(`Could not load ${path}`);
      return res.json();
    }));
  }
  return cache.get(path);
}

export const loadTopics = () => fetchJson("data/topics.json");
export const loadTopicPool = (subject, topic) => fetchJson(`data/topics/${subject}/${topic}.json`);

/** Questions for the given ids, in the same order. Ids no longer in the bank are dropped. */
export async function questionsById(ids) {
  const where = await fetchJson("data/question-topics.json");
  const files = [...new Set(ids.map((id) => where[id]).filter(Boolean))];
  const pools = await Promise.all(files.map((f) => {
    const [subject, topic] = f.split("/");
    return loadTopicPool(subject, topic);
  }));
  const byId = new Map(pools.flat().map((q) => [q.id, q]));
  return ids.map((id) => byId.get(id)).filter(Boolean);
}

/** Today's date in India (yyyy-mm-dd), the day the daily quiz and streaks go by. */
export function istToday() {
  return new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" });
}

// Small seeded generator (mulberry32), so everyone gets the same daily quiz for a date.
function seededRandom(seedText) {
  let a = [...seedText].reduce((h, c) => Math.imul(h ^ c.charCodeAt(0), 16777619), 2166136261) >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const DAILY_MIX = { quant: 4, reasoning: 3, english: 3 };
const TECH_SUBJECTS = ["dsa", "java", "python", "springboot", "react", "vue", "ai"];
const TECH_DAILY_COUNT = 5;

/** Sorted ids of every question in the given subject's topic pools. */
async function subjectIds(subject) {
  const where = await fetchJson("data/question-topics.json");
  return Object.keys(where).filter((id) => where[id].startsWith(`${subject}/`)).sort();
}

/** The same 10 exam questions for everyone on a given date: 4 quant, 3 reasoning, 3 English. */
export async function dailyQuestions(date) {
  const random = seededRandom(`quizverse-daily-${date}`);
  const ids = [];
  for (const [subject, n] of Object.entries(DAILY_MIX)) {
    ids.push(...shuffled(await subjectIds(subject), random).slice(0, n));
  }
  return questionsById(ids);
}

/** Ids of the daily tech challenge: one question from each of 5 tech subjects, picked by the date. */
async function techDailyIds(date) {
  const random = seededRandom(`quizverse-tech-daily-${date}`);
  const ids = [];
  for (const subject of shuffled(TECH_SUBJECTS, random).slice(0, TECH_DAILY_COUNT)) {
    ids.push(shuffled(await subjectIds(subject), random)[0]);
  }
  return ids.filter(Boolean);
}

/** The daily tech challenge: the same 5 tech questions for everyone on a given date. */
export async function techDailyQuestions(date) {
  return questionsById(await techDailyIds(date));
}

/** The question of the day on the home page: one tech question, never one from today's tech challenge. */
export async function questionOfTheDay(date) {
  const random = seededRandom(`quizverse-qotd-${date}`);
  const taken = new Set(await techDailyIds(date));
  const subject = TECH_SUBJECTS[Math.floor(random() * TECH_SUBJECTS.length)];
  const id = shuffled(await subjectIds(subject), random).find((x) => !taken.has(x));
  return id ? (await questionsById([id]))[0] || null : null;
}

/** Fisher-Yates on a copy. Pass a seeded random() for a repeatable order. */
export function shuffled(list, random = Math.random) {
  const out = list.slice();
  for (let i = out.length - 1; i > 0; i -= 1) {
    const j = Math.floor(random() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}

export function topicName(listing, subject, topic) {
  const s = listing.subjects.find((x) => x.id === subject);
  const t = s && s.topics.find((x) => x.id === topic);
  return t ? t.name : topic.replace(/-/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}
