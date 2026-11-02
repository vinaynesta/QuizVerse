#!/usr/bin/env python3
"""Generate the Quantitative Aptitude and Reasoning question banks.

Every answer is computed (arithmetic) or proven by brute force (syllogisms,
inequalities, seating), so no answer is typed by hand.

Usage: python3 -I scripts/gen_banks.py <output-dir>
Writes <output-dir>/{quant,reasoning}_{easy,medium,hard}.json, BANK_SIZE questions each: EXAM_POOLS slices of
QUANT_PER_LEVEL, one per exam (build_exams.py), so exams don't share questions. The first slice is the original
bank and must stay unchanged (saved bookmarks and history point at its ids); the rest come from another seed.
"""
import itertools
import json
import math
import random
import sys
from fractions import Fraction
from pathlib import Path

PER_LEVEL = 200
QUANT_PER_LEVEL = 250  # numerical + reasoning banks: SBI Mains needs 50 questions x 5 quizzes
EXAM_POOLS = 6  # one slice per exam in build_exams.py EXAMS; keep in step with it
BANK_SIZE = QUANT_PER_LEVEL * EXAM_POOLS
NAMES = ["Amit", "Bhavna", "Chetan", "Deepa", "Eshan", "Farah", "Gautam", "Hema",
         "Imran", "Jaya", "Kiran", "Lata", "Manoj", "Neha", "Omkar", "Priya",
         "Rahul", "Sneha", "Tarun", "Usha", "Varun", "Wasim", "Yash", "Zoya"]


# ---------------------------------------------------------------- helpers
def fmt(x):
    if isinstance(x, Fraction):
        return f"{x.numerator}/{x.denominator}" if x.denominator != 1 else str(x.numerator)
    if abs(x - round(x)) < 1e-9:
        return str(int(round(x)))
    return f"{x:.2f}".rstrip("0").rstrip(".")


def numeric_options(correct, r):
    """Return 3 distinct plausible wrong values near `correct`."""
    c = float(correct)
    is_int = abs(c - round(c)) < 1e-9
    mags = [abs(c) * f for f in (0.05, 0.1, 0.15, 0.2, 0.25, 0.5)] + [1, 2, 3, 5, 10]
    cands = set()
    for m in mags:
        for sign in (1, -1):
            v = c + sign * m
            v = round(v) if is_int else round(v, 2)
            if v != c and v > 0 or (c <= 0 and v != c):
                cands.add(v)
    cands = sorted(cands)
    r.shuffle(cands)
    out, seen = [], {fmt(c)}
    for v in cands:
        if fmt(v) not in seen:
            out.append(v)
            seen.add(fmt(v))
        if len(out) == 3:
            return out
    return None


def steps(*lines):
    """A worked solution: one step per line (the app keeps the line breaks)."""
    return "\n".join(lines)


def make(topic, question, correct, wrong, expl, slot):
    """Place `correct` at index slot%4 among the options."""
    options = [str(w) for w in wrong]
    idx = slot % 4
    options.insert(idx, str(correct))
    if len(set(options)) != 4:
        return None
    return {"topic": topic, "question": question, "options": options,
            "answer": idx, "explanation": expl}


def num_q(topic, text, correct, r, slot, expl, unit_pre="", unit_post="", hi=None):
    """`hi` is an optional Hindi version {"question", "explanation"}; numeric options need no translation.
    It must not use `r`, so adding Hindi never changes the generated banks or their question ids."""
    wrong = numeric_options(correct, r)
    if wrong is None:
        return None
    f = lambda v: f"{unit_pre}{fmt(v)}{unit_post}"
    q = make(topic, text, f(correct), [f(w) for w in wrong], expl, slot)
    if q and hi:
        q["hi"] = hi
    return q


def hindi(question, *lines):
    """A Hindi version of a numeric question. Machine-drafted templates: marked for review by a fluent speaker."""
    return {"question": question, "explanation": steps(*lines), "needsReview": True}


def rate_pairs():
    return [(a, b, Fraction(a * b, a + b)) for a in range(4, 61) for b in range(a, 61)
            if (a * b) % (a + b) == 0]


# ------------------------------------------------------------- quant: easy
def q_pct_of(r, s):
    p = r.choice([5, 10, 15, 20, 25, 30, 35, 40, 45, 60, 75, 80])
    n = r.randrange(40, 1200, 20)
    if (p * n) % 100:
        return None
    a = p * n // 100
    return num_q("percentage", f"What is {p}% of {n}?", a, r, s, steps(f"{p}% means {p}/100.", f"{p}/100 × {n} = {p * n}/100 = {a}.",
                       f"Shortcut: 10% of {n} is {fmt(n / 10)}, so {p}% is {fmt(n / 10)} × {fmt(p / 10)} = {a}."),
                 hi=hindi(f"{n} का {p}% कितना है?", f"{p}% का अर्थ है {p}/100।", f"{p}/100 × {n} = {p * n}/100 = {a}।"))


def q_si(r, s):
    p, rt, t = r.randrange(1000, 20001, 500), r.randint(4, 12), r.randint(2, 6)
    a = p * rt * t // 100
    return num_q("simple-interest", f"Find the simple interest on ₹{p} at {rt}% per annum for {t} years.",
                 a, r, s, steps("SI = P × R × T / 100.", f"= {p} × {rt} × {t} / 100", f"= {p * rt * t} / 100 = ₹{a}."), "₹",
                 hi=hindi(f"₹{p} पर {rt}% वार्षिक दर से {t} वर्ष का साधारण ब्याज ज्ञात कीजिए।",
                          "साधारण ब्याज = मूलधन × दर × समय / 100।", f"= {p} × {rt} × {t} / 100", f"= {p * rt * t} / 100 = ₹{a}।"))


def q_avg(r, s):
    n = r.randint(4, 7)
    nums = [r.randint(10, 90) for _ in range(n - 1)]
    avg = r.randint(25, 60)
    last = avg * n - sum(nums)
    if last < 1:
        return None
    nums.append(last)
    r.shuffle(nums)
    return num_q("average", f"What is the average of {', '.join(map(str, nums))}?", avg, r, s,
                 steps(f"Add them: {' + '.join(map(str, nums))} = {sum(nums)}.",
                       f"There are {n} numbers, so average = {sum(nums)} / {n} = {avg}."))


def q_ratio_share(r, s):
    a, b = r.randint(1, 9), r.randint(1, 9)
    if a == b:
        return None
    x = (a + b) * r.randint(20, 300)
    share = x * a // (a + b)
    n1, n2 = r.sample(NAMES, 2)
    return num_q("ratio", f"₹{x} is divided between {n1} and {n2} in the ratio "
                 f"{a}:{b}. What is the first person's share?", share, r, s,
                 steps(f"Total parts = {a} + {b} = {a + b}.", f"One part = {x} / {a + b} = ₹{x // (a + b)}.",
                       f"First share = {a} parts = {a} × {x // (a + b)} = ₹{share}."), "₹")


def q_profit_pct(r, s):
    cp = r.randrange(200, 2000, 20)
    pr = r.choice([5, 10, 12.5, 15, 20, 25, 30, 40])
    sp = cp * (100 + pr) / 100
    if abs(sp - round(sp)) > 1e-9:
        return None
    return num_q("profit-loss", f"An article bought for ₹{cp} is sold for ₹{int(sp)}. Find the profit percent.",
                 pr, r, s, steps(f"Profit = SP − CP = {int(sp)} − {cp} = ₹{int(sp) - cp}.",
                                 f"Profit % = profit / CP × 100 = {int(sp) - cp}/{cp} × 100 = {fmt(pr)}%."), "", "%",
                 hi=hindi(f"₹{cp} में खरीदी गई एक वस्तु ₹{int(sp)} में बेची जाती है। लाभ प्रतिशत ज्ञात कीजिए।",
                          f"लाभ = विक्रय मूल्य − क्रय मूल्य = {int(sp)} − {cp} = ₹{int(sp) - cp}।",
                          f"लाभ % = लाभ / क्रय मूल्य × 100 = {int(sp) - cp}/{cp} × 100 = {fmt(pr)}%।"))


