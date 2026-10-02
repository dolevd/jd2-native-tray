# CI builds and releases

## Build without releasing

**Actions → Build and test → Run workflow** builds the selected branch. Pushes to
`main` and pull requests targeting `main` run it automatically. Download the
`native-tray-…` artifact from the completed run for `NativeTray.jar`, `SHA256SUMS`
and `SourceBuild.json`. This workflow never creates a tag or release.

Both build and release workflows call the same build/test workflow. It uses Java
17, runs Maven unit tests, tests the installer against dummy files, and checks the
packaged D-Bus protocol and recovery on a private session bus. All checks are
headless; desktop sessions and the JD2 GUI are not started.

## Pinned JD2 source build and caching

JD2 is built using its [official standalone Ant build](https://support.jdownloader.org/en/knowledgebase/article/self-compiled-standalone-build).
[ci/jd2-sources.json](../ci/jd2-sources.json) pins all four SVN projects, including
AppWorkUtils. Both the operative revision and peg revision are fixed; no checkout
uses the current HEAD. No JD2 installer, updater, Flatpak or graphical application
is run on the build worker.

On the first cache miss, CI installs SVN/Ant, exports the pinned sources, runs the
official build, and retains only the six JARs used to compile our adapter. A
manifest records their checksums and source revisions. The cache key includes the
runner OS/architecture, Java major version, source-lock file and preparation
script. Exact cache hits verify and reuse those JARs without exporting or building
JD2. There is no fallback to another revision. Cache saving happens before our
extension tests, so a failing extension build does not discard a successful JD2 build.

GitHub caches are branch-scoped; pull requests can reuse the default branch cache,
but their own caches cannot replace it. GitHub can also
[evict unused caches](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching).
An evicted/deleted cache must be rebuilt. Cache corruption fails verification;
delete that cache in **Actions → Caches** and rerun.

To update JD2, change the SVN revision numbers in the lock file together and open
a PR. The changed lock automatically selects a new cache key. Test the resulting
extension in the distributed JD2 application before publishing a release.

To use the same source build locally, install SVN, Ant and JDK 17, then run:

```sh
python3 scripts/ci/prepare_jd2.py
JD2_INSTALL_DIR="$PWD/.local-build/ci/jd2" mvn package
```

The preparation script itself reuses a valid local cache. Remove
`.local-build/ci/jd2` when deliberately changing its build inputs.

## Publish from main

After merging the changes you want to release:

1. Open **Actions → Release from main → Run workflow** and select **main**.
2. Enter a new version tag, for example `v0.1.0`, and optionally select **prerelease**.
3. Run the workflow. After all build and headless tests pass, the release appears
   on the repository's **Releases** page with the JAR, checksums and provenance.

The workflow builds the exact `main` commit selected at dispatch, even if `main`
advances while testing. Running it from another branch or reusing an existing tag
fails. Only the publishing job receives repository write access. GitHub's normal
`GITHUB_TOKEN` is sufficient; no personal access token or JD2 credentials are needed.

## Headless protocol checks

The private-bus probe checks SNI properties, transparent pixmap serialization,
dbusmenu replies, action dispatch, and loss/recovery of the watcher or tray host.
It needs D-Bus and Java, with no graphical session:

```sh
bash scripts/ci/test_protocol.sh
```

The probe uses Java's headless mode and a simulated tray watcher. No KDE services,
desktop session, input simulation or full JD2 application are started. Desktop
tests are deliberately excluded. Icon appearance, click timing, focus,
minimize/close behavior and password dialogs still require in-app testing.
