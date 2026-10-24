#!/usr/bin/env python3
"""Regenerate the architecture diagrams and tables in docs/ARCHITECTURE.md and README.md from the code,
so they can't drift.

Usage: python3 -I scripts/build_architecture.py            rewrite the generated blocks
       python3 -I scripts/build_architecture.py --check    exit 1 if a doc is out of date (CI)

The pre-commit hook (.githooks/pre-commit) runs it on every commit that touches the code it reads.

Reads:
  src/main/resources/db/migration/V*.sql   database schema (Flyway migrations replayed in order)
  src/main/java/com/quizverse/**.java      controllers, endpoints, repositories, entities, services
  config/SecurityConfig.java               who may call each endpoint
  src/main/resources/static/*.html, js/    pages and the scripts they load
  src/main/resources/static/data/index.json  question content
  src/main/resources/application*.properties  environment variables
  .github/workflows/*.yml                  build, test and deploy pipeline

Only the text between "<!-- BEGIN GENERATED: name -->" and "<!-- END GENERATED: name -->" is replaced;
everything else in the docs is hand-written and kept. The migration parser understands the statements this
repo uses (CREATE TABLE, CREATE INDEX, ALTER TABLE ... ADD COLUMN / ADD CONSTRAINT / ALTER COLUMN); add to
it when a migration uses something new, or the script stops with an error.
"""
import json
import re
import sys
from collections import OrderedDict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
# Every doc may hold any of the generated blocks; each block must appear in at least one of them.
DOCS = [ROOT / "docs" / "ARCHITECTURE.md", ROOT / "README.md"]
MIGRATIONS = ROOT / "src/main/resources/db/migration"
JAVA = ROOT / "src/main/java/com/quizverse"
STATIC = ROOT / "src/main/resources/static"


# ---------- Database schema ----------

def short_type(sql_type):
    t = sql_type.upper()
    for prefix, name in (("VARCHAR", "varchar"), ("BIGINT", "bigint"), ("INTEGER", "int"), ("BOOLEAN", "boolean"),
                         ("DOUBLE", "double"), ("TIMESTAMP", "timestamptz")):
        if t.startswith(prefix):
            return name
    sys.exit(f"build_architecture: unknown column type {sql_type!r}; add it to short_type()")


def split_top_level(body):
    """Split a CREATE TABLE body on commas that aren't inside parentheses."""
    parts, depth, buf = [], 0, []
    for ch in body:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append("".join(buf).strip())
            buf = []
        else:
            buf.append(ch)
    if "".join(buf).strip():
        parts.append("".join(buf).strip())
    return parts


COLUMN = re.compile(r"(\w+)\s+([A-Z]+(?:\s+PRECISION)?(?:\(\d+\))?(?:\s+WITH TIME ZONE)?)(.*)", re.I)
REFERENCES = re.compile(r"REFERENCES\s+(\w+)\s*\((\w+)\)(?:\s+ON DELETE\s+(CASCADE|SET NULL))?", re.I)


def add_column(table, definition):
    m = COLUMN.match(definition.strip())
    if not m:
        sys.exit(f"build_architecture: can't parse column {definition!r}")
    name, sql_type, rest = m.group(1).lower(), m.group(2), m.group(3)
    col = {"type": short_type(sql_type), "length": None, "pk": "PRIMARY KEY" in rest.upper(),
           "not_null": "NOT NULL" in rest.upper() or "PRIMARY KEY" in rest.upper(), "unique": False, "fk": None}
    length = re.search(r"\((\d+)\)", sql_type)
    if col["type"] == "varchar" and length:
        col["length"] = int(length.group(1))
    ref = REFERENCES.search(rest)
    if ref:
        col["fk"] = (ref.group(1).lower(), (ref.group(3) or "").lower())
    table["columns"][name] = col


