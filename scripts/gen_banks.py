#!/usr/bin/env python3
"""Generate the Quantitative Aptitude and Reasoning question banks.

Every answer is computed (arithmetic) or proven by brute force (syllogisms,
inequalities, seating), so no answer is typed by hand.

Usage: python3 -I scripts/gen_banks.py <output-dir>
Writes <output-dir>/{quant,reasoning}_{easy,medium,hard}.json, 200 questions each.
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


def make(topic, question, correct, wrong, expl, slot):
    """Place `correct` at index slot%4 among the options."""
    options = [str(w) for w in wrong]
    idx = slot % 4
    options.insert(idx, str(correct))
    if len(set(options)) != 4:
        return None
    return {"topic": topic, "question": question, "options": options,
            "answer": idx, "explanation": expl}


def num_q(topic, text, correct, r, slot, expl, unit_pre="", unit_post=""):
    wrong = numeric_options(correct, r)
    if wrong is None:
        return None
    f = lambda v: f"{unit_pre}{fmt(v)}{unit_post}"
    return make(topic, text, f(correct), [f(w) for w in wrong], expl, slot)


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
    return num_q("percentage", f"What is {p}% of {n}?", a, r, s, f"{p}/100 × {n} = {a}")


def q_si(r, s):
    p, rt, t = r.randrange(1000, 20001, 500), r.randint(4, 12), r.randint(2, 6)
    a = p * rt * t // 100
    return num_q("simple-interest", f"Find the simple interest on ₹{p} at {rt}% per annum for {t} years.",
                 a, r, s, f"SI = P×R×T/100 = {p}×{rt}×{t}/100 = ₹{a}", "₹")


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
                 f"Sum = {sum(nums)}, divided by {n} = {avg}")


def q_ratio_share(r, s):
    a, b = r.randint(1, 9), r.randint(1, 9)
    if a == b:
        return None
    x = (a + b) * r.randint(20, 300)
    share = x * a // (a + b)
    n1, n2 = r.sample(NAMES, 2)
    return num_q("ratio", f"₹{x} is divided between {n1} and {n2} in the ratio "
                 f"{a}:{b}. What is the first person's share?", share, r, s,
                 f"Share = {a}/{a + b} × {x} = ₹{share}", "₹")


def q_profit_pct(r, s):
    cp = r.randrange(200, 2000, 20)
    pr = r.choice([5, 10, 12.5, 15, 20, 25, 30, 40])
    sp = cp * (100 + pr) / 100
    if abs(sp - round(sp)) > 1e-9:
        return None
    return num_q("profit-loss", f"An article bought for ₹{cp} is sold for ₹{int(sp)}. Find the profit percent.",
                 pr, r, s, f"Profit = {int(sp) - cp}; {int(sp) - cp}/{cp} × 100 = {fmt(pr)}%", "", "%")


def q_speed(r, s):
    v, t = r.randrange(20, 100, 5), r.randint(2, 9)
    d = v * t
    if r.random() < .5:
        return num_q("speed-time-distance", f"A car covers {d} km in {t} hours. What is its speed in km/h?",
                     v, r, s, f"Speed = {d}/{t} = {v} km/h")
    return num_q("speed-time-distance", f"How many hours will a car moving at {v} km/h take to cover {d} km?",
                 t, r, s, f"Time = {d}/{v} = {t} hours")


def q_simplify(r, s):
    a, b, c, d = r.randint(10, 60), r.randint(2, 12), r.randint(2, 12), r.randint(5, 50)
    e = r.choice(["{a} + {b} × {c} − {d}", "{a} × {b} − {c} × {d}", "({a} + {b}) × {c} − {d}"])
    val = eval(e.format(a=a, b=b, c=c, d=d).replace("×", "*").replace("−", "-"))
    if val <= 0:
        return None
    return num_q("simplification", f"Simplify: {e.format(a=a, b=b, c=c, d=d)}", val, r, s,
                 "Apply BODMAS: brackets, then multiplication, then addition/subtraction.")


def q_root(r, s):
    if r.random() < .6:
        n = r.randint(12, 45)
        return num_q("roots", f"What is the value of √{n * n}?", n, r, s, f"{n} × {n} = {n * n}")
    n = r.randint(3, 14)
    return num_q("roots", f"What is the value of ∛{n ** 3}?", n, r, s, f"{n}³ = {n ** 3}")


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
    return num_q("number-series", f"Find the missing number: {', '.join(shown)}", ans, r, s,
                 "Look at the difference or ratio between consecutive terms.")


def q_work_simple(r, s):
    a, b, t = r.choice(RATE_PAIRS)
    if a == b:
        return None
    return num_q("time-work", f"A can finish a job in {a} days and B in {b} days. In how many days will they "
                 f"finish it working together?", t, r, s, f"1/{a} + 1/{b} = 1/{fmt(t)}")


def q_discount(r, s):
    mp = r.randrange(200, 3000, 50)
    d = r.choice([5, 10, 15, 20, 25, 30])
    sp = mp * (100 - d) / 100
    if abs(sp - round(sp)) > 1e-9:
        return None
    return num_q("discount", f"The marked price of a bag is ₹{mp}. After a discount of {d}%, what is the selling price?",
                 sp, r, s, f"{mp} × (100 − {d})/100 = ₹{int(sp)}", "₹")


def q_lcm_hcf(r, s):
    a, b = r.randint(6, 60), r.randint(6, 60)
    if a == b:
        return None
    if r.random() < .5:
        return num_q("lcm-hcf", f"Find the LCM of {a} and {b}.", a * b // math.gcd(a, b), r, s,
                     f"LCM = {a}×{b}/HCF, HCF = {math.gcd(a, b)}")
    return num_q("lcm-hcf", f"Find the HCF of {a * 3} and {b * 3}.", math.gcd(a, b) * 3, r, s,
                 "Take the greatest common factor of the two numbers.")


# ----------------------------------------------------------- quant: medium
def q_ci2(r, s):
    p = r.randrange(2000, 30001, 2000)
    rt = r.choice([5, 10, 20])
    ci = round(p * ((1 + rt / 100) ** 2 - 1))
    return num_q("compound-interest", f"Find the compound interest on ₹{p} at {rt}% per annum for 2 years, "
                 f"compounded annually.", ci, r, s, f"A = {p}(1+{rt}/100)², CI = A − P = ₹{ci}", "₹")


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
    return num_q("percentage", txt, ans, r, s, "Net change = x + y + xy/100 using signed values.", "", "%")


def q_profit_disc(r, s):
    x, y = r.choice([20, 25, 30, 40, 50, 60]), r.choice([5, 10, 15, 20, 25])
    p = (100 + x) * (100 - y) / 100 - 100
    if abs(p * 100 - round(p * 100)) > 1e-6 or p <= 0:
        return None
    return num_q("profit-loss", f"A shopkeeper marks goods {x}% above cost price and allows a discount of {y}%. "
                 f"What is his profit percent?", p, r, s,
                 f"Let CP = 100: MP = {100 + x}, SP = {(100 + x) * (100 - y) / 100:g}", "", "%")


def q_alligation(r, s):
    a, b = r.randrange(20, 80, 5), r.randrange(80, 160, 5)
    m, n = r.randint(1, 5), r.randint(1, 5)
    if m == n or (a * m + b * n) % (m + n):
        return None
    mean = (a * m + b * n) // (m + n)
    return num_q("mixture", f"Two varieties of rice costing ₹{a}/kg and ₹{b}/kg are mixed in the ratio {m}:{n}. "
                 f"What is the cost per kg of the mixture?", mean, r, s,
                 f"({a}×{m} + {b}×{n})/{m + n} = ₹{mean}", "₹")


def q_train(r, s):
    v = r.choice([36, 54, 72, 90, 108])
    ms = v * 5 // 18
    t = r.randint(5, 20)
    L = ms * t
    if r.random() < .5:
        return num_q("trains", f"A train {L} m long runs at {v} km/h. In how many seconds does it cross a pole?",
                     t, r, s, f"Speed = {ms} m/s; time = {L}/{ms} = {t} s")
    plat = ms * r.randint(5, 15)
    tt = (L + plat) // ms
    return num_q("trains", f"A train {L} m long, running at {v} km/h, crosses a platform {plat} m long. "
                 f"How many seconds does it take?", tt, r, s, f"Distance = {L + plat} m at {ms} m/s")


def q_boat(r, s):
    b, st = r.randint(6, 20), r.randint(1, 5)
    if st >= b:
        return None
    t = r.randint(2, 6)
    d = (b + st) * t
    return num_q("boats-streams", f"A boat's speed in still water is {b} km/h and the stream flows at {st} km/h. "
                 f"How many hours will it take to go {d} km downstream?", t, r, s,
                 f"Downstream speed = {b + st} km/h, time = {d}/{b + st}")


def q_pipes(r, s):
    for _ in range(50):
        a, b = r.randint(6, 30), r.randint(6, 30)
        c = r.randint(20, 60)
        den = Fraction(1, a) + Fraction(1, b) - Fraction(1, c)
        if den > 0 and (1 / den).denominator == 1 and a != b:
            t = int((1 / den))
            return num_q("pipes-cisterns", f"Pipes A and B can fill a tank in {a} and {b} hours, and an outlet C "
                         f"can empty it in {c} hours. If all three are opened together, in how many hours "
                         f"is the tank filled?", t, r, s, f"Net rate = 1/{a} + 1/{b} − 1/{c} = 1/{t}")
    return None


def q_ages(r, s):
    if r.random() < .5:
        k = r.randint(2, 5)
        son = r.randint(8, 30)
        tot = son * (k + 1)
        return num_q("ages", f"The sum of a father's and his son's ages is {tot} years. The father is {k} times as "
                     f"old as the son. What is the son's age?", son, r, s, f"Son = {tot}/{k + 1} = {son}")
    f_, so = r.randint(35, 60), r.randint(8, 25)
    t = f_ - 2 * so
    if t <= 0:
        return None
    return num_q("ages", f"A father is {f_} years old and his son is {so}. After how many years will the father be "
                 f"twice as old as the son?", t, r, s, f"{f_} + x = 2({so} + x) gives x = {t}")


def q_partner(r, s):
    x, y = r.randrange(2000, 10001, 1000), r.randrange(2000, 10001, 1000)
    m = r.choice([6, 8, 9])
    profit = r.randrange(1000, 10001, 100)
    ra, rb = x * 12, y * m
    if (profit * ra) % (ra + rb):
        return None
    ans = profit * ra // (ra + rb)
    return num_q("partnership", f"A invests ₹{x} for 12 months and B invests ₹{y} for {m} months. Their total profit "
                 f"is ₹{profit}. What is A's share?", ans, r, s, f"Ratio of capital×time = {ra}:{rb}", "₹")


def q_lcm_word(r, s):
    nums = sorted(r.sample([6, 8, 9, 10, 12, 15, 18, 20, 24, 30], 3))
    l = math.lcm(*nums)
    return num_q("lcm-hcf", f"What is the smallest number divisible by {nums[0]}, {nums[1]} and {nums[2]}?",
                 l, r, s, f"LCM of {nums} = {l}")


def q_avg_replace(r, s):
    n, old = r.randint(5, 10), r.randint(20, 40)
    avg = r.randint(20, 40)
    new = old + n * r.randint(2, 6)
    return num_q("average", f"The average of {n} numbers is {avg}. If one number, {old}, is replaced by {new}, "
                 f"what is the new average?", avg + (new - old) / n, r, s,
                 f"Change in sum = {new - old}, so average changes by {(new - old)}/{n}")


def q_population(r, s):
    p = r.randrange(10000, 60001, 5000)
    a, b = r.choice([10, 20, 25]), r.choice([10, 20, 50])
    ans = p * (100 + a) * (100 + b) // 10000
    if (p * (100 + a) * (100 + b)) % 10000:
        return None
    return num_q("percentage", f"The population of a town is {p}. It grows by {a}% in the first year and by {b}% in "
                 f"the second year. What is the population after two years?", ans, r, s,
                 f"{p} × {100 + a}/100 × {100 + b}/100 = {ans}")


def q_si_multiple(r, s):
    n = r.randint(4, 12)
    k = r.choice([3, 4, 5])
    return num_q("simple-interest", f"A sum becomes double in {n} years at simple interest. In how many years will "
                 f"it become {k} times itself?", (k - 1) * n, r, s,
                 f"Interest of one P takes {n} years, so {k - 1}P takes {(k - 1) * n} years")


# ------------------------------------------------------------- quant: hard
def q_ci_si_diff(r, s):
    rt = r.choice([5, 8, 10, 12, 15, 20])
    p = r.randrange(5000, 60001, 2500)
    d = p * rt * rt / 10000
    if abs(d - round(d)) > 1e-9:
        return None
    return num_q("compound-interest", f"What is the difference between the compound interest and simple interest "
                 f"on ₹{p} for 2 years at {rt}% per annum?", d, r, s, f"Difference = P(R/100)² = ₹{int(d)}", "₹")


def q_work3(r, s):
    cands = [10, 12, 15, 20, 24, 30, 40, 60]
    for _ in range(80):
        a, b, c = (r.choice(cands) for _ in range(3))
        pairs = [Fraction(1, a) + Fraction(1, b), Fraction(1, b) + Fraction(1, c), Fraction(1, a) + Fraction(1, c)]
        if all((1 / x).denominator == 1 for x in pairs) and len({a, b, c}) == 3:
            ab, bc, ac = (int(1 / x) for x in pairs)
            return num_q("time-work", f"A and B together can do a job in {ab} days, B and C in {bc} days, and A and C "
                         f"in {ac} days. In how many days can A alone do it?", a, r, s,
                         f"Add the three rates, halve, then subtract B and C's combined rate.")
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
                         int(days), r, s, f"Remaining = 1 − {t}(1/{a}+1/{b}); B needs remaining × {b} days")
    return None


def q_replace(r, s):
    for _ in range(80):
        L = r.choice([40, 50, 60, 80, 100, 120, 150, 200])
        x = r.choice([5, 8, 10, 15, 20, 25, 30])
        left = Fraction(L) * (1 - Fraction(x, L)) ** 2
        if x < L and left.denominator == 1:
            return num_q("mixture", f"A vessel holds {L} litres of pure milk. {x} litres are removed and replaced "
                         f"with water, and the process is repeated once more. How many litres of milk remain?",
                         int(left), r, s, f"Milk left = {L}(1 − {x}/{L})²")
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
                         f"Upstream {up}, downstream {down}; still water = (up+down)/2")
        return num_q("boats-streams", f"A boat takes {t_up} hours to go {d} km upstream and {t_down} hours to "
                     f"return downstream. Find the speed of the stream (km/h).", st, r, s,
                     f"Stream = (downstream − upstream)/2 = {st}")
    return None


def q_prob_dice(r, s):
    k = r.randint(3, 11)
    cnt = sum(1 for a in range(1, 7) for b in range(1, 7) if a + b == k)
    ans = Fraction(cnt, 36)
    pool = {Fraction(i, 36) for i in range(1, 12)} - {ans}
    wrong = r.sample(sorted(pool), 3)
    return make("probability", f"Two fair dice are thrown. What is the probability that the sum is {k}?",
                fmt(ans), [fmt(w) for w in wrong], f"{cnt} favourable outcomes out of 36", r.randrange(4))


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
                f"Favourable ways / total ways = Σ c(c−1) / {n}×{n - 1}", r.randrange(4))


def q_perm(r, s):
    if r.random() < .5:
        word = r.choice(["LEADER", "BANANA", "LETTER", "COMMITTEE", "BALLOON", "SUCCESS", "PEPPER", "APPLE",
                         "GARDEN", "MISSISSIPPI", "STATISTICS", "ARRANGE"])
        cnt = math.factorial(len(word))
        for ch in set(word):
            cnt //= math.factorial(word.count(ch))
        return num_q("permutation-combination", f"In how many distinct ways can the letters of the word "
                     f"{word} be arranged?", cnt, r, s, "n! divided by the factorial of each repeated letter count")
    m, w = r.randint(4, 8), r.randint(3, 6)
    k = r.randint(3, min(5, m + w - 1))
    ans = sum(math.comb(m, i) * math.comb(w, k - i) for i in range(1, min(m, k) + 1) if k - i <= w)
    return num_q("permutation-combination", f"A committee of {k} is chosen from {m} men and {w} women. In how many "
                 f"ways can it be formed with at least one man?", ans, r, s,
                 f"Total C({m + w},{k}) minus all-women C({w},{k})")


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
                    ans, wrong[:3], f"Roots of I: {x[0]}, {x[1]}; roots of II: {y[0]}, {y[1]}", r.randrange(4))
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
    return num_q("number-series", f"Find the missing number: {', '.join(shown)}", ans, r, s,
                 "Study the pattern in differences, multipliers or alternating terms.")


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
                         f"Relative speed = {rel} km/h; distance = {l1 + l2} m")
    return None


def q_avg_group(r, s):
    n, a = r.randint(4, 9), r.randint(20, 40)
    inc = r.randint(1, 3)
    ans = (n + 1) * (a + inc) - n * a
    return num_q("average", f"The average age of {n} people is {a} years. When a new person joins, the average rises "
                 f"to {a + inc}. How old is the new person?", ans, r, s,
                 f"{n + 1}×{a + inc} − {n}×{a} = {ans}")


def q_pct_less(r, s):
    x = r.choice([25, 50, 100, 150, 300, 400])
    ans = 100 * x / (100 + x)
    return num_q("percentage", f"A's salary is {x}% more than B's. B's salary is what percent less than A's?", ans,
                 r, s, f"100x/(100+x) with x = {x}", "", "%")


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
        return five_opt("syllogism", text, ans, "Draw all possible Venn diagrams; a conclusion follows only if it holds in every one.")
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
        return five_opt("inequality", text, ans, "Combine the statements into one chain; a conclusion follows only if it is always true.")
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
                    f"Count {n} letters from A.", s)
    if t == "right":
        a = r.randint(0, 15)
        k = r.randint(3, 9)
        return make("alphabet", f"Which letter is {k}th to the right of {ALPHA[a]} in the English alphabet?",
                    ALPHA[a + k], r.sample([c for c in ALPHA if c != ALPHA[a + k]], 3), f"{ALPHA[a]} + {k} places", s)
    if t == "between":
        a = r.randint(0, 12)
        b = a + r.randint(4, 12)
        ans = b - a - 1
        return num_q("alphabet", f"How many letters are there between {ALPHA[a]} and {ALPHA[b]} in the English "
                     f"alphabet?", ans, r, s, f"Positions {a + 1} and {b + 1}: {b + 1} − {a + 1} − 1")
    w = r.choice(WORDS)
    val = sum(ALPHA.index(c) + 1 for c in w)
    return num_q("alphabet", f"If A=1, B=2, ..., Z=26, what is the sum of the letter values of the word {w}?", val,
                 r, s, "Add the position of each letter.")


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
        how = "alternating forward and backward shifts of 1, 2, 3, ..."
    if level == "easy" and r.random() < .4:
        rev = w[::-1]
        w2 = r.choice([x for x in WORDS if x != w])
        w3 = r.choice([x for x in WORDS if x not in (w, w2)])
        ans = w3[::-1]
        return make("coding-decoding", f"In a certain code, {w} is written as {rev}. How will {w3} be written in that code?",
                    ans, [shift_word(w3, [1] * len(w3)), w3, w3[1:] + w3[0]], "The letters are written in reverse order.", s)
    ref = shift_word(w, shifts)
    w2 = r.choice([x for x in WORDS if x != w and len(x) <= len(w)])
    ans = shift_word(w2, shifts[:len(w2)])
    wrong = [shift_word(w2, [-x for x in shifts[:len(w2)]]), shift_word(w2, [sh + 1 for sh in shifts[:len(w2)]]),
             w2[::-1]]
    return make("coding-decoding", f"In a certain code, {w} is written as {ref}. How will {w2} be written in that code?",
                ans, wrong, f"Rule: {how}.", s)


# ---------------------------------------------------------------- ranking
def q_ranking(r, s):
    t = r.choice(["total", "bottom", "between"])
    if t == "total":
        a, b = r.randint(5, 25), r.randint(5, 25)
        return num_q("ranking", f"{r.choice(NAMES)} ranks {a}th from the top and {b}th from the bottom in a class. "
                     f"How many students are in the class?", a + b - 1, r, s, f"{a} + {b} − 1")
    if t == "bottom":
        n, a = r.randint(30, 60), r.randint(5, 25)
        return num_q("ranking", f"In a class of {n} students, {r.choice(NAMES)} ranks {a}th from the top. What is the "
                     f"rank from the bottom?", n - a + 1, r, s, f"{n} − {a} + 1")
    n = r.randint(25, 45)
    a, b = r.randint(5, 12), r.randint(5, 15)
    if a + b >= n:
        return None
    return num_q("ranking", f"In a row of {n} people, P is {a}th from the left and Q is {b}th from the right. How many "
                 f"people are between them?", n - a - b, r, s, f"{n} − ({a} + {b})")


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
        for d in dists[1:]:
            turn = r.choice(["left", "right"])
            h = (h + (1 if turn == "right" else -1)) % 4
            x += HEADS[h][0] * d
            y += HEADS[h][1] * d
            story.append(f"turns {turn} and walks {d} km")
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
                        ans, wrong, f"Net displacement: {x} east, {y} north.", s)
        ans = direction
        wrong = [d for d in ["North", "South", "East", "West", "North-East", "North-West", "South-East",
                              "South-West"] if d != ans]
        r.shuffle(wrong)
        return make("direction-sense", f"{full} In which direction is the person now with respect to the starting point?",
                    ans, wrong[:3], f"Net displacement: {x} east, {y} north.", s)
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
                    "Order from left to right: " + ", ".join(sol) + ".", s)
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
def build_quant(level, seed):
    r = random.Random(seed)
    gens = list(QUANT[level])
    out, seen, slot, misses = [], set(), 0, {g: 0 for g in gens}
    while len(out) < QUANT_PER_LEVEL:
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


def build_reasoning(level, seed):
    r = random.Random(seed)
    out, seen, slot = [], set(), 0
    plan = REASONING[level]
    scaled = [round(c * QUANT_PER_LEVEL / PER_LEVEL) for _, c, _ in plan]
    scaled[0] += QUANT_PER_LEVEL - sum(scaled)  # absorb rounding so the total is exact
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


def validate(bank, label, n=PER_LEVEL):
    assert len(bank) == n, (label, len(bank))
    for q in bank:
        assert len(q["options"]) in (4, 5) and len(set(q["options"])) == len(q["options"]), (label, q)
        assert 0 <= q["answer"] < len(q["options"]), (label, q)


if __name__ == "__main__":
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    for i, level in enumerate(["easy", "medium", "hard"]):
        for name, fn in (("quant", build_quant), ("reasoning", build_reasoning)):
            bank = fn(level, 1000 + i)
            validate(bank, f"{name}/{level}", QUANT_PER_LEVEL)
            (out / f"{name}_{level}.json").write_text(json.dumps(bank, indent=1, ensure_ascii=False) + "\n")
            dist = [sum(1 for q in bank if q["answer"] == k) for k in range(5)]
            print(f"{name}_{level}: {len(bank)} questions, answer positions {dist}")
