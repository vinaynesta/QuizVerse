#!/usr/bin/env python3
"""Assemble exam-pattern quizzes from the question banks.

Usage: python3 -I scripts/build_exams.py <banks-dir> <english-bank-dir> <data-dir> <answer-keys.json>

Banks: <banks-dir>/{quant,reasoning}_{easy,medium,hard}.json (scripts/gen_banks.py)
       <banks-dir>/groups_{medium,hard}.json (scripts/gen_groups.py: DI tables and puzzles)
       <english-bank-dir>/{easy,medium,hard}.json and rc_{medium,hard}.json (reading comprehension sets)
Output: <data-dir>/<exam>/<stage>/<section>/<level>-<n>.json and <data-dir>/index.json.
        Timed exam quizzes are published without "answer" and "explanation": those go to <answer-keys.json>
        (outside static/), and the server scores exam quizzes and mocks with them (leaderboards).

Each quiz has exactly the section's question count; quizzes of one section and
level never share questions. Each exam takes its own slice of the Quant and Reasoning banks (POOL_SIZE questions,
the k-th exam in EXAMS gets slice k), so exams don't repeat each other's questions; English is one shared bank.
Admin corrections exported from the question editor (scripts/question_edits.json, keyed by question id) are applied
on top of the banks; an edited question keeps its original id, so history and bookmarks stay attached.
Bank questions that no quiz uses any more go to <data-dir>/practice-extra.json, so build_topics.py keeps them in
the practice pools and saved bookmarks still find them. Medium and hard Quant quizzes end with one DI set, Reasoning quizzes with one
puzzle set and English quizzes with one reading comprehension passage (GROUP_SIZE questions sharing a table or set-up, carried as "group" on each of them). Sections we have no bank for (General Awareness,
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
GROUP_SIZE = 5  # questions per DI table / puzzle in scripts/gen_groups.py
POOL_SIZE = 250  # QUANT_PER_LEVEL in scripts/gen_banks.py: one exam's slice of a Quant or Reasoning bank


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
    },    # Added Oct 2026 (#17) from the last notified patterns; check each new notification, these change often.
    # PO Mains also has a descriptive paper (letter and essay) that a quiz can't mark, so it's left out.
    {
        "id": "ibps-po", "name": "IBPS PO",
        "description": "Probationary Officer. Prelims 100 questions in 60 minutes; Mains 155 questions in 180 minutes, "
                       "plus a descriptive paper.",
        "stages": [
            {"id": "prelims", "name": "Prelims", "sections": [
                sec("english", "English Language", "english", 30, 30, 20),
                sec("quantitative-aptitude", "Quantitative Aptitude", "quant", 35, 35, 20),
                sec("reasoning", "Reasoning Ability", "reasoning", 35, 35, 20)],
             "unavailable": []},
            {"id": "mains", "name": "Mains", "sections": [
                sec("reasoning", "Reasoning & Computer Aptitude", "reasoning", 45, 60, 60,
                    "These quizzes cover the reasoning part only."),
                sec("english", "English Language", "english", 35, 40, 40),
                sec("data-analysis", "Data Analysis & Interpretation", "quant", 35, 60, 45)],
             "unavailable": [sec("general-economy-banking-awareness", "General/Economy/Banking Awareness", None, 40, 40, 35)]},
        ],
    },
    {
        "id": "sbi-po", "name": "SBI PO",
        "description": "Probationary Officer. Prelims 100 questions in 60 minutes; Mains 155 questions in 180 minutes, "
                       "plus a descriptive paper.",
        "stages": [
            {"id": "prelims", "name": "Prelims", "sections": [
                sec("english", "English Language", "english", 40, 40, 20),
                sec("quantitative-aptitude", "Quantitative Aptitude", "quant", 30, 30, 20),
                sec("reasoning", "Reasoning Ability", "reasoning", 30, 30, 20)],
             "unavailable": []},
            {"id": "mains", "name": "Mains", "sections": [
                sec("reasoning", "Reasoning & Computer Aptitude", "reasoning", 40, 60, 50,
                    "These quizzes cover the reasoning part only."),
                sec("data-analysis", "Data Analysis & Interpretation", "quant", 30, 60, 45),
                sec("english", "English Language", "english", 35, 40, 40)],
             "unavailable": [sec("general-economy-banking-awareness", "General/Economy/Banking Awareness", None, 50, 60, 45)]},
        ],
    },
    {
        "id": "rbi-assistant", "name": "RBI Assistant",
        "description": "Assistant, Reserve Bank of India. Prelims 100 questions in 60 minutes; Mains 200 questions in "
                       "135 minutes.",
        "stages": [
            {"id": "prelims", "name": "Prelims", "sections": [
                sec("english", "English Language", "english", 30, 30, 20),
                sec("numerical-ability", "Numerical Ability", "quant", 35, 35, 20),
                sec("reasoning", "Reasoning Ability", "reasoning", 35, 35, 20)],
             "unavailable": []},
            {"id": "mains", "name": "Mains", "sections": [
                sec("reasoning", "Reasoning", "reasoning", 40, 40, 30),
                sec("english", "English Language", "english", 40, 40, 30),
                sec("numerical-ability", "Numerical Ability", "quant", 40, 40, 30)],
             "unavailable": [sec("general-awareness", "General Awareness", None, 40, 40, 25),
                             sec("computer-knowledge", "Computer Knowledge", None, 40, 40, 20)]},
        ],
    },
]


def question_id(subject, q, group=None):
    """Stable id from the content, so it survives rebuilds (option order doesn't matter). A grouped question
    ("Who lives on the top floor?") is only unique with its set-up, so that is part of the key.
    Keep in sync with scripts/build_questions.py and build_topics.py."""
    parts = [subject, q["question"], *sorted(q["options"])]
    if group:
        parts.insert(1, json.dumps(group["setup"], sort_keys=True, ensure_ascii=False))
    return "q" + hashlib.sha1("\x1f".join(parts).encode()).hexdigest()[:11]


EDITS_FILE = Path(__file__).resolve().parent / "question_edits.json"
EDITS = json.loads(EDITS_FILE.read_text()) if EDITS_FILE.exists() else {}
EDIT_FIELDS = ("question", "options", "answer", "explanation")


def edited(subject, q, group=None):
    """The bank question with any admin edit applied, carrying its original id in "_id"."""
    qid = question_id(subject, q, group)
    edit = EDITS.get(qid, {})
    out = {**q, **{k: edit[k] for k in EDIT_FIELDS if k in edit}, "_id": qid}
    if "options" in edit:
        out.pop("hi", None)  # the Hindi text no longer matches; it comes back when the bank itself is fixed
    if edit.get("hidden"):
        out["hidden"] = True
    return out


def group_meta(subject, s):
    """What every question of a set carries: the set-up to show above it. The id ties the set's questions together."""
    g = {"id": "g" + hashlib.sha1(json.dumps([subject, s["setup"]], sort_keys=True, ensure_ascii=False)
                                  .encode()).hexdigest()[:11],
         "kind": s["kind"], "title": s["title"], "setup": s["setup"]}
    if s.get("needsReview"):
        g["needsReview"] = True
    return g


def published(subject, level, q, group=None):
    """A question as the public quiz file shows it: no answer or explanation."""
    out = {"id": q.get("_id") or question_id(subject, q, group), "subject": subject, "topic": q["topic"], "level": level,
           "question": q["question"], "options": q["options"]}
    if group:
        out["group"] = group
    if q.get("hi"):  # Hindi text (#18); its explanation is secret like the English one, see hi_explanation
        out["hi"] = {"question": q["hi"]["question"], "needsReview": q["hi"].get("needsReview", False)}
    return out


def hi_explanation(q):
    return q["hi"]["explanation"] if q.get("hi") else None


# One method line per English question type, added under each hand-written explanation so every answer shows
# how to approach that kind of question, not just why this option is right.
ENGLISH_TIPS = {
    "error-spotting": "Method: check subject-verb agreement, tense, pronouns, articles and prepositions part by part; "
                      "if every part is correct, choose 'No error'.",
    "sentence-improvement": "Method: read the sentence with each option in place; keep the original only if no option "
                            "fixes grammar or idiom without changing the meaning.",
    "idioms": "Method: idioms are not literal; learn them as fixed phrases and test each option in a sentence.",
    "articles-prepositions": "Method: 'a/an' depends on the sound of the next word; prepositions follow fixed pairings "
                             "(good at, interested in, depend on).",
    "fill-blank": "Method: find the clue words around the blank (tense, contrast words like 'but', fixed phrases), then "
                  "test each option.",
    "double-blank": "Method: eliminate options where either word fails; both blanks must fit the same sentence.",
    "para-jumble": "Method: find the opening sentence (introduces the subject, no pronoun or 'however'), then link pairs "
                   "by pronouns, connectors and time order.",
    "synonyms": "Method: think of the word in a sentence, then pick the option that could replace it without changing "
                "the meaning.",
    "antonyms": "Method: first find the word's meaning, then the option with the opposite meaning; beware of synonyms "
                "placed as traps.",
    "synonyms-antonyms": "Method: check whether the question asks for the same or the opposite meaning before choosing.",
    "tense-agreement": "Method: find the time words (yesterday, since, by next year) and the subject, then match tense "
                       "and number.",
    "vocabulary": "Method: use roots, prefixes and the sentence's context to narrow the meaning.",
    "spelling": "Method: look for doubled consonants, -ie/-ei and silent letters; say the word in syllables.",
    "one-word": "Method: match every part of the description; similar words often differ in one detail.",
    "word-usage": "Method: check that the word fits both meaning and grammar (noun, verb or adjective) in that sentence.",
    "confused-words": "Method: learn the pair (affect/effect, principle/principal) and check which meaning the "
                      "sentence needs.",
}


def with_tip(subject, q):
    """English explanations with the topic's method line; generated banks already carry worked steps."""
    expl = q.get("explanation", "")
    tip = ENGLISH_TIPS.get(q["topic"]) if subject == "english" else None
    return f"{expl}\n{tip}" if tip and expl else expl or tip or ""


def load_groups(subject, level, banks_dir, english_dir):
    path = Path(english_dir) / f"rc_{level}.json" if subject == "english" else Path(banks_dir) / f"groups_{level}.json"
    if not path.exists():
        return []
    sets = [s for s in json.loads(path.read_text()) if s["subject"] == subject]
    for s in sets:
        if len(s["questions"]) != GROUP_SIZE:
            sys.exit(f"{path}: set {s['id']} has {len(s['questions'])} questions, expected {GROUP_SIZE}")
    return sets


def load_bank(name, level, banks_dir, english_dir, pool=0):
    """The whole English bank, or the exam's slice of a generated bank."""
    if name == "english":
        return json.loads((Path(english_dir) / f"{level}.json").read_text())
    bank = json.loads((Path(banks_dir) / f"{name}_{level}.json").read_text())
    if len(bank) < (pool + 1) * POOL_SIZE:
        sys.exit(f"{name}_{level}: no slice {pool} (bank has {len(bank)}); raise EXAM_POOLS in gen_banks.py")
    return bank[pool * POOL_SIZE:(pool + 1) * POOL_SIZE]


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


def main(banks_dir, english_dir, data_dir, keys_path):
    out = Path(data_dir)
    keys = {}
    for exam in EXAMS:  # drop stale output of earlier layouts
        shutil.rmtree(out / exam["id"], ignore_errors=True)
    index = {"negativeMarking": 0.25, "exams": []}
    used = set()
    for pool, exam in enumerate(EXAMS):
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
                    bank = load_bank(s["bank"], level, banks_dir, english_dir, pool)
                    key = f"{exam['id']}/{stage['id']}/{s['id']}/{level}"
                    rng = random.Random(key)
                    sets = load_groups(s["bank"], level, banks_dir, english_dir)
                    sets = rng.sample(sets, QUIZZES_PER_LEVEL) if len(sets) >= QUIZZES_PER_LEVEL else []
                    single = s["questions"] - (GROUP_SIZE if sets else 0)
                    need = single * QUIZZES_PER_LEVEL
                    if len(bank) < need:
                        sys.exit(f"{exam['id']}/{stage['id']}/{s['id']}/{level}: need {need}, bank has {len(bank)}")
                    balance_answers(bank, rng)
                    rng.shuffle(bank)
                    quizzes = []
                    for n in range(1, QUIZZES_PER_LEVEL + 1):
                        chunk = [(edited(s["bank"], q), None) for q in bank[(n - 1) * single: n * single]]
                        if sets:
                            g = group_meta(s["bank"], sets[n - 1])
                            chunk += [(edited(s["bank"], q, g), g) for q in sets[n - 1]["questions"]]
                        rel = f"{key}-{n}.json"
                        qs = [published(s["bank"], level, q, g) for q, g in chunk]
                        used.update(q["id"] for q in qs)
                        keys[rel] = {"ids": [q["id"] for q in qs], "answers": [q["answer"] for q, _ in chunk],
                                     "explanations": [with_tip(s["bank"], q) for q, _ in chunk]}
                        hidden = [i for i, (q, _) in enumerate(chunk) if q.get("hidden")]
                        if hidden:  # still in the paper (the section needs its count), left out of practice pools
                            keys[rel]["hidden"] = hidden
                        if any(q.get("hi") for q, _ in chunk):
                            keys[rel]["hiExplanations"] = [hi_explanation(q) for q, _ in chunk]
                        (out / rel).parent.mkdir(parents=True, exist_ok=True)
                        (out / rel).write_text(json.dumps(qs, indent=1, ensure_ascii=False) + "\n")
                        quizzes.append({"id": f"{level}-{n}", "title": f"{level_name} Quiz {n}", "file": rel})
                    s_entry["levels"].append({"id": level, "name": level_name, "quizzes": quizzes})
                st["sections"].append(s_entry)
            entry["stages"].append(st)
        index["exams"].append(entry)

    write_extra(out, used, banks_dir, english_dir)
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
    keys_file = Path(keys_path)
    keys_file.parent.mkdir(parents=True, exist_ok=True)
    keys_file.write_text(json.dumps({"negativeMarking": index["negativeMarking"], "quizzes": keys},
                                    separators=(",", ":"), ensure_ascii=False, sort_keys=True) + "\n")
    check_ids(out, index)
    print(f"built {len(EXAMS)} exams + java into {out}")


def with_answer(p, answer, explanation, hi_expl=None):
    """A published question plus its answer, for the practice pools (which work offline)."""
    out = {**p, "answer": answer, "explanation": explanation}
    if hi_expl:
        out["hi"] = {**p["hi"], "explanation": hi_expl}
    return out


def write_extra(out, used, banks_dir, english_dir):
    """Questions of the original banks (slice 0, which older builds used for every exam) that no quiz uses now."""
    extra = []
    for subject in ("english", "quant", "reasoning"):
        for level, _ in LEVELS:
            for q in load_bank(subject, level, banks_dir, english_dir):
                q = edited(subject, q)
                p = published(subject, level, q)
                if p["id"] not in used and not q.get("hidden"):
                    used.add(p["id"])
                    extra.append(with_answer(p, q["answer"], with_tip(subject, q), hi_explanation(q)))
    (out / "practice-extra.json").write_text(json.dumps(extra, indent=1, ensure_ascii=False) + "\n")


def check_ids(out, index):
    """The same id must always mean the same question, or saved answers would point at the wrong one."""
    seen = {}
    for exam in index["exams"]:
        for stage in exam["stages"]:
            for section in stage["sections"]:
                for level in section["levels"]:
                    for quiz in level["quizzes"]:
                        for q in json.loads((out / quiz["file"]).read_text()):
                            key = (q["subject"], json.dumps(q.get("group", {}).get("setup"), sort_keys=True),
                                   q["question"], tuple(sorted(q["options"])))
                            if seen.setdefault(q["id"], key) != key:
                                sys.exit(f"id clash: {q['id']} in {quiz['file']}")


if __name__ == "__main__":
    if len(sys.argv) != 5:
        sys.exit(__doc__)
    main(*sys.argv[1:5])
