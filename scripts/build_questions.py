#!/usr/bin/env python3
"""Convert the legacy SQL question dump into the static JSON question bank.

Usage: python3 scripts/build_questions.py <input.sql> <output-data-dir>

Each "-- Section" comment in the SQL becomes one quiz file under
<output-data-dir>/<subject>/, and <subject>/quizzes.json lists them (scripts/build_exams.py builds the site index).
"""
import json
import re
import sys
from pathlib import Path

SUBJECT = "java"


def parse_row(line):
    """Parse one SQL tuple line like ('a', 'it''s', ...), returning its strings."""
    line = line.strip().rstrip(",;")
    assert line.startswith("(") and line.endswith(")"), line
    body, out, i = line[1:-1], [], 0
    while i < len(body):
        if body[i] == "'":
            i += 1
            buf = []
            while True:
                if body[i] == "'":
                    if i + 1 < len(body) and body[i + 1] == "'":
                        buf.append("'")
                        i += 2
                        continue
                    i += 1
                    break
                buf.append(body[i])
                i += 1
            out.append("".join(buf))
        else:
            i += 1
    return out


def slugify(text):
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


def main(src, out_dir):
    sections, current = [], None
    for line in Path(src).read_text().splitlines():
        if line.startswith("-- "):
            current = {"title": line[3:].strip(), "rows": []}
            sections.append(current)
        elif line.startswith("('") and current is not None:
            current["rows"].append(parse_row(line))

    out = Path(out_dir)
    (out / SUBJECT).mkdir(parents=True, exist_ok=True)
    quizzes = []
    for section in sections:
        if not section["rows"]:
            continue
        quiz_id = slugify(section["title"].replace("Questions", ""))
        questions = []
        for n, row in enumerate(section["rows"], 1):
            text, a, b, c, d, answer = row
            options = [a, b, c, d]
            assert answer in options, (section["title"], text)
            questions.append({
                "id": f"{SUBJECT}-{quiz_id}-{n}",
                "question": text,
                "options": options,
                "answer": options.index(answer),
                "explanation": "",
            })
        rel = f"{SUBJECT}/{quiz_id}.json"
        (out / rel).write_text(json.dumps(questions, indent=2, ensure_ascii=False) + "\n")
        quizzes.append({"id": quiz_id, "title": section["title"], "file": rel,
                        "count": len(questions)})

    (out / SUBJECT / "quizzes.json").write_text(json.dumps(quizzes, indent=2) + "\n")
    print(f"{sum(q['count'] for q in quizzes)} questions in {len(quizzes)} quizzes")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