def q_speed(r, s):
    v, t = r.randrange(20, 100, 5), r.randint(2, 9)
    d = v * t
    if r.random() < .5:
        return num_q("speed-time-distance", f"A car covers {d} km in {t} hours. What is its speed in km/h?",
                     v, r, s, steps("Speed = distance / time.", f"= {d} km / {t} h = {v} km/h."),
                     hi=hindi(f"एक कार {t} घंटे में {d} किमी की दूरी तय करती है। उसकी चाल किमी/घंटा में कितनी है?",
                              "चाल = दूरी / समय।", f"= {d} किमी / {t} घंटे = {v} किमी/घंटा।"))
    return num_q("speed-time-distance", f"How many hours will a car moving at {v} km/h take to cover {d} km?",
                 t, r, s, steps("Time = distance / speed.", f"= {d} km / {v} km/h = {t} hours."),
                 hi=hindi(f"{v} किमी/घंटा की चाल से चलने वाली कार {d} किमी की दूरी कितने घंटे में तय करेगी?",
                          "समय = दूरी / चाल।", f"= {d} किमी / {v} किमी/घंटा = {t} घंटे।"))


def q_simplify(r, s):
    a, b, c, d = r.randint(10, 60), r.randint(2, 12), r.randint(2, 12), r.randint(5, 50)
    e = r.choice(["{a} + {b} × {c} − {d}", "{a} × {b} − {c} × {d}", "({a} + {b}) × {c} − {d}"])
    val = eval(e.format(a=a, b=b, c=c, d=d).replace("×", "*").replace("−", "-"))
    if val <= 0:
        return None
    if e.startswith("("):
        work = steps("BODMAS: brackets first.", f"({a} + {b}) = {a + b}.", f"{a + b} × {c} = {(a + b) * c}.",
                     f"{(a + b) * c} − {d} = {val}.")
    elif e.startswith("{a} +"):
        work = steps("BODMAS: multiply before adding or subtracting.", f"{b} × {c} = {b * c}.",
                     f"{a} + {b * c} − {d} = {val}.")
    else:
        work = steps("BODMAS: both multiplications first.", f"{a} × {b} = {a * b} and {c} × {d} = {c * d}.",
                     f"{a * b} − {c * d} = {val}.")
    return num_q("simplification", f"Simplify: {e.format(a=a, b=b, c=c, d=d)}", val, r, s, work)


def q_root(r, s):
    if r.random() < .6:
        n = r.randint(12, 45)
        return num_q("roots", f"What is the value of √{n * n}?", n, r, s,
                     steps(f"Look for the number that multiplied by itself gives {n * n}.",
                           f"{n} × {n} = {n * n}, so √{n * n} = {n}.",
                           f"Tip: a square ending in {(n * n) % 10} has a root ending in "
                           f"{' or '.join(str(d) for d in range(10) if d * d % 10 == (n * n) % 10)}."))
    n = r.randint(3, 14)
    return num_q("roots", f"What is the value of ∛{n ** 3}?", n, r, s,
                 steps(f"Find the number whose cube is {n ** 3}.", f"{n} × {n} × {n} = {n ** 3}, so ∛{n ** 3} = {n}."))


def q_series_easy(r, s):
    kind = r.choice(["ap", "sq", "dbl", "tri"])
    if kind == "ap":
        a, d = r.randint(2, 30), r.randint(2, 12)
        seq = [a + d * i for i in range(6)]
    elif kind == "sq":
        k = r.randint(2, 9)
        seq = [(k + i) ** 2 for i in range(6)]
    elif kind == "dbl":
        a = r.randint(2, 9)
        seq = [a * 2 ** i for i in range(6)]
    else:
        a, d = r.randint(1, 20), r.randint(2, 7)
        seq = [a]
        for i in range(5):
            seq.append(seq[-1] + d * (i + 1))
    pos = r.randint(2, 5)
    ans = seq[pos]
    shown = ["?" if i == pos else str(v) for i, v in enumerate(seq)]
    rule = {"ap": lambda: f"Each term is {seq[1] - seq[0]} more than the one before.",
            "sq": lambda: f"The terms are squares: {seq[0]} = {round(seq[0] ** .5)}², then {round(seq[0] ** .5) + 1}², "
                          "and so on.",
            "dbl": lambda: "Each term is double the one before.",
            "tri": lambda: f"The differences grow by {seq[2] - 2 * seq[1] + seq[0]}: "
                           f"{', '.join(str(seq[i + 1] - seq[i]) for i in range(5))}."}[kind]()
    return num_q("number-series", f"Find the missing number: {', '.join(shown)}", ans, r, s,
                 steps(rule, f"So the missing term (position {pos + 1}) is {ans}.",
                       f"Check: the full series is {', '.join(map(str, seq))}."))


def q_work_simple(r, s):
    a, b, t = r.choice(RATE_PAIRS)
    if a == b:
        return None
    return num_q("time-work", f"A can finish a job in {a} days and B in {b} days. In how many days will they "
                 f"finish it working together?", t, r, s,
                 steps(f"A does 1/{a} of the job a day; B does 1/{b}.",
                       f"Together: 1/{a} + 1/{b} = {b + a}/{a * b} = 1/{fmt(t)} of the job a day.",
                       f"So they need {fmt(t)} days.", f"Shortcut: (A × B)/(A + B) = {a * b}/{a + b} = {fmt(t)}."))


def q_discount(r, s):
    mp = r.randrange(200, 3000, 50)
    d = r.choice([5, 10, 15, 20, 25, 30])
    sp = mp * (100 - d) / 100
    if abs(sp - round(sp)) > 1e-9:
        return None
    return num_q("discount", f"The marked price of a bag is ₹{mp}. After a discount of {d}%, what is the selling price?",
                 sp, r, s, steps(f"Discount = {d}% of {mp} = ₹{fmt(mp * d / 100)}.",
                                 f"SP = MP − discount = {mp} − {fmt(mp * d / 100)} = ₹{int(sp)}.",
                                 f"Or directly: {mp} × {100 - d}/100 = ₹{int(sp)}."), "₹")


def q_lcm_hcf(r, s):
    a, b = r.randint(6, 60), r.randint(6, 60)
    if a == b:
        return None
    if r.random() < .5:
        return num_q("lcm-hcf", f"Find the LCM of {a} and {b}.", a * b // math.gcd(a, b), r, s,
                     steps(f"HCF of {a} and {b} is {math.gcd(a, b)}.", "LCM × HCF = product of the two numbers.",
                           f"LCM = {a} × {b} / {math.gcd(a, b)} = {a * b} / {math.gcd(a, b)} = {a * b // math.gcd(a, b)}."))
    g = math.gcd(a * 3, b * 3)
    return num_q("lcm-hcf", f"Find the HCF of {a * 3} and {b * 3}.", g, r, s,
                 steps(f"Euclid: divide the larger by the smaller and keep the remainder until it is 0.",
                       f"{max(a, b) * 3} and {min(a, b) * 3} share the factor {g}, and no larger one.",
                       f"Check: {a * 3} = {g} × {a * 3 // g}, {b * 3} = {g} × {b * 3 // g}, and "
                       f"{a * 3 // g} and {b * 3 // g} have no common factor. HCF = {g}."))


# ----------------------------------------------------------- quant: medium
def q_ci2(r, s):
    p = r.randrange(2000, 30001, 2000)
    rt = r.choice([5, 10, 20])
    ci = round(p * ((1 + rt / 100) ** 2 - 1))
    return num_q("compound-interest", f"Find the compound interest on ₹{p} at {rt}% per annum for 2 years, "
                 f"compounded annually.", ci, r, s,
                 steps(f"Year 1 interest: {rt}% of {p} = ₹{fmt(p * rt / 100)}; amount = ₹{fmt(p * (100 + rt) / 100)}.",
                       f"Year 2 interest: {rt}% of {fmt(p * (100 + rt) / 100)} = ₹{fmt(p * (100 + rt) * rt / 10000)}.",
                       f"CI = {fmt(p * rt / 100)} + {fmt(p * (100 + rt) * rt / 10000)} = ₹{ci}.",
                       f"Formula: P[(1 + R/100)² − 1] = {p} × {fmt((1 + rt / 100) ** 2 - 1)} = ₹{ci}."), "₹")


def q_successive(r, s):
    a, b = r.choice([10, 20, 25, 30, 40, 50]), r.choice([10, 20, 25, 30, 40, 50])
    net = a - b - a * b / 100
    word = r.choice(["increased", "decreased"])
    if word == "decreased":
        net = -a + b - a * b / 100 if False else (-a - b + a * b / 100)
        up = False
    else:
        up = True
    if up:
        txt = f"The price of an item is increased by {a}% and then decreased by {b}%. What is the net percentage change? (Give the magnitude.)"
        ans = abs(a - b - a * b / 100)
    else:
        txt = f"The price of an item is decreased by {a}% and then decreased by {b}%. What is the net percentage decrease?"
        ans = a + b - a * b / 100
    if ans == 0:
        return None
    sa, sb = (a, -b) if up else (-a, -b)
    work = steps("Net % change = x + y + xy/100, with increases + and decreases −.",
                 f"x = {sa:+d}, y = {sb:+d}: {sa:+d} {sb:+d} + ({sa})({sb})/100 = {fmt(sa + sb + sa * sb / 100)}%.",
                 f"Check with 100: 100 → {fmt(100 + sa)} → {fmt((100 + sa) * (100 + sb) / 100)}, "
                 f"a change of {fmt(abs(sa + sb + sa * sb / 100))}%.")
    return num_q("percentage", txt, ans, r, s, work, "", "%")


