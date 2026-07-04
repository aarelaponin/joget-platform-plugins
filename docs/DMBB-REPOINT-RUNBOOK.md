# DMBB re-point runbook (Phase 2 acceptance — the two-track live step)

Re-point `cmbb-plugins` onto the three promoted platform modules (event-chain,
status-manager, decision-approval), retire the moved copies, and prove DMBB still works
unchanged via `run_t30` + `run_t31` + full regression. **Destructive, live** — run it as a
focused pass on a branch, with the correct Joget instance confirmed.

Repo: `…/03_debt_management/cmbb/plugins/cmbb-plugins`  (git branch `main` today — branch first).
Platform artifacts already installed to `~/.m2` (event-chain / status-manager /
decision-approval `1.0.0`) so cmbb resolves them locally.

## 0. Branch + baseline
```
cd …/03_debt_management && git checkout -b phase2-repoint-das
```

## 1. pom — pin the 3 platform modules (provided; embed into the cmbb fat bundle)
Add to cmbb-plugins/pom.xml dependencies (version 1.0.0), and ensure Embed-Dependency
includes them so their classes ship inside the cmbb JAR:
`com.fiscaladmin.joget:joget-event-chain`, `:joget-status-manager`, `:joget-decision-approval`.

## 2. Java import re-point (25 files)
Mechanical package swaps across cmbb-plugins/src/main/java:
```
com.fiscaladmin.mtca.cmbb.service.CaseEventWriter      → com.fiscaladmin.joget.eventchain.CaseEventWriter
com.fiscaladmin.mtca.cmbb.service.ChainVerifyService   → com.fiscaladmin.joget.eventchain.ChainVerifyService
com.fiscaladmin.mtca.cmbb.service.CaseRefGenerator     → com.fiscaladmin.joget.eventchain.CaseRefGenerator
com.fiscaladmin.mtca.cmbb.service.StatusManager        → com.fiscaladmin.joget.statusmanager.StatusManager
com.fiscaladmin.mtca.cmbb.service.MmConfigService      → com.fiscaladmin.joget.statusmanager.MmConfigService
com.fiscaladmin.mtca.cmbb.service.GuardContext         → com.fiscaladmin.joget.statusmanager.GuardContext
com.fiscaladmin.mtca.cmbb.service.InvalidTransitionException → com.fiscaladmin.joget.statusmanager.InvalidTransitionException
com.fiscaladmin.mtca.cmbb.phase.GuardPhase             → com.fiscaladmin.joget.statusmanager.GuardPhase
com.fiscaladmin.mtca.cmbb.service.ApprovalService      → com.fiscaladmin.joget.approval.ApprovalService
com.fiscaladmin.mtca.cmbb.service.DecisionService      → com.fiscaladmin.joget.approval.DecisionService
com.fiscaladmin.mtca.cmbb.service.AuthorityResolver    → com.fiscaladmin.joget.approval.AuthorityResolver
com.fiscaladmin.mtca.cmbb.service.MatrixValidator      → com.fiscaladmin.joget.approval.MatrixValidator
com.fiscaladmin.mtca.cmbb.service.ApprovalInbox        → com.fiscaladmin.joget.approval.ApprovalInbox
```

## 3. Delete the moved classes from cmbb (18)
service/: CaseEventWriter, ChainVerifyService, CaseRefGenerator, StatusManager,
MmConfigService, GuardContext, InvalidTransitionException, ApprovalService, DecisionService,
AuthorityResolver, MatrixValidator, ApprovalInbox; phase/GuardPhase; root:
ApprovalGateEngine, ApprovalSweepEngine, ApprovalDelegateEngine, AuthorityMatrixEngine,
ApprovalInboxBinder. (The phases Open/PreClose/Close + TransitionGuard STAY.)