def add_constraint(table, definition):
    d = definition.strip()
    unique = re.match(r"CONSTRAINT\s+\w+\s+UNIQUE\s*\(([^)]*)\)", d, re.I)
    fk = re.match(r"CONSTRAINT\s+\w+\s+FOREIGN KEY\s*\((\w+)\)\s+(REFERENCES.*)", d, re.I)
    if unique:
        cols = [c.strip().lower() for c in unique.group(1).split(",")]
        if len(cols) == 1:
            table["columns"][cols[0]]["unique"] = True
        else:
            table["uniques"].append(cols)
    elif fk:
        ref = REFERENCES.search(fk.group(2))
        table["columns"][fk.group(1).lower()]["fk"] = (ref.group(1).lower(), (ref.group(3) or "").lower())
    else:
        sys.exit(f"build_architecture: can't parse constraint {definition!r}")


def statements(sql):
    sql = re.sub(r"--[^\n]*", "", sql)
    return [s.strip() for s in sql.split(";") if s.strip()]


def schema():
    tables = OrderedDict()
    files = sorted(MIGRATIONS.glob("V*.sql"), key=lambda p: int(re.match(r"V(\d+)", p.name).group(1)))
    for path in files:
        for stmt in statements(path.read_text()):
            flat = " ".join(stmt.split())
            create = re.match(r"CREATE TABLE (\w+)\s*\((.*)\)$", flat, re.I)
            index = re.match(r"CREATE (UNIQUE )?INDEX (\w+) ON (\w+)\s*\(([^)]*)\)", flat, re.I)
            alter = re.match(r"ALTER TABLE (\w+) (.*)$", flat, re.I)
            if create:
                table = tables[create.group(1).lower()] = {"columns": OrderedDict(), "uniques": [], "indexes": []}
                for part in split_top_level(create.group(2)):
                    (add_constraint if part.upper().startswith("CONSTRAINT") else add_column)(table, part)
            elif index:
                tables[index.group(3).lower()]["indexes"].append(
                    (index.group(2).lower(), [c.strip().lower() for c in index.group(4).split(",")]))
            elif alter:
                table, action = tables[alter.group(1).lower()], alter.group(2)
                add = re.match(r"ADD COLUMN (.*)$", action, re.I)
                drop_nn = re.match(r"ALTER COLUMN (\w+) DROP NOT NULL$", action, re.I)
                if add:
                    add_column(table, add.group(1))
                elif action.upper().startswith("ADD CONSTRAINT"):
                    add_constraint(table, action[len("ADD "):])
                elif drop_nn:
                    table["columns"][drop_nn.group(1).lower()]["not_null"] = False
                else:
                    sys.exit(f"build_architecture: unsupported ALTER in {path.name}: {flat}")
            else:
                sys.exit(f"build_architecture: unsupported statement in {path.name}: {flat[:80]}")
    return tables, [p.name for p in files]


def er_diagram(tables):
    lines = ["```mermaid", "erDiagram"]
    for name, t in tables.items():
        for col_name, c in t["columns"].items():
            if c["fk"]:
                parent, on_delete = c["fk"]
                left = "||" if c["not_null"] else "|o"
                label = col_name + (f", on delete {on_delete}" if on_delete else "")
                right = "o|" if c["unique"] else "o{"
                lines.append(f'    {parent} {left}--{right} {name} : "{label}"')
    for name, t in tables.items():
        lines.append(f"    {name} {{")
        for col_name, c in t["columns"].items():
            keys = [k for k, on in (("PK", c["pk"]), ("FK", c["fk"]), ("UK", c["unique"])) if on]
            notes = []
            if c["length"]:
                notes.append(str(c["length"]))
            notes.append("not null" if c["not_null"] else "nullable")
            lines.append(f'        {c["type"]} {col_name}{" " + ",".join(keys) if keys else ""} "{", ".join(notes)}"')
        lines.append("    }")
    lines.append("```")
    return "\n".join(lines)


def schema_notes(tables, files):
    rows = ["| Table | Unique together | Indexes |", "|---|---|---|"]
    for name, t in tables.items():
        uniques = "; ".join("(" + ", ".join(u) + ")" for u in t["uniques"]) or "-"
        indexes = "; ".join(f"`{i}` (" + ", ".join(cols) + ")" for i, cols in t["indexes"]) or "-"
        rows.append(f"| `{name}` | {uniques} | {indexes} |")
    return (f"Built from {len(files)} migrations ({files[0]} … {files[-1]}).\n\n" + "\n".join(rows))