def q_profit_disc(r, s):
    x, y = r.choice([20, 25, 30, 40, 50, 60]), r.choice([5, 10, 15, 20, 25])
    p = (100 + x) * (100 - y) / 100 - 100
    if abs(p * 100 - round(p * 100)) > 1e-6 or p <= 0:
        return None
    return num_q("profit-loss", f"A shopkeeper marks goods {x}% above cost price and allows a discount of {y}%. "
                 f"What is his profit percent?", p, r, s,
                 steps("Take CP = ₹100.", f"Marked {x}% above: MP = ₹{100 + x}.",
                       f"{y}% discount on {100 + x}: SP = {100 + x} × {100 - y}/100 = ₹{(100 + x) * (100 - y) / 100:g}.",
                       f"Profit = {(100 + x) * (100 - y) / 100:g} − 100 = {fmt(p)} on 100, so {fmt(p)}%."), "", "%")


def q_alligation(r, s):
    a, b = r.randrange(20, 80, 5), r.randrange(80, 160, 5)
    m, n = r.randint(1, 5), r.randint(1, 5)
    if m == n or (a * m + b * n) % (m + n):
        return None
    mean = (a * m + b * n) // (m + n)
    return num_q("mixture", f"Two varieties of rice costing ₹{a}/kg and ₹{b}/kg are mixed in the ratio {m}:{n}. "
                 f"What is the cost per kg of the mixture?", mean, r, s,
                 steps(f"Cost of {m} kg at ₹{a} + {n} kg at ₹{b} = {a * m} + {b * n} = ₹{a * m + b * n}.",
                       f"Total weight = {m + n} kg.", f"Cost per kg = {a * m + b * n} / {m + n} = ₹{mean}.",
                       f"Alligation check: ({b} − {mean}) : ({mean} − {a}) = {b - mean} : {mean - a} = {m} : {n}."), "₹")


def q_train(r, s):
    v = r.choice([36, 54, 72, 90, 108])
    ms = v * 5 // 18
    t = r.randint(5, 20)
    L = ms * t
    if r.random() < .5:
        return num_q("trains", f"A train {L} m long runs at {v} km/h. In how many seconds does it cross a pole?",
                     t, r, s, steps(f"km/h to m/s: multiply by 5/18. {v} × 5/18 = {ms} m/s.",
                                    "To pass a pole the train covers its own length.",
                                    f"Time = {L} / {ms} = {t} seconds."))
    plat = ms * r.randint(5, 15)
    tt = (L + plat) // ms
    return num_q("trains", f"A train {L} m long, running at {v} km/h, crosses a platform {plat} m long. "
                 f"How many seconds does it take?", tt, r, s,
                 steps(f"Speed: {v} × 5/18 = {ms} m/s.", f"Distance = train + platform = {L} + {plat} = {L + plat} m.",
                       f"Time = {L + plat} / {ms} = {tt} seconds."))


def q_boat(r, s):
    b, st = r.randint(6, 20), r.randint(1, 5)
    if st >= b:
        return None
    t = r.randint(2, 6)
    d = (b + st) * t
    return num_q("boats-streams", f"A boat's speed in still water is {b} km/h and the stream flows at {st} km/h. "
                 f"How many hours will it take to go {d} km downstream?", t, r, s,
                 steps(f"Downstream, the stream helps: {b} + {st} = {b + st} km/h.",
                       f"Time = {d} / {b + st} = {t} hours."))


def q_pipes(r, s):
    for _ in range(50):
        a, b = r.randint(6, 30), r.randint(6, 30)
        c = r.randint(20, 60)
        den = Fraction(1, a) + Fraction(1, b) - Fraction(1, c)
        if den > 0 and (1 / den).denominator == 1 and a != b:
            t = int((1 / den))
            return num_q("pipes-cisterns", f"Pipes A and B can fill a tank in {a} and {b} hours, and an outlet C "
                         f"can empty it in {c} hours. If all three are opened together, in how many hours "
                         f"is the tank filled?", t, r, s,
                         steps(f"Per hour: A fills 1/{a}, B fills 1/{b}, C empties 1/{c}.",
                               f"Net = 1/{a} + 1/{b} − 1/{c} = 1/{t} of the tank per hour.",
                               f"So the tank fills in {t} hours."))
    return None


def q_ages(r, s):
    if r.random() < .5:
        k = r.randint(2, 5)
        son = r.randint(8, 30)
        tot = son * (k + 1)
        return num_q("ages", f"The sum of a father's and his son's ages is {tot} years. The father is {k} times as "
                     f"old as the son. What is the son's age?", son, r, s,
                     steps(f"Let the son be x; the father is {k}x.", f"x + {k}x = {k + 1}x = {tot}.",
                           f"x = {tot} / {k + 1} = {son} years."))
    f_, so = r.randint(35, 60), r.randint(8, 25)
    t = f_ - 2 * so
    if t <= 0:
        return None
    return num_q("ages", f"A father is {f_} years old and his son is {so}. After how many years will the father be "
                 f"twice as old as the son?", t, r, s,
                 steps("After x years: father = {0} + x, son = {1} + x.".format(f_, so),
                       f"{f_} + x = 2({so} + x) = {2 * so} + 2x.", f"x = {f_} − {2 * so} = {t} years."))


def q_partner(r, s):
    x, y = r.randrange(2000, 10001, 1000), r.randrange(2000, 10001, 1000)
    m = r.choice([6, 8, 9])
    profit = r.randrange(1000, 10001, 100)
    ra, rb = x * 12, y * m
    if (profit * ra) % (ra + rb):
        return None
    ans = profit * ra // (ra + rb)
    return num_q("partnership", f"A invests ₹{x} for 12 months and B invests ₹{y} for {m} months. Their total profit "
                 f"is ₹{profit}. What is A's share?", ans, r, s,
                 steps("Profit is shared in the ratio of capital × time.",
                       f"A: {x} × 12 = {ra}; B: {y} × {m} = {rb}. Ratio {ra // math.gcd(ra, rb)} : {rb // math.gcd(ra, rb)}.",
                       f"A's share = {ra // math.gcd(ra, rb)}/{(ra + rb) // math.gcd(ra, rb)} × {profit} = ₹{ans}."), "₹")


def factorise(n):
    """12 -> '2² × 3'."""
    out, p, sup = [], 2, str.maketrans("0123456789", "⁰¹²³⁴⁵⁶⁷⁸⁹")
    while n > 1:
        k = 0
        while n % p == 0:
            n //= p
            k += 1
        if k:
            out.append(str(p) + (str(k).translate(sup) if k > 1 else ""))
        p += 1
    return " × ".join(out)


def q_lcm_word(r, s):
    nums = sorted(r.sample([6, 8, 9, 10, 12, 15, 18, 20, 24, 30], 3))
    l = math.lcm(*nums)
    return num_q("lcm-hcf", f"What is the smallest number divisible by {nums[0]}, {nums[1]} and {nums[2]}?",
                 l, r, s, steps("The smallest number divisible by all of them is their LCM.",
                                f"Prime factors: {', '.join(f'{x} = {factorise(x)}' for x in nums)}.",
                                f"Take each prime at its highest power: LCM = {factorise(l)} = {l}."))


def q_avg_replace(r, s):
    n, old = r.randint(5, 10), r.randint(20, 40)
    avg = r.randint(20, 40)
    new = old + n * r.randint(2, 6)
    return num_q("average", f"The average of {n} numbers is {avg}. If one number, {old}, is replaced by {new}, "
                 f"what is the new average?", avg + (new - old) / n, r, s,
                 steps(f"Old sum = {n} × {avg} = {n * avg}.", f"Replacing {old} by {new} adds {new - old}: new sum = {n * avg + new - old}.",
                       f"New average = {n * avg + new - old} / {n} = {fmt(avg + (new - old) / n)}.",
                       f"Shortcut: the average rises by {new - old}/{n} = {fmt((new - old) / n)}."))


