#!/usr/bin/env python3
"""
L2 bundle/manifest smoke check.

For every registry plugin built in this repo (status: active, module: plugins/...), verify against
its built JAR that:
  1. the OSGi manifest declares a Bundle-Activator (only for plugins that register classes),
  2. every class listed under `registers:` actually exists in the JAR.

Catches the failure class where a plugin compiles but would not load/register in Joget.
Runs with no Joget instance. Requires a prior `mvn install` so target/*.jar exist.

Usage:  python3 tests/manifest_smoke.py     (exit 0 = ok, 1 = problem)
"""
import glob
import os
import sys
import zipfile

try:
    import yaml
except ImportError:
    sys.exit("PyYAML required: pip install pyyaml")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def find_jar(module_dir):
    jars = [j for j in glob.glob(os.path.join(ROOT, module_dir, "target", "*.jar"))
            if "-sources" not in j and "-javadoc" not in j]
    return jars[0] if jars else None


def main():
    reg = yaml.safe_load(open(os.path.join(ROOT, "registry.yaml")))
    problems, checked = [], 0

    for p in reg.get("plugins", []):
        if p.get("status") != "active":
            continue
        module = p.get("module", "")
        if not module.startswith("plugins/"):
            continue  # external / registered-in-place — no JAR here
        pid = p["id"]
        jar = find_jar(module)
        if not jar:
            problems.append(f"[{pid}] no built JAR under {module}/target (run mvn install)")
            continue
        checked += 1
        with zipfile.ZipFile(jar) as z:
            names = set(z.namelist())
            manifest = z.read("META-INF/MANIFEST.MF").decode("utf-8", "replace") \
                if "META-INF/MANIFEST.MF" in names else ""
            registers = p.get("registers") or []
            if registers and "Bundle-Activator:" not in manifest.replace("\n ", ""):
                problems.append(f"[{pid}] manifest has no Bundle-Activator")
            for cls in registers:
                entry = cls.replace(".", "/") + ".class"
                if entry not in names:
                    problems.append(f"[{pid}] registered class missing from JAR: {cls}")
        print(f"  OK  {pid:24s} {os.path.basename(jar)}")

    print(f"\nChecked {checked} built plugins.")
    if problems:
        print("PROBLEMS:")
        for x in problems:
            print("  - " + x)
        return 1
    print("All manifest/registration checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
