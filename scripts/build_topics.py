#!/usr/bin/env python3
"""Group every question by subject and topic, for revision, bookmarks and the custom quiz builder.

Usage: python3 -I scripts/build_topics.py <data-dir> <answer-keys.json> [<ga-bank-dir>]
       (run after build_exams.py and build_tech.py)

Reads <data-dir>/index.json and the quiz files it lists. Exam quiz files have no answers (see build_exams.py),
so their answers and explanations come from <answer-keys.json>: practice keeps instant feedback and offline use.
<data-dir>/practice-extra.json (bank questions no quiz uses, from build_exams.py) joins the pools too, and so do
the Computer Knowledge and Banking Awareness starter questions in <ga-bank-dir>/{computer,banking}.json (practice
only until there are enough for exam sections; each is marked "needsReview" until someone checks its facts). Writes
<data-dir>/topics/<subject>/<topic>.json (each question once, with its level) and
<data-dir>/topics.json (subjects, topics, display names and question counts per level) and
<data-dir>/question-topics.json (question id -> "subject/topic", to find a saved question's file).
"""
import json
import shutil
import sys
from collections import defaultdict
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_exams import edited, with_answer  # noqa: E402

SUBJECTS = {"english": "English Language", "quant": "Quantitative Aptitude",
            "reasoning": "Reasoning Ability", "computer": "Computer Knowledge", "banking": "Banking Awareness",
            "dsa": "Data Structures & Algorithms", "java": "Java",
            "python": "Python", "springboot": "Spring Boot", "react": "React", "vue": "Vue.js",
            "ai": "Artificial Intelligence"}
LEVEL_ORDER = ["easy", "medium", "hard", "very-hard", "mixed"]
NAMES = {
    "lcm-hcf": "LCM & HCF", "speed-time-distance": "Speed, Time & Distance", "time-work": "Time & Work",
    "profit-loss": "Profit & Loss", "boats-streams": "Boats & Streams", "pipes-cisterns": "Pipes & Cisterns",
    "permutation-combination": "Permutation & Combination", "synonyms-antonyms": "Synonyms & Antonyms",
    "articles-prepositions": "Articles & Prepositions", "tense-agreement": "Tense & Agreement",
    "fill-blank": "Fill in the Blank", "double-blank": "Double Fill in the Blank",
    "one-word": "One-Word Substitution", "mixture": "Mixtures & Alligation", "roots": "Squares & Roots",
    "core-java": "Core Java", "advanced-java-8-features": "Java 8+ Features",
    "arrays-strings": "Arrays & Strings", "stacks-queues": "Stacks & Queues",
    "sorting-searching": "Sorting & Searching", "recursion-backtracking": "Recursion & Backtracking",
    "oop": "OOP", "jvm-memory": "JVM & Memory", "streams-lambdas": "Streams & Lambdas",
    "basics-types": "Basics & Types", "comprehensions-generators": "Comprehensions & Generators",
    "modules-packages": "Modules & Packages", "decorators-context-managers": "Decorators & Context Managers",
    "async-concurrency": "Async & Concurrency", "core-di": "Core & Dependency Injection",
    "web-rest": "Web & REST", "data-jpa": "Data JPA", "configuration-profiles": "Configuration & Profiles",
    "actuator-observability": "Actuator & Observability", "deployment-build": "Build & Deployment",
    "jsx-components": "JSX & Components", "props-state": "Props & State",
    "rendering-reconciliation": "Rendering & Reconciliation", "context-state-management": "Context & State Management",
    "forms-events": "Forms & Events", "routing-data-fetching": "Routing & Data Fetching",
    "react-19-server-components": "React 19 & Server Components", "composition-api": "Composition API",
    "components-props-events": "Components, Props & Events", "computed-watchers": "Computed & Watchers",
    "slots-provide-inject": "Slots, Provide & Inject", "router-pinia": "Router & Pinia",
    "performance-tooling": "Performance & Tooling", "ml-basics": "ML Basics",
    "evaluation-metrics": "Evaluation Metrics", "deep-learning-architectures": "Deep Learning Architectures",
    "nlp-transformers": "NLP & Transformers", "llms-prompting": "LLMs & Prompting", "rag-agents": "RAG & Agents",
    "ai-ethics-safety": "AI Ethics & Safety", "ms-office": "MS Office", "memory-units": "Memory Units",
    "rbi": "RBI & Monetary Policy", "banking-terms": "Banking Terms", "payments": "Payment Systems",
    "data-interpretation": "Data Interpretation", "reading-comprehension": "Reading Comprehension",
}