# ---------- Backend: controllers, endpoints, access rules ----------

def java_files(folder):
    return sorted((JAVA / folder).glob("*.java"))


def class_name(path):
    return path.stem


def security_rules():
    """[(http method or None, [ant patterns], access)] in the order SecurityConfig checks them."""
    text = (JAVA / "config/SecurityConfig.java").read_text()
    rules = []
    for m in re.finditer(r"\.requestMatchers\(([^)]*)\)\.(hasRole\(\"(\w+)\"\)|authenticated\(\)|permitAll\(\))", text):
        args = m.group(1)
        method = re.search(r"HttpMethod\.(\w+)", args)
        patterns = re.findall(r"\"([^\"]+)\"", args)
        access = f"role {m.group(3)}" if m.group(3) else "login" if "authenticated" in m.group(2) else "public"
        rules.append((method.group(1) if method else None, patterns, access))
    any_request = re.search(r"\.anyRequest\(\)\.(permitAll|authenticated)\(\)", text)
    rules.append((None, ["/**"], "public" if any_request and any_request.group(1) == "permitAll" else "login"))
    return rules


def ant_match(pattern, path):
    path = re.sub(r"\{[^}]+\}", "x", path)
    regex = re.escape(pattern).replace(r"/\*\*", "(/.*)?").replace(r"\*\*", ".*").replace(r"\*", "[^/]*")
    return re.fullmatch(regex, path) is not None


def access_for(method, path, rules):
    for rule_method, patterns, access in rules:
        if (rule_method is None or rule_method == method) and any(ant_match(p, path) for p in patterns):
            return access
    return "public"


MAPPING = re.compile(r"@(Get|Post|Put|Delete|Patch)Mapping(?:\((?:value\s*=\s*)?\"([^\"]*)\"\))?")


def endpoints():
    rules = security_rules()
    out = []
    for path in java_files("controller"):
        text = path.read_text()
        if not re.search(r"@RestController\b", text):
            continue
        base = re.search(r"@RequestMapping\(\"([^\"]*)\"\)\s*public class", text)
        base = base.group(1) if base else ""
        for m in MAPPING.finditer(text):
            method, sub = m.group(1).upper(), m.group(2) or ""
            full = base + sub
            # The javadoc right above the mapping (skipping other annotations), first sentence only.
            before = text[:m.start()]
            start, end = before.rfind("/**"), before.rfind("*/")
            between = before[end + 2:] if end > start >= 0 else None
            summary = ""
            if between is not None and re.fullmatch(r"\s*(?:@\w+(?:\([^)]*\))?\s*)*", between):
                summary = " ".join(l.strip().lstrip("*").strip() for l in before[start + 3:end].splitlines()).strip()
                summary = re.sub(r"\{@code ([^}]*)\}", r"`\1`", summary)
                summary = re.sub(r"<[^>]+>", "", summary)
                summary = re.split(r"(?<!e\.g\.)(?<!i\.e\.)(?<=[.!?])\s", summary)[0].replace("|", "\\|")
            out.append((full, method, access_for(method, full, rules), class_name(path), summary))
    return sorted(out, key=lambda e: (e[0], ["GET", "POST", "PUT", "PATCH", "DELETE"].index(e[1])))


def endpoint_table():
    rows = ["| Method | Path | Access | Controller | What it does |", "|---|---|---|---|---|"]
    for path, method, access, controller, summary in endpoints():
        rows.append(f"| {method} | `{path}` | {access} | {controller} | {summary} |")
    return "\n".join(rows)


def constructor_deps(path):
    text = path.read_text()
    m = re.search(rf"public {path.stem}\(([^)]*)\)", text, re.S)
    if not m:
        return []
    return [p.split()[-2] for p in (x.strip() for x in m.group(1).split(",")) if len(p.split()) >= 2]