## 4. MmConfigService split — CmbbConfigService
The platform MmConfigService dropped the domain readers. Add a cmbb
`service/CmbbConfigService extends com.fiscaladmin.joget.statusmanager.MmConfigService`
carrying: `allocPolicy`, `coiRules`, `activeOfficers`, `slaRows`, `slaRow`, `calendarRow`
(+ constants F_ALLOC=mmAlloc, F_COI=mmCoi, F_OFFICER=mdOfficerProfile, F_SLA=mmSla,
F_CALENDAR=mmCalendar). Re-point the callers to `new CmbbConfigService(dao)`:
- `service/AllocationService.java`  (allocPolicy / coiRules / activeOfficers)
- `service/DeadlineService.java`    (slaRows / slaRow / calendarRow)
(cmbb's own ApprovalService is deleted — ignore.)

## 5. cmbb Activator — carriers + effect registration (the inversion, consumer side)
In `Activator.start(...)`, before/independent of plugin registration:
```
CaseEventWriter.setDefaultEventFormId("cmEvent");
GuardContext.setFormIds("cmCase", "cmTask", "cmDoc");
AuthorityResolver.registerRoleLevelDefault("dm_officer","OFFICER");   // + supervisor/manager/policy_admin
DecisionEffects.register("INSTALMENT_PLAN", (entity,recordId,actor,now) ->
        new ReliefService(dao, new CaseEventWriter(dao), new JogetProcessStarter()).apply(recordId, actor, now));
DecisionEffects.register("WRITE_OFF", (entity,recordId,actor,now) ->
        new WriteOffService(dao, new CaseEventWriter(dao)).applyApproved(recordId, actor, now));
DecisionEffect enforce = (entity,recordId,actor,now) ->
        new EnforcementActionService(dao, new CaseEventWriter(dao)).executeApproved(recordId, actor, now);
DecisionEffects.register("ENFORCE_ACTION", enforce);
DecisionEffects.register("ENFORCE_JUDICIAL", enforce);
```
Delete cmbb `service/ApprovalEffects.java`’s `service(dao)`/`registry(dao)` factories (the
engines now read `DecisionEffects.snapshot()`); keep it only as the registrar above (or fold
into the Activator). Re-point `ClosePhase` to the platform `DecisionService`.

## 6. App-definition FQN migration (10 artefact files)
The deployed app references the engines/binder by their OLD FQNs. Swap
`com.fiscaladmin.mtca.cmbb.<Engine>` → `com.fiscaladmin.joget.approval.<Engine>` in:
- dmbb/features/DMBB-APPROVAL-gate/forms/{F-cmApprovalDecision.spec.yml, F-cmApprovalDelegate.spec.yml, F-cmApprovalSweep.spec.yml, cmAuthorityCheck.json}
- dmbb/features/DMBB-APPROVAL-gate/datalists/list_cmApproval_my.json
- dmbb/generated/forms/{cmApprovalDecision.json, cmApprovalDelegate.json, cmApprovalSweep.json, cmAuthorityCheck.json}
- dmbb/generated/datalists/list_cmApproval_my.json
Then redeploy those forms/datalists to the app.

## 7. Build + deploy + acceptance (LIVE — needs the DMBB Joget instance up)
```
mvn -f cmbb-plugins/pom.xml -DskipTests clean package     # compile-verify the re-point
# deploy the JAR + the 6 updated form/datalist artefacts to the running Joget, restart if needed
# then:
run_t30    # approval gate: single / chain / quorum / rank-block / SoD  → all pass
run_t31    # approval depth 2: sweep escalate→timeout, delegate binding → all pass
# full dmbb regression sweep → green
```
Confirm the live instance first (8077 and 8089 both answer 200; the DMBB app instance must be
the one carrying jwdb + the mock ledger).

## Definition of done
- [ ] `mvn package` green; cmbb JAR contains no `com.fiscaladmin.mtca.cmbb.service.{moved}`.
- [ ] `DecisionEffects` has the 3 effects registered at start-up; a gate approval runs the effect.
- [ ] run_t30 + run_t31 + full regression green on the live instance.
- [ ] cmbb no longer contains the 18 moved classes; it pins the 3 platform artifacts.
- [ ] Commit on the branch; open the PR.