def q_population(r, s):
    p = r.randrange(10000, 60001, 5000)
    a, b = r.choice([10, 20, 25]), r.choice([10, 20, 50])
    ans = p * (100 + a) * (100 + b) // 10000
    if (p * (100 + a) * (100 + b)) % 10000:
        return None
    return num_q("percentage", f"The population of a town is {p}. It grows by {a}% in the first year and by {b}% in "
                 f"the second year. What is the population after two years?", ans, r, s,
                 steps(f"After year 1: {p} × {100 + a}/100 = {p * (100 + a) // 100}.",
                       f"After year 2: {p * (100 + a) // 100} × {100 + b}/100 = {ans}."))


def q_si_multiple(r, s):
    n = r.randint(4, 12)
    k = r.choice([3, 4, 5])
    return num_q("simple-interest", f"A sum becomes double in {n} years at simple interest. In how many years will "
                 f"it become {k} times itself?", (k - 1) * n, r, s,
                 steps(f"Doubling means interest = P (one times the sum) in {n} years.",
                       f"{k} times itself means interest = {k - 1}P.",
                       f"Simple interest grows evenly, so {k - 1}P takes {k - 1} × {n} = {(k - 1) * n} years."))


# ------------------------------------------------------------- quant: hard
def q_ci_si_diff(r, s):
    rt = r.choice([5, 8, 10, 12, 15, 20])
    p = r.randrange(5000, 60001, 2500)
    d = p * rt * rt / 10000
    if abs(d - round(d)) > 1e-9:
        return None
    return num_q("compound-interest", f"What is the difference between the compound interest and simple interest "
                 f"on ₹{p} for 2 years at {rt}% per annum?", d, r, s,
                 steps(f"SI for 2 years = {p} × {rt} × 2/100 = ₹{fmt(p * rt * 2 / 100)}.",
                       f"CI adds interest on the first year's interest: {rt}% of {fmt(p * rt / 100)} = ₹{int(d)}.",
                       f"Shortcut for 2 years: P(R/100)² = {p} × ({rt}/100)² = ₹{int(d)}."), "₹")


def q_work3(r, s):
    cands = [10, 12, 15, 20, 24, 30, 40, 60]
    for _ in range(80):
        a, b, c = (r.choice(cands) for _ in range(3))
        pairs = [Fraction(1, a) + Fraction(1, b), Fraction(1, b) + Fraction(1, c), Fraction(1, a) + Fraction(1, c)]
        if all((1 / x).denominator == 1 for x in pairs) and len({a, b, c}) == 3:
            ab, bc, ac = (int(1 / x) for x in pairs)
            return num_q("time-work", f"A and B together can do a job in {ab} days, B and C in {bc} days, and A and C "
                         f"in {ac} days. In how many days can A alone do it?", a, r, s,
                         steps(f"Rates per day: A+B = 1/{ab}, B+C = 1/{bc}, A+C = 1/{ac}.",
                               f"Adding all three gives 2(A+B+C) = {fmt(Fraction(1, ab) + Fraction(1, bc) + Fraction(1, ac))}, "
                               f"so A+B+C = {fmt((Fraction(1, ab) + Fraction(1, bc) + Fraction(1, ac)) / 2)}.",
                               f"A = (A+B+C) − (B+C) = {fmt((Fraction(1, ab) + Fraction(1, bc) + Fraction(1, ac)) / 2)} − 1/{bc} = 1/{a}.",
                               f"So A alone takes {a} days."))
    return None


def q_work_leave(r, s):
    for _ in range(80):
        a, b = r.randint(10, 40), r.randint(10, 40)
        t = r.randint(2, 8)
        rem = 1 - t * (Fraction(1, a) + Fraction(1, b))
        days = rem * b
        if a != b and rem > 0 and days.denominator == 1:
            return num_q("time-work", f"A can complete a work in {a} days and B in {b} days. They work together for "
                         f"{t} days, then A leaves. In how many more days does B finish the remaining work?",
                         int(days), r, s,
                         steps(f"Together per day: 1/{a} + 1/{b} = {fmt(Fraction(1, a) + Fraction(1, b))}.",
                               f"In {t} days: {t} × {fmt(Fraction(1, a) + Fraction(1, b))} = {fmt(1 - rem)} of the work.",
                               f"Left: 1 − {fmt(1 - rem)} = {fmt(rem)}.",
                               f"B alone: {fmt(rem)} × {b} = {int(days)} days."))
    return None


def q_replace(r, s):
    for _ in range(80):
        L = r.choice([40, 50, 60, 80, 100, 120, 150, 200])
        x = r.choice([5, 8, 10, 15, 20, 25, 30])
        left = Fraction(L) * (1 - Fraction(x, L)) ** 2
        if x < L and left.denominator == 1:
            return num_q("mixture", f"A vessel holds {L} litres of pure milk. {x} litres are removed and replaced "
                         f"with water, and the process is repeated once more. How many litres of milk remain?",
                         int(left), r, s,
                         steps(f"Each round keeps (1 − {x}/{L}) = {fmt(1 - Fraction(x, L))} of the milk.",
                               f"After round 1: {L} × {fmt(1 - Fraction(x, L))} = {fmt(L * (1 - Fraction(x, L)))} litres.",
                               f"After round 2: {fmt(L * (1 - Fraction(x, L)))} × {fmt(1 - Fraction(x, L))} = {int(left)} litres."))
    return None


def q_boat_hard(r, s):
    for _ in range(80):
        b, st = r.randint(8, 24), r.randint(2, 7)
        if st >= b:
            continue
        up, down = b - st, b + st
        d = math.lcm(up, down) * r.randint(1, 3)
        t_up, t_down = d // up, d // down
        if r.random() < .5:
            return num_q("boats-streams", f"A boat takes {t_up} hours to go {d} km upstream and {t_down} hours to "
                         f"return downstream. Find the speed of the boat in still water (km/h).", b, r, s,
                         steps(f"Upstream speed = {d}/{t_up} = {up} km/h; downstream = {d}/{t_down} = {down} km/h.",
                               f"Boat = (down + up)/2 = ({down} + {up})/2 = {b} km/h."))
        return num_q("boats-streams", f"A boat takes {t_up} hours to go {d} km upstream and {t_down} hours to "
                     f"return downstream. Find the speed of the stream (km/h).", st, r, s,
                     steps(f"Upstream speed = {d}/{t_up} = {up} km/h; downstream = {d}/{t_down} = {down} km/h.",
                           f"Stream = (down − up)/2 = ({down} − {up})/2 = {st} km/h."))
    return None


def q_prob_dice(r, s):
    k = r.randint(3, 11)
    cnt = sum(1 for a in range(1, 7) for b in range(1, 7) if a + b == k)
    ans = Fraction(cnt, 36)
    pool = {Fraction(i, 36) for i in range(1, 12)} - {ans}
    wrong = r.sample(sorted(pool), 3)
    return make("probability", f"Two fair dice are thrown. What is the probability that the sum is {k}?",
                fmt(ans), [fmt(w) for w in wrong],
                steps("Two dice give 6 × 6 = 36 equally likely outcomes.",
                      f"Sum {k}: " + ", ".join(f"({a},{k - a})" for a in range(1, 7) if 1 <= k - a <= 6) + f" = {cnt} outcomes.",
                      f"Probability = {cnt}/36 = {fmt(ans)}."), r.randrange(4))


