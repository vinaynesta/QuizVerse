// Question lookups for practice sets: find saved questions (bookmarks, mistakes) by id,
// and load topic pools for the custom quiz builder. Files come from scripts/build_topics.py.

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

/** Fisher-Yates on a copy. */
export function shuffled(list) {
  const out = list.slice();
  for (let i = out.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}

export function topicName(listing, subject, topic) {
  const s = listing.subjects.find((x) => x.id === subject);
  const t = s && s.topics.find((x) => x.id === topic);
  return t ? t.name : topic.replace(/-/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}
