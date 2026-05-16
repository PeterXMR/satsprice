# Releasing SatsPrice

How builds get from a commit to a downloadable APK, and what's still missing
for a production-grade signed release.

## TL;DR

| Situation | Trigger | Result |
|---|---|---|
| Open or push to a PR against `main` | `pull_request` | CI builds the APK, attaches it as a workflow artifact (14-day retention). Download from the run's Summary page. |
| Merge to `main` | `push: branches: [main]` | Same — APK as workflow artifact, useful for nightly-style testing. |
| Push a tag like `v0.1.0-preview1` | `push: tags: ['v*']` | CI builds and **publishes a GitHub Release** with the APK attached. Tag has a `-suffix` → pre-release. |
| Push a tag like `v0.1.0` | `push: tags: ['v*']` | Same workflow, but the release is marked as **"Latest"** (not pre-release). |

CI workflow: [.github/workflows/android.yml](../.github/workflows/android.yml)

## Pre-release vs. release: what's the difference?

In GitHub, "pre-release" is **a flag, not a different artifact**. It:

- Adds a "Pre-release" badge on the release page.
- Excludes the release from the "Latest release" auto-tracker on the repo
  landing page and from the `releases/latest` redirect.

It does **not** affect the APK contents, the build process, or signing. A
pre-release APK is the same kind of file as a release APK — only the
discoverability differs.

This workflow decides the flag from the tag name (semver convention): any tag
with a hyphen after the version (e.g. `v1.2.3-preview1`, `v1.2.3-rc.1`,
`v1.2.3-beta.4`) is marked pre-release. Plain `v1.2.3` becomes "Latest".

## Publishing a release (today)

Decide on a tag name following [semver](https://semver.org/):

- `v0.2.0` — a normal release. Becomes "Latest" on the repo page.
- `v0.2.0-preview1`, `v0.2.0-rc.1` — pre-releases.

Then:

```sh
# From the commit you want to release (typically on main)
git tag v0.2.0
git push origin v0.2.0
```

That's it. The workflow:

1. Builds the Rust core for all 4 Android ABIs.
2. Assembles the debug APK.
3. Renames it to `SatsPrice-v0.2.0.apk`.
4. Creates a GitHub Release at `/releases/tag/v0.2.0` with auto-generated
   release notes from the commits since the previous tag.
5. Attaches the APK as a release asset.

Find the published release under [Releases](https://github.com/PeterXMR/satsprice/releases).

## Publishing a release manually (CI bypass)

If you need a release without going through CI — e.g. for a hotfix from a
machine that can build locally:

```sh
# Build all 4 ABIs locally
just build-android
cd app && ./gradlew :composeApp:assembleDebug && cd ..

# Stage the APK with a meaningful filename
cp app/composeApp/build/outputs/apk/debug/composeApp-debug.apk \
   /tmp/SatsPrice-v0.2.0.apk

# Create the release. Omit --prerelease for a real release.
gh release create v0.2.0 /tmp/SatsPrice-v0.2.0.apk \
    --title "v0.2.0" \
    --generate-notes
```

The CI workflow does exactly this, just with GitHub-hosted runners.

## What this build is NOT (yet)

The APK produced by the current workflow is **a debug build**. That's fine
for sideloading on your own devices and sharing with testers, but it isn't
production-grade for these reasons:

- **Debug-signed.** Signed with the Android debug keystore (auto-generated
  per machine / CI runner). A given install path is uninstallable when the
  signing certificate changes. Play Store will refuse it.
- **No R8 / ProGuard.** `app/composeApp/build.gradle.kts` sets
  `isMinifyEnabled = false` for the release variant with the comment
  *"R8 rules for UniFFI/JNA come in Phase 13"* — the keep-rules that
  prevent reflection-loaded native bridges from being stripped haven't been
  written. Enabling R8 today would break UniFFI at runtime.
- **`buildType` is `debug`.** Slower, larger, and includes assertions.

## Roadmap to production-quality releases

Roughly in order of dependency:

1. **Generate a release keystore.** `keytool -genkey -v -keystore satsprice-release.jks -alias satsprice ...`. Store the file + alias + passwords as GitHub repo secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, etc.).
2. **Add a `signingConfigs { release { ... } }` block** to [app/composeApp/build.gradle.kts](../app/composeApp/build.gradle.kts) that reads from `System.getenv()` so CI can supply the secrets without committing them.
3. **Write R8 keep-rules** for UniFFI's generated bindings and JNA's reflection-loaded native methods. Site: `app/composeApp/proguard-rules.pro` (new file).
4. **Flip `isMinifyEnabled = true`** on the release variant once R8 rules are in place. Verify the release APK still passes the same emulator smoke tests as debug.
5. **Update the workflow** to:
   - Build `assembleRelease` instead of `assembleDebug` when a tag is pushed.
   - Decode the keystore from `RELEASE_KEYSTORE_BASE64` into a file before the gradle step.
   - Provide signing env vars during `assembleRelease`.
6. **Match `applicationId`** when graduating to Play Store. Today the id is
   `price.sats` — confirm that's the intended production identifier before
   publishing, because once on Play Store it can't change without orphaning
   the app's reviews and installs.

Until those land, every release is effectively a "debug release" — fine for
demos and direct distribution, not for the store.

## CI architecture quick reference

The single workflow file ([.github/workflows/android.yml](../.github/workflows/android.yml)) does three things based on the trigger:

```
on push to main          → build APK, save 14-day artifact
on PR against main       → build APK, save 14-day artifact (pre-merge gate)
on push of v* tag        → build APK + publish GitHub Release with the APK
```

Caching: `Swatinem/rust-cache@v2` (cargo registry + `core/target`),
`gradle/actions/setup-gradle@v3` (`~/.gradle`), and `taiki-e/install-action`
fetches a prebuilt `cargo-ndk` binary instead of compiling it from source
(saves ~3 minutes per run).

Concurrency: in-progress runs on the same branch are cancelled on new
commits, but tag-triggered runs are **never** cancelled — every tag must
produce exactly one release.
