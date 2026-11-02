#!/usr/bin/env python3
"""Generate grouped question sets: data interpretation (DI) tables and floor puzzles.

Usage: python3 -I scripts/gen_groups.py <output-dir>
Writes <output-dir>/groups_{medium,hard}.json: a list of sets, each
{"id", "subject", "kind", "title", "setup", "questions": [{topic, question, options, answer, explanation}, ...]}.
"setup" is shown above every question of the set. "kind" is "table" (setup is {"caption", "columns", "rows"}) or
"text" (setup is a string). "subject" is the bank the set belongs to: DI is quant, puzzles are reasoning.
Every answer is computed from the generated data or proven by brute force, as in gen_banks.py. Reading
comprehension sets are hand-written in scripts/english_bank/rc_{medium,hard}.json, in the same shape (see build_exams.py).
"""
import itertools
import json
import random
import sys
from fractions import Fraction
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_banks import NAMES, fmt, make, numeric_options, steps  # noqa: E402

SETS_PER_LEVEL = 30
QUESTIONS_PER_SET = 5
BANKS = ["SBI", "PNB", "BOB", "Canara", "Union", "Indian"]
YEARS = [2020, 2021, 2022, 2023, 2024, 2025]


def num_q(topic, text, correct, r, slot, expl, unit=""):
    wrong = numeric_options(correct, r)
    if wrong is None:
        return None
    return make(topic, text, f"{fmt(correct)}{unit}", [f"{fmt(w)}{unit}" for w in wrong], expl, slot)


# --------------------------------------------------------------------- DI
def di_table(r, level, n):
    """Loans disbursed (₹ crore) by banks over years: a table, then 5 computed questions."""
    banks = r.sample(BANKS, 5)
    years = YEARS[-4:] if level == "medium" else YEARS[-5:]
    step = 10 if level == "medium" else 5
    data = {b: [r.randrange(100, 600, step) for _ in years] for b in banks}
    rows = [[b] + data[b] for b in banks]
    setup = {"caption": "Loans disbursed by five banks (₹ crore)", "columns": ["Bank"] + [str(y) for y in years],
             "rows": rows}
    qs, slot = [], n * 5
    b1, b2 = r.sample(banks, 2)
    y = r.randrange(len(years))

    # 1. Total of one bank over all years.
    tot = sum(data[b1])
    qs.append(num_q("data-interpretation", f"What is the total amount disbursed by {b1} over all the years shown (₹ crore)?",
                    tot, r, slot, steps(f"{b1}: {' + '.join(map(str, data[b1]))} = {tot}.")))
    # 2. Ratio between two banks in a year.
    a, b = data[b1][y], data[b2][y]
    ratio = Fraction(a, b)
    wrong = {Fraction(b, a), Fraction(a + step, b), Fraction(a, b + step), Fraction(a - step, b)} - {ratio}
    qs.append(make("data-interpretation", f"What is the ratio of {b1}'s disbursement to {b2}'s in {years[y]}?",
                   f"{ratio.numerator}:{ratio.denominator}",
                   [f"{w.numerator}:{w.denominator}" for w in sorted(wrong)[:3]],
                   steps(f"{years[y]}: {b1} = {a}, {b2} = {b}.", f"{a}:{b}, divide by {a // ratio.numerator}: "
                         f"{ratio.numerator}:{ratio.denominator}."), slot + 1))
    # 3. Percentage change for a bank between first and last year.
    first, last = data[b2][0], data[b2][-1]
    pct = round((last - first) * 100 / first, 2)
    qs.append(num_q("data-interpretation", f"By what percent did {b2}'s disbursement change from {years[0]} to "
                    f"{years[-1]}? (Negative means a fall.)", pct, r, slot + 2,
                    steps(f"{years[0]}: {first}; {years[-1]}: {last}.", f"Change = {last - first}.",
                          f"% change = {last - first}/{first} × 100 = {fmt(pct)}%."), "%"))
    # 4. Average of all banks in one year.
    col = [data[bk][y] for bk in banks]
    avg = Fraction(sum(col), len(col))
    qs.append(num_q("data-interpretation", f"What is the average disbursement of the five banks in {years[y]} (₹ crore)?",
                    float(avg), r, slot + 3,
                    steps(f"{years[y]}: {' + '.join(map(str, col))} = {sum(col)}.", f"Average = {sum(col)}/5 = {fmt(float(avg))}.")))
    # 5. Which bank had the highest total? (unique)
    totals = {bk: sum(data[bk]) for bk in banks}
    best = max(totals, key=totals.get)
    if list(totals.values()).count(totals[best]) > 1:
        return None
    wrong = [bk for bk in banks if bk != best]
    r.shuffle(wrong)
    qs.append(make("data-interpretation", "Which bank disbursed the most in total over all the years shown?", best,
                   wrong[:3], steps("Totals: " + ", ".join(f"{bk} {totals[bk]}" for bk in banks) + ".",
                                    f"Highest: {best} ({totals[best]})."), slot + 4))
    if None in qs:
        return None
    return {"subject": "quant", "kind": "table", "title": "Data interpretation", "setup": setup, "questions": qs}


# ----------------------------------------------------------------- puzzle
FLOOR_ITEMS = ["red", "blue", "green", "yellow", "white", "black", "pink"]


