# AGENTS.md — TermuxLite

## Mandatory: post-write bug check

After writing or editing ANY code (logic or implementation), always run a
self-review pass before declaring done. Check at minimum:

1. **Compile sanity** — invalid lambda params, wrong types, unresolved
   references, shadowed names.
2. **Logic holes** — null/empty inputs, boundary conditions, off-by-one,
   impossible branches, leftover dead code from edits.
3. **Runtime env assumptions** — Android app sandbox limits (no /apex
   readdir, /proc visibility, scoped storage) must be verified empirically,
   not assumed.
4. **Behavioral proof** — simulate the logic against real device data when
   possible (e.g. diff against a known-good dump) before committing.

Report findings ("verified safe" vs "bug fixed") explicitly in your summary.

## Project notes

- applicationId is `com.termux` (namespace `com.termux.lite`) — installing
  updates over official Termux in place.
- AndroidEnv exports BOOTCLASSPATH/DEX2OATBOOTCLASSPATH/ANDROID_* into every
  terminal session so app_process tools (Shizuku rish) work. Three-tier
  fallback: /proc/self/environ → module probing → core-only.
- Commits follow Conventional Commits, lowercase type prefix (`fix:`,
  `feat:`). CI builds APKs; no local SDK required.
