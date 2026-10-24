#!/usr/bin/env python3
"""Build the Tech Interview Prep quizzes from the hand-written tech banks.

Usage: python3 -I scripts/build_tech.py scripts/tech_bank <data-dir>     (run after build_exams.py)

Banks: <tech-bank-dir>/<subject>/{easy,medium,hard}.json, each an array of
{topic, question, options, answer, explanation}.
Output: <data-dir>/tech/<subject>/<level>-<n>.json, and a "tech" entry in <data-dir>/index.json
(replaced if already there, so the script can run again). Quizzes are untimed practice with instant
feedback, like the Java practice set. Quizzes of one subject and level never share questions.
"""
import json
import random
import shutil
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # no scripts/__pycache__, which would trip CI's "generated files changed" check
sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_exams import balance_answers, check_ids, question_id  # noqa: E402

# Display order on the exam page. The id is also each question's "subject" (topic pools, stats, the daily
# tech challenge in static/js/questions.js), so don't rename one without migrating saved answers.
SUBJECTS = [
    ("dsa", "Data Structures & Algorithms"),
    ("java", "Java"),
    ("python", "Python"),
    ("springboot", "Spring Boot"),
    ("react", "React"),
    ("vue", "Vue.js"),
    ("ai", "Artificial Intelligence"),
]
LEVELS = [("easy", "Easy"), ("medium", "Medium"), ("hard", "Hard")]
QUESTIONS_PER_QUIZ = 10
KEYS = {"topic", "question", "options", "answer", "explanation"}


def validate(subject, level, bank):
    seen = set()
    for n, q in enumerate(bank):
        where = f"{subject}/{level}.json #{n + 1}"
        if set(q) != KEYS:
            sys.exit(f"{where}: keys must be {sorted(KEYS)}")
        opts = q["options"]
        if len(opts) != 4 or len(set(opts)) != 4 or not all(isinstance(o, str) and o.strip() for o in opts):
            sys.exit(f"{where}: needs 4 distinct non-empty options")
        if not isinstance(q["answer"], int) or not 0 <= q["answer"] < 4:
            sys.exit(f"{where}: answer must be 0-3")
        if not q["question"].strip() or not q["explanation"].strip():
            sys.exit(f"{where}: question and explanation can't be empty")
        if q["question"] in seen:
            sys.exit(f"{where}: duplicate question")
        seen.add(q["question"])


def main(bank_dir, data_dir):
    banks, out = Path(bank_dir), Path(data_dir)
    shutil.rmtree(out / "tech", ignore_errors=True)
    sections = []
    for subject, name in SUBJECTS:
        levels = []
        for level, level_name in LEVELS:
            bank = json.loads((banks / subject / f"{level}.json").read_text())
            validate(subject, level, bank)
            key = f"tech/{subject}/{level}"
            rng = random.Random(key)
            balance_answers(bank, rng)
            rng.shuffle(bank)
            quizzes = []
            for n in range(len(bank) // QUESTIONS_PER_QUIZ):
                chunk = bank[n * QUESTIONS_PER_QUIZ: (n + 1) * QUESTIONS_PER_QUIZ]
                rel = f"{key}-{n + 1}.json"
                qs = [{"id": question_id(subject, q), "subject": subject, "topic": q["topic"], "level": level,
                       "question": q["question"], "options": q["options"], "answer": q["answer"],
                       "explanation": q["explanation"]} for q in chunk]
                (out / rel).parent.mkdir(parents=True, exist_ok=True)
                (out / rel).write_text(json.dumps(qs, indent=1, ensure_ascii=False) + "\n")
                quizzes.append({"id": f"{level}-{n + 1}", "title": f"{level_name} Quiz {n + 1}", "file": rel})
            levels.append({"id": level, "name": level_name, "quizzes": quizzes})
        sections.append({"id": subject, "name": name, "questions": None, "marks": None, "minutes": None,
                         "note": None, "levels": levels})

    index_path = out / "index.json"
    index = json.loads(index_path.read_text())
    index["exams"] = [e for e in index["exams"] if e["id"] != "tech"]
    index["exams"].append({
        "id": "tech", "name": "Tech Interview Prep",
        "description": "DSA, Java, Python, Spring Boot, React, Vue and AI. Untimed practice with an explanation "
                       "after every answer.",
        "stages": [{"id": "practice", "name": "Practice", "unavailable": [], "sections": sections}],
    })
    index_path.write_text(json.dumps(index, indent=2, ensure_ascii=False) + "\n")
    check_ids(out, index)
    total = sum(len(q) for s in sections for lv in s["levels"] for q in [lv["quizzes"]]) * QUESTIONS_PER_QUIZ
    print(f"built {len(sections)} tech subjects ({total} questions) into {out}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