def entity_tables():
    out = {}
    for path in java_files("model"):
        m = re.search(r"@Table\(name = \"(\w+)\"", path.read_text())
        if m:
            out[path.stem] = m.group(1)
    return out


def repo_entity(path):
    m = re.search(r"JpaRepository<(\w+),", path.read_text())
    return m.group(1) if m else None


def component_diagram():
    controllers = [p for p in java_files("controller") if re.search(r"@RestController\b", p.read_text())]
    repos = {p.stem: repo_entity(p) for p in java_files("repository")}
    services = {p.stem for d in ("security", "service", "config") for p in java_files(d)} - {"SecurityConfig"}
    entities = entity_tables()

    lines = ["```mermaid", "flowchart LR",
             '    browser(["Browser: static pages + JS modules"])',
             '    filter["Spring Security filter chain<br/>(session cookie, CSRF, access rules)"]',
             "    browser -->|JSON /api/*| filter"]
    lines.append('    subgraph controllers["Controllers"]')
    lines += [f"        {p.stem}" for p in controllers]
    lines.append("    end")
    lines.append('    subgraph services["Security and services"]')
    used_services = sorted({d for p in controllers for d in constructor_deps(p) if d in services}
                           | {d for p in java_files("security") for d in constructor_deps(p) if d in services}
                           | {s for s in services if s in ("SessionTracker", "Streaks")})
    lines += [f"        {s}" for s in used_services]
    lines.append("    end")
    lines.append('    subgraph repositories["Spring Data repositories"]')
    lines += [f"        {r}" for r in sorted(repos)]
    lines.append("    end")
    lines.append('    db[("Database<br/>H2 locally, PostgreSQL in prod")]')
    lines.append("    filter --> controllers")
    for p in controllers:
        for dep in constructor_deps(p):
            if dep in repos or dep in services:
                lines.append(f"    {p.stem} --> {dep}")
        if "Streaks." in p.read_text():
            lines.append(f"    {p.stem} --> Streaks")
    for p in java_files("security"):
        for dep in constructor_deps(p):
            if dep in repos or dep in services:
                lines.append(f"    {p.stem} --> {dep}")
    for repo, entity in sorted(repos.items()):
        if entity in entities:
            lines.append(f'    {repo} -->|{entities[entity]}| db')
    if "GoogleTokenVerifier" in used_services:
        lines.append('    google(["Google JWKS keys"])')
        lines.append("    GoogleTokenVerifier -.->|verify ID token| google")
    lines.append("```")
    return "\n".join(lines)


# ---------- Frontend: pages and modules ----------

def frontend_diagram():
    pages = sorted(p for p in STATIC.glob("*.html"))
    modules = sorted((STATIC / "js").glob("*.js"))
    lines = ["```mermaid", "flowchart LR"]
    lines.append('    subgraph pages["Pages"]')
    lines += [f'        page_{p.stem}["{p.name}"]' for p in pages]
    lines.append("    end")
    lines.append('    subgraph modules["JS modules"]')
    lines += [f'        js_{m.stem}["{m.name}"]' for m in modules if m.stem != "theme"]
    lines.append("    end")
    lines.append('    data[("static/data/*.json<br/>question bank")]')
    lines.append('    apiserver(["/api/* (Spring Boot)"])')
    for p in pages:
        # theme.js is on every page; leaving it out keeps the chart readable.
        for src in re.findall(r"<script[^>]*src=\"/?js/(\w+)\.js\"", p.read_text()):
            if src != "theme":
                lines.append(f"    page_{p.stem} --> js_{src}")
    for m in modules:
        text = m.read_text()
        for dep in sorted(set(re.findall(r"from \"\./(\w+)\.js\"", text))):
            lines.append(f"    js_{m.stem} --> js_{dep}")
        if re.search(r"fetch(?:Json)?\(\s*[`\"]data/", text):
            lines.append(f"    js_{m.stem} -.-> data")
        if re.search(r"fetch\(\s*path\b|fetch\(path,", text) and m.stem == "api":
            lines.append(f"    js_{m.stem} -.-> apiserver")
    lines.append("```")
    return "\n".join(lines)


