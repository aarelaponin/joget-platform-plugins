#!/usr/bin/env python3
"""Self-contained deploy of the gamsm app to jdx8 (GAM, port 8088).
Login (admin/admin) -> CSRF -> delete any prior gamsm -> import -> publish.
No toolkit dependency; no DB password needed."""
import sys, requests

BASE = "http://localhost:8088/jw"
USER, PW = "admin", "admin"
JWA = "/tmp/APP_gamsm-1.jwa"
s = requests.Session()


def csrf():
    r = s.get(BASE + "/csrf", headers={"Referer": BASE + "/web/console/home"})
    return r.text.strip()


def login():
    r = s.post(BASE + "/web/json/workflow/user/login",
               data={"j_username": USER, "j_password": PW, "hash": ""},
               headers={"Referer": BASE + "/web/login"})
    print("login:", r.status_code, r.text[:120])


def main():
    login()
    # delete prior version(s) — ignore failures
    for v in ("1", "2", "3"):
        try:
            r = s.post(f"{BASE}/web/console/app/gamsm/{v}/delete",
                       data={"OWASP_CSRFTOKEN": csrf()},
                       headers={"Referer": BASE + "/web/console/apps"})
            if r.status_code == 200:
                print(f"delete v{v}: 200")
        except Exception as e:
            print("delete err", e)
    # import
    with open(JWA, "rb") as f:
        r = s.post(BASE + "/web/console/app/import/submit",
                   files={"appZip": ("APP_gamsm-1.jwa", f, "application/zip")},
                   data={"OWASP_CSRFTOKEN": csrf()},
                   headers={"Referer": BASE + "/web/console/app/import"})
    print("import:", r.status_code, r.text[:200])
    # publish v1
    r = s.post(BASE + "/web/console/app/gamsm/1/publish",
               data={"OWASP_CSRFTOKEN": csrf()},
               headers={"Referer": BASE + "/web/console/apps"})
    print("publish v1:", r.status_code)
    # confirm
    r = s.get(BASE + "/web/json/console/app/list", headers={"Referer": BASE + "/web/console/apps"})
    print("apps:", [a.get("id") for a in r.json()] if r.headers.get("content-type", "").startswith("application/json") else r.text[:100])


if __name__ == "__main__":
    main()
