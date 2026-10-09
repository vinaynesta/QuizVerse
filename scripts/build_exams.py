#!/usr/bin/env python3
"""Assemble exam-pattern quizzes from the question banks.

Usage: python3 -I scripts/build_exams.py <banks-dir> <english-bank-dir> <data-dir>

Banks: <banks-dir>/{quant,reasoning}_{easy,medium,hard}.json (scripts/gen_banks.py)
       <english-bank-dir>/{easy,medium,hard}.json
Output: <data-dir>/<exam>/<stage>/<section>/<level>-<n>.json and <data-dir>/index.json.

Each quiz has exactly the section's question count; quizzes of one section and
level never share questions. Sections we have no bank for (General Awareness,
Computer, Hindi) are listed as "unavailable" so the app can show them as coming soon.
The Java practice quizzes in <data-dir>/java are indexed untimed.
"""
import json
import random
import shutil
import sys
from pathlib import Path

LEVELS = [("easy", "Easy"), ("medium", "Medium"), ("hard", "Hard")]
QUIZZES_PER_LEVEL = 5


def sec(id_, name, bank, q, marks, minutes, note=None):
    return {"id": id_, "name": name, "bank": bank, "questions": q, "marks": marks, "minutes": minutes, "note": note}


# From the exam-pattern table supplied by the user (IBPS / SBI / IBPS RRB 2026 patterns).
EXAMS = [
    {
        "id": "ibps-clerk", "name": "IBPS Clerk",
        "description": "Customer Service Associate. Prelims 100 questions in 60 minutes; Mains 160 questions in 125 minutes.",
        "stages": [
            {"id": "prelims", "name": "Prelims", "sections": [
                sec("english", "English Language", "english", 30, 30, 20),
                sec("numerical-ability", "Numerical Ability", "quant", 35, 35, 20),
                sec("reasoning", "Reasoning Ability", "reasoning", 35, 35, 20)],
             "unavailable": []},
            {"id": "mains", "name": "Mains", "sections": [
                sec("reasoning", "Reasoning Ability", "reasoning", 40, 60, 35),
                sec("english", "English Language", "english", 40, 40, 35),
                sec("quantitative-aptitude", "Quantitative Aptitude", "quant", 40, 50, 35)],
             "unavailable": [sec("general-financial-awareness", "General/Financial Awareness", None, 40, 50, 20)]},
        ],
    },
    {
        "id": "sbi-clerk", "name": "SBI Clerk",
        "description": "Junior Associate. Prelims 100 questions in 60 minutes; Mains 190 questions in 160 minutes.",
        "stages": [
            {"id": "prelims", "name": "Prelims", "sections": [
                sec("english", "English Language", "english", 30, 30, 20),
                sec("quantitative-aptitude", "Quantitative Aptitude", "quant", 35, 35, 20),
                sec("reasoning", "Reasoning Ability", "reasoning", 35, 35, 20)],
             "unavailable": []},
            {"id": "mains", "name": "Mains", "sections": [
                sec("english", "General English", "english", 40, 40, 35),
                sec("quantitative-aptitude", "Quantitative Aptitude", "quant", 50, 50, 45),
                sec("reasoning", "Reasoning Ability", "reasoning", 50, 60, 45,
                    "The real section is Reasoning Ability & Computer Aptitude; these quizzes cover the reasoning part only.")],
             "unavailable": [sec("general-financial-awareness", "General/Financial Awareness", None, 50, 50, 35)]},
        ],
    },
    {
        "id": "rrb-clerk", "name": "IBPS RRB Clerk",
        "description": "Office Assistant (Multipurpose). Prelims 80 questions in 45 minutes; Mains 200 questions in 120 minutes.",
        "stages": [
            {"id": "prelims", "name": "Prelims", "sections": [
                sec("reasoning", "Reasoning Ability", "reasoning", 40, 40, 25),
                sec("numerical-ability", "Numerical Ability", "quant", 40, 40, 20)],
             "unavailable": []},
            {"id": "mains", "name": "Mains", "sections": [
                sec("reasoning", "Reasoning Ability", "reasoning", 40, 50, 30),
                sec("english", "English Language", "english", 40, 40, 30),
                sec("numerical-ability", "Numerical Ability", "quant", 40, 50, 30)],
             "unavailable": [sec("computer-knowledge", "Computer Knowledge", None, 40, 20, 15),
                             sec("general-awareness", "General Awareness", None, 40, 40, 15)]},
        ],
    },
]


