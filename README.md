# druvu-lib-fx

[![Maven GitHub Publish](https://github.com/DenissLarka/druvu-lib-fx/actions/workflows/maven-github-publish.yml/badge.svg)](https://github.com/DenissLarka/druvu-lib-fx/actions/workflows/maven-github-publish.yml)
![Java](https://img.shields.io/badge/Java-25%20(FX)-blue)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

JavaFX application toolkit for [druvu](https://druvu.com) desktop apps — **a library, not a
framework**: the app owns `Application`, `Stage`, `Scene` and its layout; every toolkit capability
is opt-in. Nothing here requires implementing a toolkit interface to start an app.

## Modules

| Module                | What                                                                          |
|-----------------------|-------------------------------------------------------------------------------|
| `druvu-lib-fx`        | The toolkit (JPMS module `com.druvu.lib.fx`)                                  |
| `druvu-lib-fx-example`| **Market Watch** — showcase app exercising every toolkit API. Never deployed. |

## What's in the toolkit

| Package  | Capability                                                                                    |
|----------|-----------------------------------------------------------------------------------------------|
| `bus`    | In-app event bus with selectable delivery — caller, FX thread, async (`FxBus`)                |
| `exec`   | Background task execution with lifecycle events (`FxExec`, `TaskEvent`)                       |
| `dock`   | Docking layout, vendored from [DockFX](https://github.com/RobertBColton/DockFX) (MPL-2.0) and made Java 25 / jlink-clean — see [NOTICE.md](NOTICE.md) |
| `status` | Status-bar model (`StatusBarModel`)                                                           |
| `auth`   | Self-contained sign-in control with a pluggable backend (`LoginPane`, `Authenticator`)        |
| `prefs`  | Per-app home directory, properties-backed preferences, window-geometry persistence           |
| `notify` | Toast notifications — info/success/warning/error, auto-dismiss                                |
| `util`   | FX threading helpers (`FxThreads`) — every toolkit API states its threading contract in its terms |

## Build

Requires **JDK 25 with JavaFX bundled** (e.g. Azul Zulu FX). JavaFX modules come from the JDK —
there are deliberately no `org.openjfx` dependencies, because druvu apps ship their own JDK via
the install pipeline.

```
mvn verify                                  # build + tests + quality gates
mvn -pl druvu-lib-fx-example javafx:run     # launch the Market Watch showcase
```

## Using it

Not published to Maven Central — druvu apps consume it in-reactor or from a local `mvn install`;
to try it, build it yourself. **No compatibility promises yet:** this library exists for druvu
products, and its API moves when they need it to. Issues are welcome; support is best-effort.

## License

[Apache-2.0](LICENSE). The `com.druvu.lib.fx.dock` package is vendored from DockFX under
MPL-2.0, with per-file headers kept — provenance and modifications in [NOTICE.md](NOTICE.md).

---

Part of [druvu](https://druvu.com) — modular Java libraries and JVM tooling, built to last.
