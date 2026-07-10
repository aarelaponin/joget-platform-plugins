#!/usr/bin/env python3
"""Method-gate — regress every method-evidence run against the kit (a standing CI check).

Locks in the proof: any change to the kit tools (or to a pilot's decisions) that breaks the method
fails here. Runs, against the shipped kit:
  1. the DM answer-key oracle (spec_lint catches the Class-A gaps, citation_check the Class-B leaks);
  2. the F13-payments run — GREEN (lint ok, spec-lint 0 gaps, walkthrough --strict clean);
  3. the approval-gate run — STABLE in-progress (lint ok, walkthrough clean, exactly 2 open decisions
     still surfaced — the authority bands + role vocabulary).

Point at the kit with $KIT (defaults to the sibling checkout). Exit 0 only if every check holds.
"""
import os
import pathlib
import subprocess
import sys

import yaml

HERE = pathlib.Path(__file__).resolve().parent
KIT = pathlib.Path(os.environ.get("KIT") or (HERE.parents[1] / "joget-spec-kit"))
sys.path.insert(0, str(KIT / "tools"))

try:
    import lint_decisions as ld
    import spec_lint as sl
    import walkthrough as wt
    import toconfirm as tc
except ImportError:
    print(f"method-gate: kit tools not found under {KIT}/tools (set $KIT)"); sys.exit(2)


def _load(p): return yaml.safe_load(pathlib.Path(p).read_text())


def check(name, cond, detail=""):
    print(f"  [{'OK ' if cond else 'FAIL'}] {name}{(' — ' + detail) if detail and not cond else ''}")
    return cond


def gate_run(folder, *, expect_green, expect_open):
    d = HERE / folder
    dec = _load(d / "decisions.yaml")
    subj = _load(d / "subjects.yaml")
    scen = _load(d / "scenarios.yaml")
    ok = True
    ok &= check(f"{folder}: lint-decisions clean", ld.lint(dec)[0] == [])
    gaps = [f for f in sl.spec_lint(dec, subj) if f[1] == "GAP"]
    if expect_green:
        ok &= check(f"{folder}: spec-lint 0 gaps", len(gaps) == 0, f"{gaps}")
    else:
        ok &= check(f"{folder}: spec-lint still surfaces the open decision(s)", len(gaps) >= 1)
    _, uncited, dangling = wt.build(scen, dec)
    ok &= check(f"{folder}: walkthrough --strict clean", not uncited and not dangling, f"uncited={uncited} dangling={dangling}")
    opencount = len(tc.collect([dec]))
    ok &= check(f"{folder}: {opencount} open decision(s) (expect {expect_open})", opencount == expect_open)
    return ok


def main() -> int:
    print("METHOD-GATE — regressing the method-evidence runs against the kit")
    ok = True
    # 1. DM answer-key oracle (its own runner asserts the Class-A/B catch)
    rc = subprocess.run([sys.executable, str(HERE / "dm-answer-key" / "run_dm_oracle.py")],
                        env={**os.environ, "KIT": str(KIT)}).returncode
    ok &= check("dm-answer-key oracle PASS", rc == 0)
    # 2. F13 — green
    ok &= gate_run("f13-pilot", expect_green=True, expect_open=0)
    # 3. approval-gate — green after the post-1.0 rulings (0 open)
    ok &= gate_run("approval-gate-run2", expect_green=True, expect_open=0)
    print("METHOD-GATE:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
