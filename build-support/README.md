# build-support — in-project Maven repository

This directory makes the reactor build **self-contained**: it can be built and tested on
any machine or CI runner with only Maven Central reachable — **no Joget Maven repository and
no credentials**.

## Why this exists

The plugins compile against Joget build-time artifacts that are **not on Maven Central**:

- `org.joget:wflow-core`, `wflow-commons`, `wflow-directory`, `wflow-jdbc`,
  `wflow-plugin-base`, `wflow-wfengine`, `org.joget.api:apibuilder_api` — Joget DX platform
  jars (used at **`provided`** scope; the running Joget server supplies them at runtime).
- `global.govstack:joget-status-framework` — Apache-2.0 status/quality helper, a dependency
  of `form-quality-runtime`.
- Joget's bundled workflow engine transitively pulled in by `wflow-core`:
  `EnhydraShark:*` (Enhydra Shark, LGPL) and `TogetherRelationalObjects:*` /
  `TogetherApplicationFramework:*` (Enhydra DODS/EAF, LGPL).

Joget publishes these on its own Archiva (`developer.joget.org/archiva`), but that server is
**credential-gated** and returns `401` without a valid Joget developer account — so a bare CI
runner cannot resolve them. Rather than depend on that external, authenticated, changeable
server, the exact artifacts are vendored here as a **file-based Maven repository**.

## How it is wired

`build-support/m2/` is a normal Maven repository layout (`groupId/artifactId/version/…`).
The reactor's root `pom.xml` declares it, listed before Central:

```xml
<repository>
  <id>in-project-joget</id>
  <url>file://${maven.multiModuleProjectDirectory}/build-support/m2</url>
  <releases><enabled>true</enabled></releases>
  <snapshots><enabled>true</enabled></snapshots>
</repository>
```

`${maven.multiModuleProjectDirectory}` resolves to the repo root, so the path is correct on
every checkout (local or CI). Nothing else is required: `mvn clean verify` on a clean runner
resolves these artifacts from here and everything else from Central.

## Scope and licensing

- Everything here is a **build-time / `provided`-scope** dependency — none of it is shipped
  inside the plugin JARs this repo produces (the plugin bundles only embed their own code and
  a couple of small utility libraries).
- `joget-status-framework` is Apache-2.0. The Enhydra Shark / Together artifacts are LGPL.
  The `org.joget:*` platform jars come from the developer's licensed Joget DX distribution.
- These files are vendored **only** to make CI reproducible in this **private** repository for
  internal use. Do not make this repository public without reviewing redistribution terms for
  the `org.joget:*` jars, or switch to GitHub Packages hosting (see below).

## Refreshing the set (e.g. Joget version bump)

The vendored set is exactly the non-Central closure of the reactor's dependencies. To
regenerate it after a Joget upgrade, on a machine whose `~/.m2` already has the new artifacts
(i.e. where a normal build succeeds):

```bash
./build-support/refresh.sh      # convergence loop: clean-room build → copy any missing
                                # non-Central artifact from ~/.m2 → repeat until green
```

`refresh.sh` runs `mvn clean verify` against an empty throwaway local repo using only Central
plus this file repo, and copies in any artifact that is missing from **both**. It stops when
the clean-room build is green — guaranteeing the set is complete and minimal.

## Alternative hosting

If this repo goes public, or you prefer not to keep binaries in git, host the same artifacts
in the repo's **GitHub Packages** Maven registry (the parent pom's `distributionManagement`
already targets it) and swap the `<repository>` URL for the Packages URL, with CI reading via
the automatic `GITHUB_TOKEN` (`packages: read`). The build stays otherwise identical.
