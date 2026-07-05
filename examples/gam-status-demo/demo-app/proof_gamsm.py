#!/usr/bin/env python3
"""End-to-end reuse proof on jdx8 (GAM, port 8088).

Login -> import gamsm -> publish -> seed the state machine (mmEntityState,
mmEntityTransition, gamWidget) via userview CRUD form submits -> drive a LEGAL
and an ILLEGAL gamMove -> read back gamWidget.status, gamMove.result and the
gamEvent chain. All data goes through the Joget form path, so the GamMoveGuard
post-processor (which calls the PLATFORM StatusManager) actually fires.
"""
import re, sys, time, requests

BASE = "http://localhost:8088/jw"
UV = BASE + "/web/userview/gamsm/gamsmUv/_"
USER, PW = "admin", "admin"
JWA = "/tmp/APP_gamsm-1.jwa"
s = requests.Session()


def _clean(tok):
    tok = (tok or "").strip()
    if "\n" in tok or "<" in tok or len(tok) > 120:
        m = re.search(r'([A-Za-z0-9+/=_-]{20,100})', tok)
        tok = m.group(1) if m else ""
    return tok


def csrf():
    return _clean(s.get(BASE + "/csrf", headers={"Referer": BASE + "/web/console/home"}).text)


def login():
    s.get(BASE + "/web/login")
    tok = _clean(s.get(BASE + "/csrf", headers={"Referer": BASE + "/web/login"}).text)
    data = {"j_username": USER, "j_password": PW}
    headers = {"Referer": BASE + "/web/login"}
    if tok:
        data["OWASP-CSRFTOKEN"] = tok
        headers["OWASP-CSRFTOKEN"] = tok
    s.post(BASE + "/j_spring_security_check", data=data, headers=headers, allow_redirects=True)
    home = s.get(BASE + "/web/console/home")
    ok = "login" not in home.url.lower()
    print("login:", "OK" if ok else "FAIL", home.url)
    return ok


def deploy():
    for v in ("1", "2", "3"):
        s.post(f"{BASE}/web/console/app/gamsm/{v}/delete",
               data={"OWASP_CSRFTOKEN": csrf()}, headers={"Referer": BASE + "/web/console/apps"})
    with open(JWA, "rb") as f:
        r = s.post(BASE + "/web/console/app/import/submit",
                   files={"appZip": ("APP_gamsm-1.jwa", f, "application/zip")},
                   data={"OWASP_CSRFTOKEN": csrf()},
                   headers={"Referer": BASE + "/web/console/app/import"})
    print("import:", r.status_code)
    r = s.post(BASE + "/web/console/app/gamsm/1/publish",
               data={"OWASP_CSRFTOKEN": csrf()}, headers={"Referer": BASE + "/web/console/apps"})
    print("publish:", r.status_code)


def submit(menu, fields):
    """Submit a userview CRUD add-form: GET the add page, carry hidden inputs, POST."""
    url = f"{UV}/{menu}"
    g = s.get(url, params={"_mode": "add"}, headers={"Referer": BASE + "/web/console/home"})
    hidden = dict(re.findall(r'<input[^>]*type="hidden"[^>]*name="([^"]+)"[^>]*value="([^"]*)"', g.text))
    data = {}
    data.update(hidden)
    data.update(fields)
    if "OWASP_CSRFTOKEN" not in data:
        data["OWASP_CSRFTOKEN"] = csrf()
    r = s.post(url, params={"_mode": "add", "_submit": "true"}, data=data, headers={"Referer": url})
    return r.status_code


def list_rows(menu):
    r = s.get(f"{UV}/{menu}", params={"_action": "getData", "isReturnData": "true"},
              headers={"Referer": BASE + "/web/console/home"})
    try:
        return r.json().get("data", [])
    except Exception:
        return r.text[:200]


def main():
    if not login():
        sys.exit(1)
    deploy()
    time.sleep(3)
    # --- seed the state machine (entity=gamWidget, scope=DEFAULT) ---
    E = {"entity": "gamWidget", "scope": "DEFAULT"}
    print("state DRAFT :", submit("mmEntityStateList", {**E, "code": "DRAFT", "isInitial": "true", "isTerminal": "false"}))
    print("state ACTIVE:", submit("mmEntityStateList", {**E, "code": "ACTIVE", "isInitial": "false", "isTerminal": "false"}))
    print("state CLOSED:", submit("mmEntityStateList", {**E, "code": "CLOSED", "isInitial": "false", "isTerminal": "true"}))
    print("tx DRAFT->ACTIVE :", submit("mmEntityTransitionList", {**E, "fromStatus": "DRAFT", "toStatus": "ACTIVE"}))
    print("tx ACTIVE->CLOSED:", submit("mmEntityTransitionList", {**E, "fromStatus": "ACTIVE", "toStatus": "CLOSED"}))
    print("widget W1 (DRAFT):", submit("gamWidgetList", {"name": "Widget One", "status": "DRAFT"}))
    time.sleep(2)
    widgets = list_rows("gamWidgetList")
    wid = widgets[0]["id"] if isinstance(widgets, list) and widgets else None
    print("widget id:", wid, "status:", widgets[0].get("status") if wid else widgets)
    if not wid:
        sys.exit("no widget id")
    # --- LEGAL move: DRAFT -> ACTIVE ---
    print("move LEGAL  (DRAFT->ACTIVE):", submit("gamMoveAdd", {"widgetId": wid, "targetStatus": "ACTIVE", "reason": "reuse proof legal"}))
    time.sleep(2)
    # --- ILLEGAL move: ACTIVE -> DRAFT (no such transition) ---
    print("move ILLEGAL(ACTIVE->DRAFT):", submit("gamMoveAdd", {"widgetId": wid, "targetStatus": "DRAFT", "reason": "reuse proof illegal"}))
    time.sleep(2)
    print("\n=== RESULTS ===")
    print("gamWidget:", [{"id": w["id"], "status": w.get("status")} for w in list_rows("gamWidgetList")])
    print("gamMove  :", [{"target": m.get("targetStatus"), "result": m.get("result")} for m in list_rows("gamMoveList")])


if __name__ == "__main__":
    main()
