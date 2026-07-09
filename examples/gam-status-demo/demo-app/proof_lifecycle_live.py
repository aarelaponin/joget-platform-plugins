#!/usr/bin/env python3
"""G3 live proof — the LIFECYCLE PROJECTOR's output drives the live StatusManager (jdx8).

Reads a `project_lifecycle` seed (lifecycle-seed.yaml), loads its states/transitions into the
gam-status-demo `mmEntityState`/`mmEntityTransition` tables (re-keyed to the guard's fixed
entity `gamWidget`), deploys/seeds through the Joget form path so the GamMoveGuard fires, then
drives a LEGAL and an ILLEGAL move through the platform StatusManager and asserts enforcement.

Reuses the proven deploy_dx9 login/csrf/import/publish (jdx8 needs the master-token login).

    proof_lifecycle_live.py <lifecycle-seed.yaml>
"""
import os, re, sys, time
import requests
import yaml

sys.path.insert(0, "/Users/aarelaponin/IdeaProjects/rsr/joget/joget-spec-kit/tools")
import deploy_dx9 as d

BASE = "http://localhost:8088/jw"
UV = BASE + "/web/userview/gamsm/gamsmUv/_"
USER, PW = "admin", os.environ.get("JDX8_PASSWORD", "admin")
JWA = "/tmp/APP_gamsm-1.jwa"
ENTITY = "gamWidget"          # the guard's fixed entity key (schema forces snake_case upstream)
s = requests.Session()


def submit(menu, fields):
    url = f"{UV}/{menu}"
    g = s.get(url, params={"_mode": "add"}, headers={"Referer": BASE + "/web/console/home"})
    hidden = dict(re.findall(r'<input[^>]*type="hidden"[^>]*name="([^"]+)"[^>]*value="([^"]*)"', g.text))
    name, val = d.csrf(s, BASE)
    data = {}; data.update(hidden); data.update(fields); data.setdefault(name, val)
    r = s.post(url, params={"_mode": "add", "_submit": "true"}, data=data,
               headers={"Referer": url, name: val})
    return r.status_code


def list_rows(menu):
    r = s.get(f"{UV}/{menu}", params={"_action": "getData", "isReturnData": "true"},
              headers={"Referer": BASE + "/web/console/home"})
    try:
        return r.json().get("data", [])
    except Exception:
        return []


def deploy():
    name, val = d.csrf(s, BASE)
    for v in ("1", "2", "3"):
        s.post(f"{BASE}/web/console/app/gamsm/{v}/delete", data={name: val},
               headers={"Referer": BASE + "/web/console/apps", name: val})
    d.import_app(s, BASE, JWA)
    d.publish(s, BASE, "gamsm", "1")


def main():
    seed = yaml.safe_load(open(sys.argv[1] if len(sys.argv) > 1 else "/tmp/lc-seed.yaml"))
    states, trans = seed["mmEntityState"], seed["mmEntityTransition"]
    init = next(st["code"] for st in states if st.get("isInitial") == "true")
    legal_to = next(t["toStatus"] for t in trans if t["fromStatus"] == init)
    print(f"projector seed: entity(src)={states[0]['entity']} states={[st['code'] for st in states]} "
          f"transitions={[(t['fromStatus'], t['toStatus']) for t in trans]}")
    print(f"drive: initial={init}  legal={init}->{legal_to}  illegal={legal_to}->{init}\n")

    d.login(s, BASE, USER, PW)
    deploy()
    inst = {"installation_path": "/Users/aarelaponin/joget-enterprise-linux-9.0.7",
            "tomcat": {"http_port": 8088}}
    d.restart(inst, BASE)          # DD-005: clear the definition cache so the fresh userview serves
    d.login(s, BASE, USER, PW)     # re-establish session after restart
    time.sleep(2)

    for st in states:
        submit("mmEntityStateList", {"entity": ENTITY, "scope": st["scope"], "code": st["code"],
               "isInitial": st.get("isInitial") or "false", "isTerminal": st.get("isTerminal") or "false"})
    for t in trans:
        submit("mmEntityTransitionList", {"entity": ENTITY, "scope": t["scope"],
               "fromStatus": t["fromStatus"], "toStatus": t["toStatus"]})
    submit("gamWidgetList", {"name": "W1", "status": init})
    time.sleep(2)
    widgets = list_rows("gamWidgetList")
    wid = widgets[0]["id"] if widgets else None
    print("seeded. widget:", wid, "status:", widgets[0].get("status") if widgets else None)
    if not wid:
        sys.exit("no widget id (seeding failed)")

    print("LEGAL   move (", init, "->", legal_to, "):", submit("gamMoveAdd", {"widgetId": wid, "targetStatus": legal_to, "reason": "projector legal"})); time.sleep(2)
    print("ILLEGAL move (", legal_to, "->", init, "):", submit("gamMoveAdd", {"widgetId": wid, "targetStatus": init, "reason": "projector illegal"})); time.sleep(2)

    final = (list_rows("gamWidgetList") or [{}])[0].get("status")
    moves = [{"target": m.get("targetStatus"), "result": (m.get("result") or "")[:48]} for m in list_rows("gamMoveList")]
    events = list_rows("gamEventList")
    print("\n=== RESULTS ===")
    print("final widget status:", final, f"(expect {legal_to})")
    print("moves:", moves)
    print("event-chain rows:", len(events))
    legal_ok = final == legal_to
    illegal_refused = final != init and any(m["target"] == init and not m["result"].startswith("OK") for m in moves)
    print(f"\nLEGAL advanced: {legal_ok}  |  ILLEGAL refused (nothing written): {illegal_refused}")
    print("G3 LIVE PROOF:", "PASS" if legal_ok and illegal_refused else "FAIL")
    sys.exit(0 if legal_ok and illegal_refused else 2)


if __name__ == "__main__":
    main()
