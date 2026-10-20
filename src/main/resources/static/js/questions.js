// Question lookups for practice sets: find saved questions (bookmarks, mistakes) by id,
// pick the daily quiz, and load topic pools for the custom quiz builder. Files come from scripts/build_topics.py.

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

/** The same 10 exam questions for everyone on a given date: 4 quant, 3 reasoning, 3 English. */
export async function dailyQuestions(date) {
  const where = await fetchJson("data/question-topics.json");
  const random = seededRandom(`quizverse-daily-${date}`);
  const ids = [];
  for (const [subject, n] of Object.entries(DAILY_MIX)) {
    const pool = Object.keys(where).filter((id) => where[id].startsWith(`${subject}/`)).sort();
    ids.push(...shuffled(pool, random).slice(0, n));
  }
  return questionsById(ids);
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
