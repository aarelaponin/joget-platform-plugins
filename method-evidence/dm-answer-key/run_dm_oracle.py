#!/usr/bin/env python3
"""DM answer-key oracle — the method's own regression test (PLAN v3 M1c).

Drives the kit's upstream checks against a faithful reconstruction of the Debt Management naive
inventory and proves they CATCH the documented failures (EVIDENCE-DM-forced-slot-extraction):

  - spec_lint flags the Class-A silences across ALL SIX dimensions the spec went quiet on
    (formulas, failure behaviour, interpretation, lifecycle, grain, scenario/state coverage);
  - citation_check flags the NINE Class-B values the spec had already decided but the build set
    uncited.

Passes (exit 0) only if the checks catch the failures. Point at the kit with $KIT (defaults to the
sibling checkout). Run:  KIT=/path/to/joget-spec-kit python3 run_dm_oracle.py
"""
import os
import pathlib
import sys

import yaml

HERE = pathlib.Path(__file__).resolve().parent
KIT = pathlib.Path(os.environ.get("KIT") or (HERE.parents[2] / "joget-spec-kit"))
sys.path.insert(0, str(KIT / "tools"))

try:
    import spec_lint as sl
    import citation_check as cc
except ImportError:
    print(f"dm-oracle: kit tools not found under {KIT}/tools (set $KIT)"); sys.exit(2)

SUBJECTS = yaml.safe_load((HERE / "subjects.yaml").read_text())
NAIVE = yaml.safe_load((HERE / "naive.decisions.yaml").read_text())
LEAKY = yaml.safe_load((HERE / "realization-leaky.yaml").read_text())

SIX_FAMILIES = {"U-FORMULA", "U-FAILURE", "U-INTERPRET", "U-LIFECYCLE", "U-GRAIN"}  # + scenario/state


def main() -> int:
    findings = sl.spec_lint(NAIVE, SUBJECTS)
    gaps = [f for f in findings if f[1] == "GAP"]
    fired = {code for code, _, _, _ in findings}
    scenario_family = bool(fired & {"U-SCENARIO", "U-STATES"})
    cite_errors = cc.citation_check(LEAKY, NAIVE)

    problems = []
    missing_families = SIX_FAMILIES - fired
    if missing_families:
        problems.append(f"spec_lint missed dimension-families {sorted(missing_families)}")
    if not scenario_family:
        problems.append("spec_lint did not fire the scenario/state family")
    if len(gaps) < 20:
        problems.append(f"spec_lint found only {len(gaps)} Class-A gaps (expected >= 20)")
    if len(cite_errors) < 9:
        problems.append(f"citation_check found only {len(cite_errors)} Class-B leaks (expected >= 9)")

    print(f"DM ANSWER-KEY ORACLE — spec_lint: {len(gaps)} Class-A gaps across "
          f"{len(fired & (SIX_FAMILIES | {'U-SCENARIO', 'U-STATES'}))} dimension-families "
          f"{sorted(fired)}; citation_check: {len(cite_errors)} Class-B leaks caught.")
    if problems:
        print("ORACLE FAIL — " + "; ".join(problems)); return 1
    print("ORACLE PASS — the checks catch every documented Class-A dimension and all nine Class-B leaks.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
