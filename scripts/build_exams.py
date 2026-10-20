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
import hashlib
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


def question_id(subject, q):
    """Stable id from the content, so it survives rebuilds (option order doesn't matter).
    Keep in sync with scripts/build_questions.py."""
    key = "\x1f".join([subject, q["question"], *sorted(q["options"])])
    return "q" + hashlib.sha1(key.encode()).hexdigest()[:11]


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


# Last published prelims cutoffs, General category, for the reference line on mock results. They vary by
# state, so each gives the range and a few large states. From coaching sites that copy the scorecard figures
# (no official consolidated notice exists); check the official result for your state and category.
# Only add figures with a source. Researched 17 Oct 2026.
CUTOFFS = {
    ("ibps-clerk", "prelims"): {
        "cycle": "2025", "outOf": 100, "low": 45.25, "lowState": "Gujarat", "high": 86.5, "highState": "Chandigarh",
        "states": {"Uttar Pradesh": 69.75, "Bihar": 61.25, "Maharashtra": 46, "Karnataka": 46},
        "sources": ["https://www.adda247.com/jobs/ibps-clerk-cut-off/", "https://www.careerpower.in/ibps-clerk-cut-off.html"],
    },
    ("sbi-clerk", "prelims"): {
        "cycle": "2025-26", "outOf": 100, "low": 52, "lowState": "Tamil Nadu", "high": 81.75, "highState": "Jammu & Kashmir",
        "states": {"Uttar Pradesh": 79.5, "West Bengal": 81.5, "Maharashtra": 65.5},
        "sources": ["https://www.adda247.com/jobs/sbi-clerk-cut-off/", "https://www.mahendras.org/blogs/sbi-clerk-previous-year-cut-off"],
    },
    ("rrb-clerk", "prelims"): {
        "cycle": "2025", "outOf": 80, "low": 48.5, "lowState": "Mizoram", "high": 77.5, "highState": "Punjab",
        "states": {"Uttar Pradesh": 71.75, "Maharashtra": 73.5, "Tamil Nadu": 62.5, "Karnataka": 58.25},
        "sources": ["https://www.practicemock.com/rrb-clerk-cut-offs/", "https://www.careerpower.in/ibps-rrb-clerk-cut-off.html"],
    },
}


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
            cutoff = CUTOFFS.get((exam["id"], stage["id"]))
            if cutoff:
                total = sum(x["marks"] for x in stage["sections"])
                if cutoff["outOf"] != total:
                    sys.exit(f"{exam['id']}/{stage['id']}: cutoff is out of {cutoff['outOf']} but the stage has {total} marks")
                st["cutoff"] = cutoff
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
                        qs = [{"id": question_id(s["bank"], q), "subject": s["bank"], "topic": q["topic"],
                               "level": level, "question": q["question"], "options": q["options"],
                               "answer": q["answer"], "explanation": q.get("explanation", "")} for q in chunk]
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
    check_ids(out, index)
    print(f"built {len(EXAMS)} exams + java into {out}")


def check_ids(out, index):
    """The same id must always mean the same question, or saved answers would point at the wrong one."""
    seen = {}
    for exam in index["exams"]:
        for stage in exam["stages"]:
            for section in stage["sections"]:
                for level in section["levels"]:
                    for quiz in level["quizzes"]:
                        for q in json.loads((out / quiz["file"]).read_text()):
                            key = (q["subject"], q["question"], tuple(sorted(q["options"])))
                            if seen.setdefault(q["id"], key) != key:
                                sys.exit(f"id clash: {q['id']} in {quiz['file']}")


if __name__ == "__main__":
    main(*sys.argv[1:4])