def floor_puzzle(r, level, n):
    """N people on floors 1 (bottom) to N, each with a colour; clues until exactly one arrangement fits."""
    size = 5 if level == "medium" else 6
    people = r.sample(NAMES, size)
    colours = r.sample(FLOOR_ITEMS, size)
    floor = dict(zip(people, r.sample(range(1, size + 1), size)))
    colour = dict(zip(people, colours))
    by_floor = {f: p for p, f in floor.items()}
    clues = []
    for a, b in itertools.permutations(people, 2):
        d = floor[a] - floor[b]
        if d == 1:
            clues.append((f"{a} lives immediately above {b}.", lambda F, a=a, b=b: F[a] - F[b] == 1))
        if d > 0:
            clues.append((f"{a} lives on a floor above {b}.", lambda F, a=a, b=b: F[a] > F[b]))
        if abs(d) == 2:
            clues.append((f"Exactly one person lives between {a} and {b}.", lambda F, a=a, b=b: abs(F[a] - F[b]) == 2))
    for p in people:
        f = floor[p]
        if f == 1:
            clues.append((f"{p} lives on the ground floor (floor 1).", lambda F, p=p: F[p] == 1))
        if f == size:
            clues.append((f"{p} lives on the top floor.", lambda F, p=p, s=size: F[p] == s))
        if f % 2 == 0:
            clues.append((f"{p} lives on an even-numbered floor.", lambda F, p=p: F[p] % 2 == 0))
        else:
            clues.append((f"{p} lives on an odd-numbered floor.", lambda F, p=p: F[p] % 2 == 1))
    r.shuffle(clues)
    alive = [dict(zip(people, perm)) for perm in itertools.permutations(range(1, size + 1))]
    chosen = []
    for text, pred in clues:
        nxt = [F for F in alive if pred(F)]
        if len(nxt) < len(alive):
            chosen.append(text)
            alive = nxt
        if len(alive) == 1:
            break
    if len(alive) != 1 or len(chosen) > 9:
        return None
    # Colours: each tied to a person or a floor, so the reader must place people first.
    colour_clues = []
    for p in people:
        if r.random() < .5:
            colour_clues.append(f"{p} likes {colour[p]}.")
        else:
            colour_clues.append(f"The person on floor {floor[p]} likes {colour[p]}.")
    r.shuffle(colour_clues)
    setup = (f"{size} people, {', '.join(sorted(people))}, live on the {size} floors of a building, one per floor "
             f"(floor 1 is the bottom). Each likes a different colour: {', '.join(sorted(colours))}.\n"
             + "\n".join(chosen + colour_clues))
    order = "Bottom to top: " + ", ".join(f"{f} {by_floor[f]} ({colour[by_floor[f]]})" for f in range(1, size + 1)) + "."
    expl = lambda last: steps("Place people using the floor clues first; only one arrangement fits all of them.",
                              order, last)
    qs, slot = [], n * 5
    top = by_floor[size]
    qs.append(make("puzzle", "Who lives on the top floor?", top, r.sample([p for p in people if p != top], 3),
                   expl(f"Top floor: {top}."), slot))
    p = r.choice(people)
    qs.append(make("puzzle", f"Which colour does {p} like?", colour[p], r.sample([c for c in colours if c != colour[p]], 3),
                   expl(f"{p}: {colour[p]}."), slot + 1))
    f = r.randint(2, size - 1)
    above = by_floor[f + 1]
    qs.append(make("puzzle", f"Who lives immediately above {by_floor[f]}?", above,
                   r.sample([x for x in people if x != above], 3), expl(f"Floor {f + 1}: {above}."), slot + 2))
    c = r.choice(colours)
    owner = next(x for x in people if colour[x] == c)
    qs.append(make("puzzle", f"On which floor does the person who likes {c} live?", str(floor[owner]),
                   r.sample([str(x) for x in range(1, size + 1) if x != floor[owner]], 3),
                   expl(f"{c} is {owner}'s colour; {owner} is on floor {floor[owner]}."), slot + 3))
    x, y = r.sample(people, 2)
    between = abs(floor[x] - floor[y]) - 1
    qs.append(make("puzzle", f"How many people live between {x} and {y}?", str(between),
                   [str(v) for v in r.sample([v for v in range(0, size - 1) if v != between], 3)],
                   expl(f"{x} is on floor {floor[x]}, {y} on floor {floor[y]}: {between} between."), slot + 4))
    return {"subject": "reasoning", "kind": "text", "title": "Puzzle", "setup": setup, "questions": qs}


GENERATORS = {"medium": [di_table, floor_puzzle], "hard": [di_table, floor_puzzle]}


def build(level, seed):
    r = random.Random(seed)
    out, n = [], 0
    while len(out) < SETS_PER_LEVEL:
        gen = GENERATORS[level][len(out) % len(GENERATORS[level])]
        s = gen(r, level, n)
        n += 1
        if s and all(len(set(q["options"])) == 4 for q in s["questions"]):
            s["id"] = f"g-{level}-{len(out) + 1}"
            out.append(s)
        if n > 2000:
            raise RuntimeError(f"groups {level}: only {len(out)} sets")
    return out


if __name__ == "__main__":
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    for i, level in enumerate(["medium", "hard"]):
        sets = build(level, 7000 + i)
        (out / f"groups_{level}.json").write_text(json.dumps(sets, indent=1, ensure_ascii=False) + "\n")
        print(f"groups_{level}: {len(sets)} sets, {sum(len(s['questions']) for s in sets)} questions")
