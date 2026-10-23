# Agent instructions

- Before version, build identity, CI or Release work, read and follow [VERSIONING.md](VERSIONING.md), the authoritative policy. Versions are pure numeric `X.Y.Z`; the shared new starting point is `0.1.0`, then plugins advance independently. Never add rc/local/ui/date suffixes.
- Keep `master` the default integration branch and preserve feature branches/user changes. `gradle.properties:pluginVersion` is the only plugin-version source; never overwrite published tags, Releases or assets. Historical Release cleanup needs explicit scoped authorization; never delete or move Git tags without it.
- Publish only successful version-tag CI artifacts. Record real critical acceptance and actual limits; green tests alone do not establish real-account usability. Early `0.x` may be labeled early/prerelease without changing its numeric version.
- This repository is `dev-reader`; preserve plugin ID `io.github.ataraxyadong.devreader`, original author/license attribution and actual API endpoints. Do not modify other plugin repositories.

- Release title format is `dev-reader X.Y.Z`. Use the four-section public release template; put unverified acceptance under known limitations. Authorized editorial changes must preserve existing tags/assets and must not repackage a published version.
