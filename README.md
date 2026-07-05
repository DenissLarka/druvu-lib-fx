# druvu-lib-fx

JavaFX application toolkit for druvu desktop apps — **a library, not a framework**: the app owns
`Application`, `Stage`, `Scene` and its layout; every toolkit capability is opt-in. Nothing here
requires implementing a toolkit interface to start an app.

## Modules

| Module                | What                                                                                   |
|-----------------------|----------------------------------------------------------------------------------------|
| `druvu-lib-fx`        | The toolkit (JPMS module `com.druvu.lib.fx`): `bus`, `exec`, `status`, `auth`, `nav`, `prefs`, `notify`, `util` |
| `druvu-lib-fx-example`| **Market Watch** — showcase app exercising every toolkit API. Never deployed.            |

## Build

Requires **JDK 25 with JavaFX bundled** (e.g. Azul Zulu FX). JavaFX modules come from the JDK —
there are deliberately no `org.openjfx` dependencies, because druvu apps ship their own JDK via
the install pipeline.

```
mvn verify                                  # build + tests + quality gates
mvn -pl druvu-lib-fx-example javafx:run     # launch the Market Watch showcase
```

## Status

Private-first; may be opened later. Threading contracts are stated on every API in terms of
`com.druvu.lib.fx.util.FxThreads`.