def content_table():
    index = json.loads((STATIC / "data/index.json").read_text())
    rows = ["| Exam / track | Stage | Sections | Quizzes | Questions |", "|---|---|---|---|---|"]
    for exam in index["exams"]:
        for stage in exam["stages"]:
            quizzes = questions = 0
            for section in stage["sections"]:
                for level in section["levels"]:
                    for quiz in level["quizzes"]:
                        quizzes += 1
                        questions += len(json.loads((STATIC / "data" / quiz["file"]).read_text()))
            names = ", ".join(s["name"] for s in stage["sections"])
            rows.append(f"| {exam['name']} | {stage['name']} | {names} | {quizzes} | {questions} |")
    topics = json.loads((STATIC / "data/topics.json").read_text())
    unique = sum(sum(t["levels"].values()) for s in topics["subjects"] for t in s["topics"])
    return "\n".join(rows) + (f"\n\nQuiz slots can reuse a question (the same bank feeds several exams): "
                              f"{unique} unique questions in {len(topics['subjects'])} subjects.")


# ---------- JPA entities (the Java side of the schema) ----------

FIELD = re.compile(r"private\s+([\w<>, ]+?)\s+(\w+)\s*(?:=[^;]*)?;")
RELATION = re.compile(r"@(ManyToOne|OneToOne|OneToMany)\b([^\n]*)\n(?:\s*@[^\n]*\n)*\s*private\s+(\w+)\s+(\w+);")


def entity_diagram():
    tables = entity_tables()
    lines = ["```mermaid", "classDiagram", "    direction LR"]
    relations = []
    for path in java_files("model"):
        if path.stem not in tables:
            continue
        text = path.read_text()
        # field -> (annotation, target class, required)
        related = {m.group(4): (m.group(1), m.group(3), "optional = false" in m.group(2)) for m in RELATION.finditer(text)}
        lines.append(f"    class {path.stem} {{")
        lines.append(f"        <<table {tables[path.stem]}>>")
        for m in FIELD.finditer(text):
            type_, name = m.group(1).strip(), m.group(2)
            if name not in related:
                lines.append(f"        {type_.replace('<', '~').replace('>', '~')} {name}")
        lines.append("    }")
        for field, (kind, target, required) in related.items():
            one = "1" if required else "0..1"
            arrow = {"ManyToOne": f'"*" --> "{one}"', "OneToOne": f'"1" --> "{one}"', "OneToMany": '"1" --> "*"'}[kind]
            relations.append(f"    {path.stem} {arrow} {target} : {field}")
    lines += relations
    lines.append("```")
    return "\n".join(lines)


# ---------- Configuration ----------

def config_table():
    """Every ${ENV_VAR} the properties files read, with its default and where it's used."""
    rows = ["| Variable | Default | Used by | Profile |", "|---|---|---|---|"]
    seen = {}
    # application.properties first: it applies to every profile.
    for path in sorted((ROOT / "src/main/resources").glob("application*.properties"), key=lambda p: (p.stem != "application", p.name)):
        profile = path.stem.partition("-")[2] or "all"
        for line in path.read_text().splitlines():
            if line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            for var, default in re.findall(r"\$\{([A-Z0-9_]+)(?::([^}]*))?\}", value):
                entry = seen.setdefault(var, {"default": default, "keys": [], "profiles": []})
                if key.strip() not in entry["keys"]:
                    entry["keys"].append(key.strip())
                if profile not in entry["profiles"]:
                    entry["profiles"].append(profile)
    for var, e in seen.items():
        default = f"`{e['default']}`" if e["default"] else "(none)"
        rows.append(f"| `{var}` | {default} | {', '.join(f'`{k}`' for k in e['keys'])} | {', '.join(e['profiles'])} |")
    return "\n".join(rows)


# ---------- Build, CI and deploy ----------

