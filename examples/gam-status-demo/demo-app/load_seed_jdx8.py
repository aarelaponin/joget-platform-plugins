#!/usr/bin/env python3
"""Load a project_lifecycle seed into jdx8's LIVE StatusManager metamodel tables
(app_fd_mmentitystate / app_fd_mmentitytransition — the store MmConfigService reads), then
read it back. Proves the lifecycle projector's output populates the real config store cleanly.

    load_seed_jdx8.py <lifecycle-seed.yaml>
"""
import datetime
import os
import subprocess
import sys
import uuid

import yaml

DSN = ["-h", "localhost", "-p", "5432", "-U", "joget_gam", "-d", "jwdb_gam"]
ENV = dict(os.environ, PGPASSWORD=os.environ.get("JOGET_GAM_DB_PASSWORD", ""))
NOW = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def psql(sql):
    return subprocess.run(["psql", *DSN, "-tAc", sql], capture_output=True, text=True, env=ENV).stdout.strip()


def main():
    seed = yaml.safe_load(open(sys.argv[1]))
    psql("delete from app_fd_mmentitystate")
    psql("delete from app_fd_mmentitytransition")
    for st in seed["mmEntityState"]:
        psql("insert into app_fd_mmentitystate (id, datecreated, c_entity, c_scope, c_code, "
             "c_isinitial, c_isterminal) values "
             f"('{uuid.uuid4()}','{NOW}','{st['entity']}','{st['scope']}','{st['code']}',"
             f"'{st['isInitial']}','{st['isTerminal']}')")
    for t in seed["mmEntityTransition"]:
        psql("insert into app_fd_mmentitytransition (id, datecreated, c_entity, c_scope, "
             "c_fromstatus, c_tostatus) values "
             f"('{uuid.uuid4()}','{NOW}','{t['entity']}','{t['scope']}','{t['fromStatus']}',"
             f"'{t['toStatus']}')")
    print("=== read back from jdx8 live tables ===")
    print("mmEntityState (entity|code|isInitial|isTerminal):")
    print(psql("select c_entity||'|'||c_code||'|'||coalesce(c_isinitial,'')||'|'||"
               "coalesce(c_isterminal,'') from app_fd_mmentitystate order by c_code"))
    print("mmEntityTransition (entity|from|to):")
    print(psql("select c_entity||'|'||c_fromstatus||'|'||c_tostatus "
               "from app_fd_mmentitytransition order by c_fromstatus"))


if __name__ == "__main__":
    main()