def display_name(topic):
    return NAMES.get(topic) or topic.replace("-", " ").title()


def load_ga(ga_dir):
    """Hand-written Computer Knowledge and Banking Awareness questions, published as practice questions."""
    out = []
    for subject in ("computer", "banking"):
        path = Path(ga_dir) / f"{subject}.json"
        for q in json.loads(path.read_text()) if path.exists() else []:
            q = edited(subject, q)
            if q.get("hidden"):
                continue
            p = {"id": q["_id"], "subject": subject, "topic": q["topic"], "level": q["level"],
                 "question": q["question"], "options": q["options"], "answer": q["answer"],
                 "explanation": q.get("explanation", "")}
            if q.get("needsReview"):
                p["needsReview"] = True
            out.append(p)
    return out


def main(data_dir, keys_path, ga_dir=None):
    data = Path(data_dir)
    index = json.loads((data / "index.json").read_text())
    keys = json.loads(Path(keys_path).read_text())["quizzes"]
    pools = defaultdict(dict)  # (subject, topic) -> id -> question
    for exam in index["exams"]:
        for stage in exam["stages"]:
            for section in stage["sections"]:
                for level in section["levels"]:
                    for quiz in level["quizzes"]:
                        key = keys.get(quiz["file"])
                        for n, q in enumerate(json.loads((data / quiz["file"]).read_text())):
                            if key and n in key.get("hidden", []):
                                continue
                            if key:
                                q = with_answer(q, key["answers"][n], key["explanations"][n],
                                                key.get("hiExplanations", [None] * (n + 1))[n])
                            pools[(q["subject"], q["topic"])][q["id"]] = q

    extra = data / "practice-extra.json"
    for q in (json.loads(extra.read_text()) if extra.exists() else []) + (load_ga(ga_dir) if ga_dir else []):
        pools[(q["subject"], q["topic"])].setdefault(q["id"], q)

    out = data / "topics"
    shutil.rmtree(out, ignore_errors=True)
    subjects = defaultdict(list)
    for (subject, topic), by_id in sorted(pools.items()):
        qs = sorted(by_id.values(), key=lambda q: (LEVEL_ORDER.index(q["level"]), q["id"]))
        path = out / subject / f"{topic}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(qs, indent=1, ensure_ascii=False) + "\n")
        levels = defaultdict(int)
        for q in qs:
            levels[q["level"]] += 1
        subjects[subject].append({"id": topic, "name": display_name(topic), "file": f"topics/{subject}/{topic}.json",
                                  "levels": {lv: levels[lv] for lv in LEVEL_ORDER if levels[lv]}})

    listing = {"subjects": [{"id": s, "name": SUBJECTS.get(s, s.title()),
                             "topics": sorted(subjects[s], key=lambda t: t["name"])}
                            for s in SUBJECTS if s in subjects]}
    (data / "topics.json").write_text(json.dumps(listing, indent=1, ensure_ascii=False) + "\n")
    where = {qid: f"{subject}/{topic}" for (subject, topic), by_id in sorted(pools.items()) for qid in sorted(by_id)}
    (data / "question-topics.json").write_text(json.dumps(where, separators=(",", ":")) + "\n")
    total = sum(len(v) for v in pools.values())
    print(f"{total} unique questions in {len(pools)} topics")


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4):
        sys.exit(__doc__)
    main(*sys.argv[1:4])