def pipeline_diagram():
    """From the GitHub workflows: what triggers each job and the steps it runs."""
    lines = ["```mermaid", "flowchart LR", '    dev(["git push / pull request"])']
    for path in sorted((ROOT / ".github/workflows").glob("*.yml")):
        text = path.read_text()
        wf = path.stem.replace("-", "_")
        name = re.search(r"^name:\s*(.+)$", text, re.M).group(1).strip()
        triggers = []
        if re.search(r"^  push:", text, re.M):
            triggers.append("push")
        if re.search(r"^  pull_request:", text, re.M):
            triggers.append("pull request")
        if "workflow_dispatch" in text:
            triggers.append("manual run")
        branches = re.search(r"branches:\s*\[([^\]]*)\]", text)
        label = " / ".join(triggers) + (f" to {branches.group(1).strip().strip(chr(34))}" if branches else "")
        lines.append(f'    subgraph {wf}["{name} ({path.name})"]')
        jobs = re.split(r"^  (\w+):\s*$", text.split("\njobs:", 1)[1], flags=re.M)[1:]
        previous_job = None
        for job, body in zip(jobs[::2], jobs[1::2]):
            steps = [st.strip() for st in re.findall(r"^\s*- name:\s*(.+)$", body, re.M)]
            needs = re.search(r"^\s*needs:\s*(\w+)", body, re.M)
            cond = re.search(r"^\s*if:\s*(.+)$", body, re.M)
            node = f"{wf}_{job}"
            lines.append(f'        {node}["<b>{job}</b><br/>' + "<br/>".join(f"• {st}" for st in steps) + '"]')
            if needs:
                edge = "main only" if cond and "refs/heads/main" in cond.group(1) else ""
                lines.append(f"        {wf}_{needs.group(1)} -->{'|' + edge + '|' if edge else ''} {node}")
            elif previous_job is None:
                lines.append(f"    dev -->|{label}| {node}")
            previous_job = node
        lines.append("    end")
        if "railway up" in text:
            deploy_job = [j for j, b in zip(jobs[::2], jobs[1::2]) if "railway up" in b][0]
            lines.append(f"    {wf}_{deploy_job} --> railway")
    lines.append('    railway[("Railway<br/>Dockerfile build → container")]')
    lines.append("```")
    return "\n".join(lines)


# ---------- Writing the docs ----------

def blocks():
    tables, files = schema()
    return {
        "components": component_diagram(),
        "frontend": frontend_diagram(),
        "schema": er_diagram(tables),
        "entities": entity_diagram(),
        "config": config_table(),
        "pipeline": pipeline_diagram(),
        "schema-notes": schema_notes(tables, files),
        "endpoints": endpoint_table(),
        "content": content_table(),
    }


def render(doc, generated):
    for name, body in generated.items():
        pattern = re.compile(rf"(<!-- BEGIN GENERATED: {re.escape(name)} -->\n)(?:.*?\n)?(<!-- END GENERATED: {re.escape(name)} -->)",
                             re.S)
        doc = pattern.sub(lambda m: m.group(1) + body + "\n" + m.group(2), doc)
    return doc


def main(args):
    generated = blocks()
    texts = {path: path.read_text() for path in DOCS}
    used = {n for t in texts.values() for n in re.findall(r"<!-- BEGIN GENERATED: ([\w-]+) -->", t)}
    unknown, missing = used - generated.keys(), generated.keys() - used
    if unknown or missing:
        sys.exit(f"build_architecture: unknown blocks {sorted(unknown)}, blocks in no doc {sorted(missing)}")
    stale = []
    for path, current in texts.items():
        updated = render(current, generated)
        if updated == current:
            continue
        stale.append(path.relative_to(ROOT).as_posix())
        if "--check" not in args:
            path.write_text(updated)
    if "--check" in args:
        if stale:
            print(f"{', '.join(stale)} out of date. Run: python3 -I scripts/build_architecture.py", file=sys.stderr)
            sys.exit(1)
        print("architecture docs are up to date")
        return
    print("updated " + ", ".join(stale) if stale else "architecture docs already up to date")


if __name__ == "__main__":
    main(sys.argv[1:])