def load_bank(name, level, banks_dir, english_dir):
    path = (Path(english_dir) / f"{level}.json") if name == "english" else Path(banks_dir) / f"{name}_{level}.json"
    return json.loads(path.read_text())


FIXED_MARKERS = ("No error", "No improvement", "Part (", "conclusion", "None of", "All of", "Both ")


def is_fixed_order(q):
    """Options whose order carries meaning (error parts, orderings, 'none of these')."""
    opts = q["options"]
    return (any(m in o for o in opts for m in FIXED_MARKERS)
            or all(o.isalpha() and o.isupper() and len(o) <= 5 for o in opts))


def balance_answers(bank, rng):
    """Spread correct answers evenly over A-D by swapping options (skips fixed-order questions)."""
    free = [q for q in bank if not is_fixed_order(q)]
    rng.shuffle(free)
    for n, q in enumerate(free):
        target = n % 4
        if q["answer"] != target:
            o = q["options"]
            o[q["answer"]], o[target] = o[target], o[q["answer"]]
            q["answer"] = target


def meta(s):
    return {"id": s["id"], "name": s["name"], "questions": s["questions"], "marks": s["marks"],
            "minutes": s["minutes"], "note": s["note"]}


def main(banks_dir, english_dir, data_dir):
    out = Path(data_dir)
    for exam in EXAMS:  # drop stale output of earlier layouts
        shutil.rmtree(out / exam["id"], ignore_errors=True)
    index = {"negativeMarking": 0.25, "exams": []}
    for exam in EXAMS:
        entry = {k: exam[k] for k in ("id", "name", "description")}
        entry["stages"] = []
        for stage in exam["stages"]:
            st = {"id": stage["id"], "name": stage["name"], "sections": [],
                  "unavailable": [meta(u) for u in stage["unavailable"]]}
            for s in stage["sections"]:
                s_entry = meta(s)
                s_entry["levels"] = []
                for level, level_name in LEVELS:
                    bank = load_bank(s["bank"], level, banks_dir, english_dir)
                    need = s["questions"] * QUIZZES_PER_LEVEL
                    if len(bank) < need:
                        sys.exit(f"{exam['id']}/{stage['id']}/{s['id']}/{level}: need {need}, bank has {len(bank)}")
                    key = f"{exam['id']}/{stage['id']}/{s['id']}/{level}"
                    rng = random.Random(key)
                    balance_answers(bank, rng)
                    rng.shuffle(bank)
                    quizzes = []
                    for n in range(1, QUIZZES_PER_LEVEL + 1):
                        chunk = bank[(n - 1) * s["questions"]: n * s["questions"]]
                        rel = f"{key}-{n}.json"
                        qs = [{"id": f"{exam['id']}-{stage['id']}-{s['id']}-{level}-{n}-{i}", "topic": q["topic"],
                               "question": q["question"], "options": q["options"], "answer": q["answer"],
                               "explanation": q.get("explanation", "")} for i, q in enumerate(chunk, 1)]
                        (out / rel).parent.mkdir(parents=True, exist_ok=True)
                        (out / rel).write_text(json.dumps(qs, indent=1, ensure_ascii=False) + "\n")
                        quizzes.append({"id": f"{level}-{n}", "title": f"{level_name} Quiz {n}", "file": rel})
                    s_entry["levels"].append({"id": level, "name": level_name, "quizzes": quizzes})
                st["sections"].append(s_entry)
            entry["stages"].append(st)
        index["exams"].append(entry)

    java_quizzes = json.loads((out / "java" / "quizzes.json").read_text())
    index["exams"].append({
        "id": "java", "name": "Java Practice", "description": "Untimed Java practice quizzes.",
        "stages": [{"id": "practice", "name": "Practice", "unavailable": [], "sections": [{
            "id": "java", "name": "Java", "questions": None, "marks": None, "minutes": None, "note": None,
            "levels": [{"id": q["id"], "name": q["title"].replace(" Questions", ""),
                        "quizzes": [{"id": q["id"], "title": q["title"], "file": q["file"]}]}
                       for q in java_quizzes]}]}],
    })
    (out / "index.json").write_text(json.dumps(index, indent=2, ensure_ascii=False) + "\n")
    print(f"built {len(EXAMS)} exams + java into {out}")


if __name__ == "__main__":
    main(*sys.argv[1:4])
