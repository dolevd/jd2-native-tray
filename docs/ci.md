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
uses the current HEAD. Each export must also match its committed `tree_sha256`
before any exported Ant build instructions are executed. The digest covers every
file's content, relative path and executable flag, plus directories. Symlinks and
other special entries are rejected, and SVN externals are not fetched. No JD2 installer, updater, Flatpak or graphical application
is run on the build worker.

The official documented SVN URLs use unauthenticated `svn://`; their HTTPS
endpoints do not provide working anonymous access. Content pins in the reviewed
Git checkout provide the trust anchor: CI never obtains expected hashes from SVN
or updates them to accept downloaded content. The initial pins were established
from the fixed-revision exports, with build scripts and bundled libraries
cross-checked against the [HTTPS community mirror at a fixed commit](https://github.com/mycodedoesnotcompile2/jdownloader_mirror/tree/7aec0cddff4f87089a287db02f17a935beeded55).
This establishes a fixed baseline, not an upstream signature or a guarantee of
upstream authorship. The mirror is only a cross-check; CI still builds the official exports.

[ci/jd2-toolchain.json](../ci/jd2-toolchain.json) pins Temurin **17.0.20.1+1**
and Apache Ant **1.10.15**. Ant is downloaded over HTTPS and checked against its
committed SHA-512 before extraction or execution. The preparation script verifies
the JDK vendor, complete runtime version (including build number), and Ant version.
The JDK lock also records Adoptium's SemVer alias used by `setup-java`; the runtime
check still requires `17.0.20.1+1` exactly.

On the first cache miss, CI installs SVN and the pinned Ant, exports and verifies the pinned sources, runs the
official build, and retains only the six JARs used to compile our adapter. A
manifest records their checksums, source revisions and toolchain pins. The cache key includes the
runner OS/architecture, complete source and toolchain lock files, and preparation
script. Exact cache hits verify and reuse those JARs without exporting or building
JD2. There is no fallback to another revision. Cache saving happens before our
extension tests, so a failing extension build does not discard a successful JD2 build.

GitHub caches are branch-scoped; pull requests can reuse the default branch cache,
but their own caches cannot replace it. GitHub can also
[evict unused caches](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching).
An evicted/deleted cache must be rebuilt. Cache corruption fails verification;
delete that cache in **Actions → Caches** and rerun.

To update JD2, review the new exports against an independently trusted source,
then change the SVN revisions and corresponding tree digests in the lock file
together and open a PR. Do not automatically accept a hash from an unverified
SVN response. The canonical digest can be calculated with `tree_sha256` in
`scripts/ci/prepare_jd2.py`. Toolchain updates likewise require changes to their
lock file, including Ant's archive hash. Either changed lock selects a new cache key. Test the resulting
extension in the distributed JD2 application before publishing a release.

To use the same source build locally, install SVN, Python 3.11 or later, and the
exact Temurin and Ant versions in the toolchain lock (with both tools on `PATH`), then run:

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