def q_prob_balls(r, s):
    red, blue, green = r.randint(2, 6), r.randint(2, 6), r.randint(1, 5)
    n = red + blue + green
    same = Fraction(sum(c * (c - 1) for c in (red, blue, green)), n * (n - 1))
    pool = set()
    while len(pool) < 6:
        den = r.choice([n, n * (n - 1) // 2, 2 * n, n + 1, 3 * n])
        pool.add(Fraction(r.randint(1, den - 1), den))
    pool.discard(same)
    wrong = r.sample(sorted(pool), 3)
    return make("probability", f"A bag has {red} red, {blue} blue and {green} green balls. Two balls are drawn "
                f"without replacement. What is the probability that both are of the same colour?",
                fmt(same), [fmt(w) for w in wrong],
                steps(f"Total balls = {n}. Ordered ways to draw two = {n} × {n - 1} = {n * (n - 1)}.",
                      f"Both red: {red}×{red - 1} = {red * (red - 1)}; both blue: {blue}×{blue - 1} = {blue * (blue - 1)}; "
                      f"both green: {green}×{green - 1} = {green * (green - 1)}.",
                      f"Probability = {red * (red - 1) + blue * (blue - 1) + green * (green - 1)}/{n * (n - 1)} = {fmt(same)}."),
                r.randrange(4))


def q_perm(r, s):
    if r.random() < .5:
        word = r.choice(["LEADER", "BANANA", "LETTER", "COMMITTEE", "BALLOON", "SUCCESS", "PEPPER", "APPLE",
                         "GARDEN", "MISSISSIPPI", "STATISTICS", "ARRANGE"])
        cnt = math.factorial(len(word))
        for ch in set(word):
            cnt //= math.factorial(word.count(ch))
        return num_q("permutation-combination", f"In how many distinct ways can the letters of the word "
                     f"{word} be arranged?", cnt, r, s,
                     steps(f"{word} has {len(word)} letters: {len(word)}! = {math.factorial(len(word))} if all were different.",
                           "Repeated letters: " + (", ".join(f"{ch} ×{word.count(ch)}" for ch in sorted(set(word)) if word.count(ch) > 1) or "none") + ".",
                           f"Divide by each repeat's factorial: {math.factorial(len(word))} / "
                           f"{' × '.join(f'{word.count(ch)}!' for ch in sorted(set(word)) if word.count(ch) > 1) or '1'} = {cnt}."))
    m, w = r.randint(4, 8), r.randint(3, 6)
    k = r.randint(3, min(5, m + w - 1))
    ans = sum(math.comb(m, i) * math.comb(w, k - i) for i in range(1, min(m, k) + 1) if k - i <= w)
    return num_q("permutation-combination", f"A committee of {k} is chosen from {m} men and {w} women. In how many "
                 f"ways can it be formed with at least one man?", ans, r, s,
                 steps(f"All committees: C({m + w},{k}) = {math.comb(m + w, k)}.",
                       f"Committees with no man (all women): C({w},{k}) = {math.comb(w, k)}.",
                       f"At least one man = {math.comb(m + w, k)} − {math.comb(w, k)} = {ans}."))


def q_quadratic(r, s):
    def roots():
        a, b = r.randint(-9, 9), r.randint(-9, 9)
        return (a, b) if a and b else None
    for _ in range(30):
        x, y = roots(), roots()
        if not x or not y:
            continue
        def eq(p, v):
            b = -(p[0] + p[1])
            c = p[0] * p[1]
            bs = f"{'+' if b >= 0 else '−'} {abs(b) if abs(b) != 1 else ''}{v}" if b else ""
            cs = f"{'+' if c >= 0 else '−'} {abs(c)}"
            return f"{v}² {bs} {cs} = 0".replace("  ", " ")
        pairs = [(a, b) for a in x for b in y]
        gt = all(a > b for a, b in pairs)
        ge = all(a >= b for a, b in pairs)
        lt = all(a < b for a, b in pairs)
        le = all(a <= b for a, b in pairs)
        eqq = all(a == b for a, b in pairs)
        if eqq:
            ans = "x = y"
        elif gt:
            ans = "x > y"
        elif lt:
            ans = "x < y"
        elif ge:
            ans = "x ≥ y"
        elif le:
            ans = "x ≤ y"
        else:
            ans = "No relation can be established"
        opts = ["x > y", "x < y", "x ≥ y", "x ≤ y", "x = y", "No relation can be established"]
        wrong = [o for o in opts if o != ans]
        r.shuffle(wrong)
        return make("quadratic-equations", f"Solve both equations and compare x and y.\nI. {eq(x, 'x')}\nII. {eq(y, 'y')}",
                    ans, wrong[:3],
                    steps(f"I factorises to (x − {x[0]})(x − {x[1]}) = 0, so x = {x[0]} or {x[1]}.".replace("− -", "+ "),
                          f"II factorises to (y − {y[0]})(y − {y[1]}) = 0, so y = {y[0]} or {y[1]}.".replace("− -", "+ "),
                          "Compare every pair: " + ", ".join(f"{a} vs {b}" for a, b in pairs) + ".",
                          f"Result: {ans}."), r.randrange(4))
    return None


def q_series_hard(r, s):
    kind = r.choice(["dd", "mulk", "alt", "cube", "sqpm"])
    if kind == "dd":
        a, d, e = r.randint(2, 20), r.randint(2, 8), r.randint(1, 5)
        seq, diff = [a], d
        for _ in range(6):
            seq.append(seq[-1] + diff)
            diff += e
    elif kind == "mulk":
        a, m, k = r.randint(2, 6), r.randint(2, 4), r.randint(1, 6)
        seq = [a]
        for _ in range(6):
            seq.append(seq[-1] * m + k)
    elif kind == "alt":
        a, d1 = r.randint(2, 15), r.randint(2, 9)
        b, m2 = r.randint(3, 9), r.randint(2, 3)
        seq = []
        for i in range(4):
            seq += [a + d1 * i, b * m2 ** i]
    elif kind == "cube":
        k, c = r.randint(2, 6), r.randint(-3, 6)
        seq = [(k + i) ** 3 + c for i in range(7)]
    else:
        k, c = r.randint(2, 8), r.randint(1, 5)
        seq = [(k + i) ** 2 + (c if i % 2 == 0 else -c) for i in range(7)]
    pos = r.randint(3, len(seq) - 2)
    ans = seq[pos]
    shown = ["?" if i == pos else str(v) for i, v in enumerate(seq)]
    rule = {"dd": lambda: f"Differences: {', '.join(str(seq[i + 1] - seq[i]) for i in range(len(seq) - 1))}; "
                          f"each grows by {e}.",
            "mulk": lambda: f"Each term = previous × {m} + {k}.",
            "alt": lambda: f"Two series alternate: odd places add {d1} ({', '.join(map(str, seq[0::2]))}), "
                           f"even places multiply by {m2} ({', '.join(map(str, seq[1::2]))}).",
            "cube": lambda: f"Each term = n³ {'+' if c >= 0 else '−'} {abs(c)} for n = {k}, {k + 1}, …",
            "sqpm": lambda: f"Each term = n² + {c}, then n² − {c}, alternately, for n = {k}, {k + 1}, …"}[kind]()
    return num_q("number-series", f"Find the missing number: {', '.join(shown)}", ans, r, s,
                 steps(rule, f"So the missing term is {ans}.", f"Full series: {', '.join(map(str, seq))}."))


def q_trains_cross(r, s):
    for _ in range(80):
        l1, l2 = r.randrange(100, 400, 10), r.randrange(100, 400, 10)
        v1, v2 = r.randrange(36, 100, 6), r.randrange(36, 100, 6)
        same = r.random() < .5
        rel = abs(v1 - v2) if same else v1 + v2
        if rel == 0:
            continue
        t = Fraction((l1 + l2) * 18, rel * 5)
        if t.denominator == 1:
            way = "in the same direction" if same else "in opposite directions"
            return num_q("trains", f"Two trains {l1} m and {l2} m long run at {v1} km/h and {v2} km/h {way} on "
                         f"parallel tracks. In how many seconds do they completely cross each other?", int(t), r, s,
                         steps(f"Relative speed ({'subtract' if same else 'add'}, {way}): "
                               f"{v1} {'−' if same else '+'} {v2} = {rel} km/h = {rel} × 5/18 = {fmt(Fraction(rel * 5, 18))} m/s.",
                               f"Distance to cross = both lengths = {l1} + {l2} = {l1 + l2} m.",
                               f"Time = {l1 + l2} / {fmt(Fraction(rel * 5, 18))} = {int(t)} seconds."))
    return None


def q_avg_group(r, s):
    n, a = r.randint(4, 9), r.randint(20, 40)
    inc = r.randint(1, 3)
    ans = (n + 1) * (a + inc) - n * a
    return num_q("average", f"The average age of {n} people is {a} years. When a new person joins, the average rises "
                 f"to {a + inc}. How old is the new person?", ans, r, s,
                 steps(f"Old total = {n} × {a} = {n * a}.", f"New total = {n + 1} × {a + inc} = {(n + 1) * (a + inc)}.",
                       f"New person = {(n + 1) * (a + inc)} − {n * a} = {ans} years."))


def q_pct_less(r, s):
    x = r.choice([25, 50, 100, 150, 300, 400])
    ans = 100 * x / (100 + x)
    return num_q("percentage", f"A's salary is {x}% more than B's. B's salary is what percent less than A's?", ans,
                 r, s, steps("Take B = 100.", f"A is {x}% more: A = {100 + x}.",
                             f"B is {x} less than A; as a share of A: {x}/{100 + x} × 100 = {fmt(ans)}%."), "", "%")


QUANT = {
    "easy": [q_pct_of, q_si, q_avg, q_ratio_share, q_profit_pct, q_speed, q_simplify, q_root, q_series_easy,
             q_work_simple, q_discount, q_lcm_hcf],
    "medium": [q_ci2, q_successive, q_profit_disc, q_alligation, q_train, q_boat, q_pipes, q_ages, q_partner,
               q_lcm_word, q_avg_replace, q_population, q_si_multiple],
    "hard": [q_ci_si_diff, q_work3, q_work_leave, q_replace, q_boat_hard, q_prob_dice, q_prob_balls, q_perm,
             q_quadratic, q_series_hard, q_trains_cross, q_avg_group, q_pct_less],
}
RATE_PAIRS = rate_pairs()


# ------------------------------------------------------- reasoning helpers
OPT5 = ["Only conclusion I follows", "Only conclusion II follows", "Either conclusion I or II follows",
        "Neither conclusion I nor II follows", "Both conclusions I and II follow"]


def verdict(i_models, ii_models, models, same_pair=False):
    i = all(i_models(m) for m in models)
    ii = all(ii_models(m) for m in models)
    if i and ii:
        return 4
    if i:
        return 0
    if ii:
        return 1
    if same_pair and all(i_models(m) or ii_models(m) for m in models):
        return 2
    return 3


def five_opt(topic, text, ans_idx, expl):
    return {"topic": topic, "question": text, "options": list(OPT5), "answer": ans_idx, "explanation": expl}


# ------------------------------------------------------------- syllogism
OBJECTS = ["pens", "books", "tables", "chairs", "lamps", "boxes", "bags", "clocks", "keys", "coins", "doors",
           "roads", "trees", "birds", "rivers", "stones", "flowers", "cups", "hills", "shirts"]
_MODELS = {}


def syl_models(n):
    """All possible non-empty-region sets for n terms (regions = subsets of terms)."""
    if n not in _MODELS:
        regions = range(1, 2 ** n)  # a region is a bitmask of the sets it lies in (exclude outside-all)
        ms = []
        for mask in range(1, 2 ** len(regions)):
            ms.append([reg for k, reg in enumerate(regions) if mask >> k & 1])
        _MODELS[n] = ms
    return _MODELS[n]


def p_all(x, y):
    return lambda m: not any((reg >> x & 1) and not (reg >> y & 1) for reg in m)


def p_some(x, y):
    return lambda m: any((reg >> x & 1) and (reg >> y & 1) for reg in m)


def p_no(x, y):
    return lambda m: not any((reg >> x & 1) and (reg >> y & 1) for reg in m)


def p_somenot(x, y):
    return lambda m: any((reg >> x & 1) and not (reg >> y & 1) for reg in m)


KINDS = [("All {x} are {y}.", p_all), ("Some {x} are {y}.", p_some), ("No {x} is {y}.", p_no),
         ("Some {x} are not {y}.", p_somenot)]


def q_syllogism(r, s, nterms, nstmt):
    for _ in range(100):
        terms = r.sample(OBJECTS, nterms)
        stmts = []
        order = list(range(nterms))
        for i in range(nstmt):
            x, y = (order[i], order[i + 1]) if i + 1 < nterms else r.sample(order, 2)
            kind = r.choice(KINDS)
            stmts.append((x, y, kind))
        preds = [k[1](x, y) for x, y, k in stmts]
        models = [m for m in syl_models(nterms) if all(p(m) for p in preds)]
        if not models:
            continue
        concl = []
        for _c in range(2):
            x, y = r.sample(range(nterms), 2)
            kind = r.choice(KINDS)
            concl.append((x, y, kind))
        if concl[0][:2] == concl[1][:2] and concl[0][2] is concl[1][2]:
            continue
        pi, pii = (k[1](x, y) for x, y, k in concl)
        ans = verdict(pi, pii, models, set(concl[0][:2]) == set(concl[1][:2]))
        lines = " ".join(k[0].format(x=terms[x], y=terms[y]) for x, y, k in stmts)
        c_txt = [k[0].format(x=terms[x], y=terms[y]).replace("Some ", "Some ") for x, y, k in concl]
        text = f"Statements: {lines}\nConclusions:\nI. {c_txt[0]}\nII. {c_txt[1]}"
        def judge(name, pred, c):
            if all(pred(m) for m in models):
                return f"{name} ({c}) is true in every diagram the statements allow, so it follows."
            return f"{name} ({c}) fails in at least one possible diagram, so it does not follow."
        work = [f"Draw the statements as Venn diagrams: {lines}",
                judge("Conclusion I", pi, c_txt[0]), judge("Conclusion II", pii, c_txt[1])]
        if ans == 2:
            work.append("I and II are about the same pair and between them cover every case, so either I or II follows.")
        work.append(f"Answer: {OPT5[ans]}.")
        return five_opt("syllogism", text, ans, steps(*work))
    return None


# ------------------------------------------------------------ inequality
SYMS = [">", "<", "≥", "≤", "="]


def sym_ok(sym, a, b):
    return {">": a > b, "<": a < b, "≥": a >= b, "≤": a <= b, "=": a == b}[sym]


def q_inequality(r, s, n):
    for _ in range(100):
        letters = r.sample("ABCDEFGHJKLMNPQRSTUVWXYZ", n)
        syms = [r.choice(SYMS) for _ in range(n - 1)]
        if len(set(syms)) == 1:
            continue
        models = [v for v in itertools.product(range(n), repeat=n)
                  if all(sym_ok(syms[i], v[i], v[i + 1]) for i in range(n - 1))]
        idx = {l: k for k, l in enumerate(letters)}

        def conclusion():
            a, b = r.sample(letters, 2)
            derivable = [sy for sy in SYMS if all(sym_ok(sy, m[idx[a]], m[idx[b]]) for m in models)]
            if derivable and r.random() < .5:
                sy = r.choice(derivable)
            else:
                sy = r.choice(SYMS)
            return a, sy, b
        c1, c2 = conclusion(), conclusion()
        if c1 == c2:
            continue
        pi = lambda m, c=c1: sym_ok(c[1], m[idx[c[0]]], m[idx[c[2]]])
        pii = lambda m, c=c2: sym_ok(c[1], m[idx[c[0]]], m[idx[c[2]]])
        ans = verdict(pi, pii, models, {c1[0], c1[2]} == {c2[0], c2[2]})
        stm = " ".join(f"{letters[i]} {syms[i]}" for i in range(n - 1)) + f" {letters[-1]}"
        text = (f"Statements: {stm}\nConclusions:\nI. {c1[0]} {c1[1]} {c1[2]}\nII. {c2[0]} {c2[1]} {c2[2]}")
        def judge(name, c, pred):
            if all(pred(m) for m in models):
                return f"{name}: {c[0]} {c[1]} {c[2]} always holds in the chain, so it follows."
            bad = next(m for m in models if not pred(m))
            return (f"{name}: {c[0]} {c[1]} {c[2]} can fail (e.g. {c[0]} = {bad[idx[c[0]]]}, {c[2]} = {bad[idx[c[2]]]} "
                    f"fits every statement), so it does not follow.")
        work = [f"Read the chain {stm} from {c1[0]} to {c1[2]} and from {c2[0]} to {c2[2]}. "
                "A mix of > and < (or ≥ and ≤) between two letters breaks the link.",
                judge("I", c1, pi), judge("II", c2, pii)]
        if ans == 2:
            work.append("I and II compare the same two letters and together cover every case, so either follows.")
        work.append(f"Answer: {OPT5[ans]}.")
        return five_opt("inequality", text, ans, steps(*work))
    return None


# ----------------------------------------------------------------- series
def q_num_series(r, s, level):
    return q_series_easy(r, s) if level == "easy" else q_series_hard(r, s)


# --------------------------------------------------------------- alphabet
ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"


def shift_word(w, shifts):
    return "".join(ALPHA[(ALPHA.index(c) + sh) % 26] for c, sh in zip(w, shifts))


WORDS = ["PLAN", "CODE", "BANK", "LOAN", "CASH", "NOTE", "RATE", "GOLD", "FUND", "COIN", "DEBT", "TRADE",
         "CLERK", "SCORE", "TABLE", "MONEY", "STOCK", "PRICE", "SHARE", "BOARD"]


def q_alphabet(r, s):
    t = r.choice(["pos", "right", "between", "sum"])
    if t == "pos":
        n = r.randint(3, 24)
        return make("alphabet", f"Which letter is at the {n}th position from the left end of the English alphabet?",
                    ALPHA[n - 1], r.sample([c for c in ALPHA if c != ALPHA[n - 1]], 3),
                    steps("Positions: A=1, E=5, J=10, O=15, T=20, Y=25 (the EJOTY trick).",
                          f"Position {n} is {ALPHA[n - 1]}."), s)
    if t == "right":
        a = r.randint(0, 15)
        k = r.randint(3, 9)
        return make("alphabet", f"Which letter is {k}th to the right of {ALPHA[a]} in the English alphabet?",
                    ALPHA[a + k], r.sample([c for c in ALPHA if c != ALPHA[a + k]], 3),
                    steps(f"{ALPHA[a]} is letter {a + 1}.", f"{k} places to the right: {a + 1} + {k} = {a + k + 1}.",
                          f"Letter {a + k + 1} is {ALPHA[a + k]}."), s)
    if t == "between":
        a = r.randint(0, 12)
        b = a + r.randint(4, 12)
        ans = b - a - 1
        return num_q("alphabet", f"How many letters are there between {ALPHA[a]} and {ALPHA[b]} in the English "
                     f"alphabet?", ans, r, s,
                     steps(f"{ALPHA[a]} is letter {a + 1} and {ALPHA[b]} is letter {b + 1}.",
                           f"Letters strictly between: {b + 1} − {a + 1} − 1 = {ans} ({ALPHA[a + 1:b]})."))
    w = r.choice(WORDS)
    val = sum(ALPHA.index(c) + 1 for c in w)
    return num_q("alphabet", f"If A=1, B=2, ..., Z=26, what is the sum of the letter values of the word {w}?", val,
                 r, s, steps(" + ".join(f"{c}={ALPHA.index(c) + 1}" for c in w) + ".",
                             f"Sum = {' + '.join(str(ALPHA.index(c) + 1) for c in w)} = {val}."))


def q_coding(r, s, level):
    w = r.choice(WORDS)
    if level == "easy":
        k = r.randint(1, 4)
        shifts = [k] * len(w)
        how = f"each letter moved {k} place(s) forward"
    elif level == "medium":
        shifts = [r.choice([1, 2, 3])] * len(w) if r.random() < .3 else [1, 2, 3, 4, 5][:len(w)]
        how = "letters shifted by a fixed rule"
    else:
        shifts = [(-1) ** i * (i + 1) for i in range(len(w))]
        how = "alternating forward and backward shifts of 1, 2, 3 and so on"
    if level == "easy" and r.random() < .4:
        rev = w[::-1]
        w2 = r.choice([x for x in WORDS if x != w])
        w3 = r.choice([x for x in WORDS if x not in (w, w2)])
        ans = w3[::-1]
        return make("coding-decoding", f"In a certain code, {w} is written as {rev}. How will {w3} be written in that code?",
                    ans, [shift_word(w3, [1] * len(w3)), w3, w3[1:] + w3[0]],
                    steps(f"{w} → {rev}: the same letters, written in reverse order.", f"So {w3} → {ans}."), s)
    ref = shift_word(w, shifts)
    w2 = r.choice([x for x in WORDS if x != w and len(x) <= len(w)])
    ans = shift_word(w2, shifts[:len(w2)])
    wrong = [shift_word(w2, [-x for x in shifts[:len(w2)]]), shift_word(w2, [sh + 1 for sh in shifts[:len(w2)]]),
             w2[::-1]]
    return make("coding-decoding", f"In a certain code, {w} is written as {ref}. How will {w2} be written in that code?",
                ans, wrong,
                steps(f"Compare {w} with {ref} letter by letter: "
                      + ", ".join(f"{a}→{b} ({'+' if sh >= 0 else '−'}{abs(sh)})" for a, b, sh in zip(w, ref, shifts)) + ".",
                      f"Rule: {how}.",
                      f"Apply it to {w2}: " + ", ".join(f"{a}→{b}" for a, b in zip(w2, ans)) + f", giving {ans}."), s)


# ---------------------------------------------------------------- ranking
def q_ranking(r, s):
    t = r.choice(["total", "bottom", "between"])
    if t == "total":
        a, b = r.randint(5, 25), r.randint(5, 25)
        return num_q("ranking", f"{r.choice(NAMES)} ranks {a}th from the top and {b}th from the bottom in a class. "
                     f"How many students are in the class?", a + b - 1, r, s,
                     steps(f"Counting from the top ({a}) and from the bottom ({b}) counts this student twice.",
                           f"Total = {a} + {b} − 1 = {a + b - 1}."))
    if t == "bottom":
        n, a = r.randint(30, 60), r.randint(5, 25)
        return num_q("ranking", f"In a class of {n} students, {r.choice(NAMES)} ranks {a}th from the top. What is the "
                     f"rank from the bottom?", n - a + 1, r, s,
                     steps(f"{a - 1} students are above, so {n} − {a} = {n - a} are below.",
                           f"Rank from the bottom = {n - a} + 1 = {n - a + 1}."))
    n = r.randint(25, 45)
    a, b = r.randint(5, 12), r.randint(5, 15)
    if a + b >= n:
        return None
    return num_q("ranking", f"In a row of {n} people, P is {a}th from the left and Q is {b}th from the right. How many "
                 f"people are between them?", n - a - b, r, s,
                 steps(f"P and everyone to P's left: {a}. Q and everyone to Q's right: {b}.",
                       f"Since {a} + {b} < {n}, they don't overlap. Between them: {n} − ({a} + {b}) = {n - a - b}."))


# -------------------------------------------------------------- direction
COMPASS = {(0, 1): "North", (1, 1): "North-East", (1, 0): "East", (1, -1): "South-East", (0, -1): "South",
           (-1, -1): "South-West", (-1, 0): "West", (-1, 1): "North-West"}
HEADS = [(0, 1), (1, 0), (0, -1), (-1, 0)]


def sign(x):
    return (x > 0) - (x < 0)


def q_direction(r, s, legs):
    for _ in range(100):
        h = r.randrange(4)
        x = y = 0
        parts = []
        start = ["North", "East", "South", "West"][h]
        parts.append(f"{r.choice(NAMES)} starts walking towards {start}, covers {{d0}} km")
        dists = [r.randint(2, 12) for _ in range(legs)]
        x, y = HEADS[h][0] * dists[0], HEADS[h][1] * dists[0]
        text = f"covers {dists[0]} km"
        story = [f"A person starts walking towards {start} and covers {dists[0]} km"]
        walk = [f"Use east as +x and north as +y. {dists[0]} km {start}: ({x}, {y})."]
        for d in dists[1:]:
            turn = r.choice(["left", "right"])
            h = (h + (1 if turn == "right" else -1)) % 4
            x += HEADS[h][0] * d
            y += HEADS[h][1] * d
            story.append(f"turns {turn} and walks {d} km")
            walk.append(f"Turn {turn} to face {['North', 'East', 'South', 'West'][h]}, {d} km: ({x}, {y}).")
        if x == 0 and y == 0:
            continue
        direction = COMPASS[(sign(x), sign(y))]
        dist = math.hypot(x, y)
        full = ", then ".join(story) + "."
        if (x == 0 or y == 0 or abs(dist - round(dist)) < 1e-9) and r.random() < .6:
            ans = f"{fmt(dist)} km {direction}"
            pool = [f"{fmt(dist)} km {COMPASS[k]}" for k in COMPASS if COMPASS[k] != direction]
            pool += [f"{fmt(dist + d)} km {direction}" for d in (1, 2, -1)]
            r.shuffle(pool)
            wrong = list(dict.fromkeys(w for w in pool if w != ans))[:3]
            return make("direction-sense", f"{full} How far and in which direction is the person from the starting point?",
                        ans, wrong, steps(*walk, f"Net: {abs(x)} km {'east' if x >= 0 else 'west'}, {abs(y)} km {'north' if y >= 0 else 'south'}.",
                                          f"Distance = √({x}² + {y}²) = {fmt(dist)} km, towards the {direction}."
                                          if x and y else f"So {fmt(dist)} km {direction}."), s)
        ans = direction
        wrong = [d for d in ["North", "South", "East", "West", "North-East", "North-West", "South-East",
                              "South-West"] if d != ans]
        r.shuffle(wrong)
        return make("direction-sense", f"{full} In which direction is the person now with respect to the starting point?",
                    ans, wrong[:3], steps(*walk, f"Net: {abs(x)} km {'east' if x >= 0 else 'west'}, {abs(y)} km {'north' if y >= 0 else 'south'}.",
                                          f"So the person is to the {direction} of the start."), s)
    return None


# ---------------------------------------------------------------- seating
def clue_pool(arr, n, r):
    pos = {p: i for i, p in enumerate(arr)}
    cl = []
    for a, b in itertools.permutations(arr, 2):
        pa, pb = pos[a], pos[b]
        if pb == pa + 1:
            cl.append((f"{a} sits immediately to the left of {b}.", lambda p, a=a, b=b: p.index(b) == p.index(a) + 1))
        if pb > pa:
            cl.append((f"{a} sits somewhere to the left of {b}.", lambda p, a=a, b=b: p.index(a) < p.index(b)))
        if abs(pa - pb) == 2:
            cl.append((f"Exactly one person sits between {a} and {b}.",
                       lambda p, a=a, b=b: abs(p.index(a) - p.index(b)) == 2))
        if abs(pa - pb) > 1:
            cl.append((f"{a} and {b} are not neighbours.", lambda p, a=a, b=b: abs(p.index(a) - p.index(b)) > 1))
    for a in arr:
        if pos[a] in (0, n - 1):
            cl.append((f"{a} sits at one of the extreme ends.", lambda p, a=a: p.index(a) in (0, n - 1)))
        k = pos[a]
        if k in (0, n - 1):
            side = "left" if k == 0 else "right"
            cl.append((f"{a} sits at the extreme {side} end.", lambda p, a=a, k=k: p.index(a) == k))
    for a, b, c in itertools.permutations(arr, 3):
        if pos[a] < pos[b] < pos[c]:
            cl.append((f"{b} sits somewhere between {a} and {c}.",
                       lambda p, a=a, b=b, c=c: p.index(a) < p.index(b) < p.index(c)
                       or p.index(c) < p.index(b) < p.index(a)))
    return cl


def q_seating(r, s, n):
    for _ in range(40):
        people = r.sample(NAMES, n)
        arr = people[:]
        r.shuffle(arr)
        pool = clue_pool(arr, n, r)
        r.shuffle(pool)
        perms = list(itertools.permutations(people))
        chosen, alive = [], perms
        for text, pred in pool:
            if text in [c[0] for c in chosen]:
                continue
            nxt = [p for p in alive if pred(p)]
            if len(nxt) < len(alive):
                chosen.append((text, pred))
                alive = nxt
            if len(alive) == 1:
                break
            if len(chosen) > 9:
                break
        if len(alive) != 1 or len(chosen) < 4:
            continue
        sol = alive[0]
        r.shuffle(chosen)
        texts = " ".join(c[0] for c in chosen)
        if any(f"{a} sits at one of the extreme ends." in texts and f"{a} sits at the extreme" in texts for a in people):
            continue
        qk = r.choice(["right", "left", "second", "mid", "right_of"] if n % 2 else ["right", "left", "second", "right_of"])
        if qk == "right":
            qt, ans = "Who sits at the extreme right end?", sol[-1]
        elif qk == "left":
            qt, ans = "Who sits at the extreme left end?", sol[0]
        elif qk == "second":
            qt, ans = "Who sits second from the left end?", sol[1]
        elif qk == "mid":
            qt, ans = "Who sits exactly in the middle?", sol[n // 2]
        else:
            x = r.choice(sol[:-1])
            qt, ans = f"Who sits immediately to the right of {x}?", sol[sol.index(x) + 1]
        if f"{ans} sits at the extreme" in texts and qk in ("right", "left"):
            continue
        wrong = [p for p in people if p != ans]
        r.shuffle(wrong)
        text = (f"{n} friends {', '.join(sorted(people))} sit in a straight row facing north. "
                + " ".join(c[0] for c in chosen) + f"\n{qt}")
        return make("seating-arrangement", text, ans, wrong[:3],
                    steps("Start with the clues that fix a position (ends, 'immediately left of'), then place the rest.",
                          *[f"• {c[0]}" for c in chosen],
                          "Only one order satisfies every clue, left to right: " + ", ".join(sol) + ".",
                          f"{qt} {ans}."), s)
    return None


REASONING = {
    "easy": [("number-series", 35, lambda r, s: q_num_series(r, s, "easy")), ("alphabet", 30, q_alphabet),
             ("coding-decoding", 30, lambda r, s: q_coding(r, s, "easy")), ("ranking", 25, q_ranking),
             ("direction-sense", 30, lambda r, s: q_direction(r, s, r.randint(2, 3))),
             ("syllogism", 25, lambda r, s: q_syllogism(r, s, 3, 2)),
             ("inequality", 25, lambda r, s: q_inequality(r, s, 4))],
    "medium": [("seating-arrangement", 40, lambda r, s: q_seating(r, s, 5)),
               ("syllogism", 35, lambda r, s: q_syllogism(r, s, 3, 3)),
               ("inequality", 30, lambda r, s: q_inequality(r, s, 5)),
               ("direction-sense", 25, lambda r, s: q_direction(r, s, r.randint(3, 4))),
               ("coding-decoding", 25, lambda r, s: q_coding(r, s, "medium")),
               ("number-series", 25, lambda r, s: q_num_series(r, s, "medium")), ("ranking", 20, q_ranking)],
    "hard": [("seating-arrangement", 50, lambda r, s: q_seating(r, s, r.choice([6, 7]))),
             ("syllogism", 40, lambda r, s: q_syllogism(r, s, 4, 3)),
             ("inequality", 35, lambda r, s: q_inequality(r, s, 6)),
             ("direction-sense", 25, lambda r, s: q_direction(r, s, r.randint(4, 6))),
             ("coding-decoding", 20, lambda r, s: q_coding(r, s, "hard")),
             ("number-series", 30, lambda r, s: q_num_series(r, s, "hard"))],
}


# ------------------------------------------------------------------ driver
def build_quant(level, seed, n=QUANT_PER_LEVEL):
    r = random.Random(seed)
    gens = list(QUANT[level])
    out, seen, slot, misses = [], set(), 0, {g: 0 for g in gens}
    while len(out) < n:
        if not gens:
            raise RuntimeError(f"quant {level}: ran out of templates at {len(out)}")
        g = gens[slot % len(gens)]
        q = g(r, slot)
        slot += 1
        if q and q["question"] not in seen:
            seen.add(q["question"])
            out.append(q)
            misses[g] = 0
        else:
            misses[g] += 1
            if misses[g] > 400:
                gens.remove(g)
    return out


def build_reasoning(level, seed, n=QUANT_PER_LEVEL):
    r = random.Random(seed)
    out, seen, slot = [], set(), 0
    plan = REASONING[level]
    scaled = [round(c * n / PER_LEVEL) for _, c, _ in plan]
    scaled[0] += n - sum(scaled)  # absorb rounding so the total is exact
    for (topic, _, gen), count in zip(plan, scaled):
        got, tries = 0, 0
        while got < count:
            tries += 1
            if tries > 4000:
                raise RuntimeError(f"reasoning {level}/{topic}: only {got}/{count}")
            q = gen(r, slot)
            if q and q["question"] not in seen:
                seen.add(q["question"])
                out.append(q)
                got += 1
                slot += 1
    r.shuffle(out)
    return out


def build_bank(fn, level, i):
    """The original slice (seed 1000+i, unchanged), then new questions from seed 2000+i until BANK_SIZE."""
    bank = fn(level, 1000 + i)
    seen = {near_key(q) for q in bank}
    extra = fn(level, 2000 + i, BANK_SIZE + QUANT_PER_LEVEL)
    for q in extra:
        if len(bank) == BANK_SIZE:
            break
        if near_key(q) not in seen:
            seen.add(near_key(q))
            bank.append(q)
    return bank


def near_key(q):
    """Questions that differ only in case, spacing or punctuation count as the same question."""
    return " ".join("".join(c for c in q["question"].lower() if c.isalnum() or c.isspace()).split())


def validate(bank, label, n=PER_LEVEL):
    assert len(bank) == n, (label, len(bank))
    assert len({near_key(q) for q in bank}) == n, (label, "near-duplicate questions")
    for q in bank:
        assert len(q["options"]) in (4, 5) and len(set(q["options"])) == len(q["options"]), (label, q)
        assert 0 <= q["answer"] < len(q["options"]), (label, q)


if __name__ == "__main__":
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    for i, level in enumerate(["easy", "medium", "hard"]):
        for name, fn in (("quant", build_quant), ("reasoning", build_reasoning)):
            bank = build_bank(fn, level, i)
            validate(bank, f"{name}/{level}", BANK_SIZE)
            (out / f"{name}_{level}.json").write_text(json.dumps(bank, indent=1, ensure_ascii=False) + "\n")
            dist = [sum(1 for q in bank if q["answer"] == k) for k in range(5)]
            print(f"{name}_{level}: {len(bank)} questions, answer positions {dist}")
