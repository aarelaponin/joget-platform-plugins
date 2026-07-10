#!/usr/bin/env python3
"""Bidirectional reconstruction check for the DM anchor pack (feeds the realism gate).
Asserts: every DMBB feature (F01-F14) maps to a real skeleton procedure; every skeleton procedure
is reached by >=1 feature; and every procedure cites >=1 source. Run from the pack root."""
import pathlib
import sys

import yaml

P = pathlib.Path(__file__).resolve().parent
sk = yaml.safe_load((P / "skeleton.yaml").read_text())["procedures"]
an = yaml.safe_load((P / "anchors" / "procedures.yaml").read_text())
proc_ids = {p["id"] for p in sk}
errs = []

for p in sk:
    if not p.get("sources"):
        errs.append(f"procedure {p['id']}: no source (verify-before-build rule)")

feats = an.get("dm_features", [])
reached = set()
for fdef in feats:
    if fdef["procedure"] not in proc_ids:
        errs.append(f"feature {fdef['f']} -> unknown procedure {fdef['procedure']!r}")
    else:
        reached.add(fdef["procedure"])

# every procedure must be reached by at least one feature (no orphan procedure)
for pid in proc_ids:
    if pid not in reached:
        errs.append(f"procedure {pid}: not reached by any DMBB feature (orphan)")

# level entries must match the skeleton
lvl_ids = {p["id"] for p in an.get("procedures", [])}
for pid in proc_ids:
    if pid not in lvl_ids:
        errs.append(f"procedure {pid}: no L-level in anchors/procedures.yaml")


tadat = an.get("tadat_indicators", [])
if len(tadat) < 7:
    errs.append(f"expected the 7 verified TADAT indicators (P5-15/17/19/20, P7-23/24/25), found {len(tadat)}")
for ti in tadat:
    if ti["procedure"] not in proc_ids:
        errs.append(f"TADAT {ti['id']} -> unknown procedure {ti['procedure']!r}")

if errs:
    print("DM RECONSTRUCTION: FAIL")
    for e in errs:
        print("  -", e)
    sys.exit(1)
print(f"DM RECONSTRUCTION: PASS — {len(feats)} features map to {len(proc_ids)} procedures; "
      f"every procedure sourced and reached; {len(tadat)} TADAT indicators anchored (external, tadat.org).")
sys.exit(0)
