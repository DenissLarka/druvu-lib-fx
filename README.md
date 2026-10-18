# druvu-lib-fx

[![CI](https://github.com/DenissLarka/druvu-lib-fx/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/DenissLarka/druvu-lib-fx/actions/workflows/ci.yml)
[![Maven GitHub Publish](https://github.com/DenissLarka/druvu-lib-fx/actions/workflows/maven-github-publish.yml/badge.svg)](https://github.com/DenissLarka/druvu-lib-fx/actions/workflows/maven-github-publish.yml)
[![Release](https://img.shields.io/github/v/release/DenissLarka/druvu-lib-fx?label=GitHub%20Packages&color=blue)](https://github.com/DenissLarka/druvu-lib-fx/packages)
![Java](https://img.shields.io/badge/Java-27%20(FX)-blue)
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
| `dock`   | Docking layout, vendored from [DockFX](https://github.com/RobertBColton/DockFX) (MPL-2.0) and made jlink-clean — see [NOTICE.md](NOTICE.md); `DockLayout` + `DockLayoutPersistence` keep the workspace across runs and put a hidden panel back where it was |
| `status` | Status-bar model (`StatusBarModel`)                                                           |
| `auth`   | Self-contained sign-in control with a pluggable backend (`LoginPane`, `Authenticator`)        |
| `prefs`  | Per-app home directory, properties-backed preferences, window geometry that reopens where it was — or on a screen, when the display it was on is gone (`AppHome`, `Prefs`, `WindowGeometry`) |
| `notify` | Toast notifications — info/success/warning/error, auto-dismiss                                |
| `theme`  | Theme menu over [AtlantaFX](https://github.com/mkpaz/atlantafx) (MIT) — nine themes, AtlantaFX's seven plus druvu light and dark, persisted; kit widgets follow the current one (`FxTheme`, `ThemeManager`, `KitStyles`) |
| `os`     | macOS application menu (About / Settings / Quit) and Finder open-file events, on plain `java.awt.Desktop` — declines quietly elsewhere (`DesktopHooks`) |
| `util`   | FX threading helpers (`FxThreads`) and a debounce for save-on-change (`Debounce`) — every toolkit API states its threading contract in its terms |

## Quick start

A complete app: it keeps its theme and its window between runs, does its work off the FX thread
and reports back with a toast.

```java
// src/main/java/module-info.java
module com.example.hello {
    requires com.druvu.lib.fx; // brings javafx.controls and atlantafx.base with it
    exports com.example.hello to javafx.graphics; // JavaFX instantiates the Application subclass
}
```

```java
// src/main/java/com/example/hello/HelloApp.java
package com.example.hello;

import com.druvu.lib.fx.exec.FxExec;
import com.druvu.lib.fx.notify.Notifications;
import com.druvu.lib.fx.prefs.AppHome;
import com.druvu.lib.fx.prefs.Prefs;
import com.druvu.lib.fx.prefs.WindowGeometry;
import com.druvu.lib.fx.theme.ThemeManager;
import com.druvu.lib.fx.util.FxThreads;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class HelloApp extends Application {

    private final FxExec exec = new FxExec();

    @Override
    public void start(Stage stage) {
        Prefs prefs = Prefs.in(AppHome.of("hello")); // ~/.druvu.com/hello/preferences.properties
        new ThemeManager(prefs).applyStored(); // first, before any toolkit widget exists
        Notifications toasts = new Notifications(stage);

        Button work = new Button("Do some work");
        work.setOnAction(event -> exec.run("Working", () -> Thread.sleep(1000)) // off the FX thread
                .thenRunAsync(() -> toasts.success("Done"), FxThreads.fxExecutor()));

        stage.setTitle("Hello");
        stage.setScene(new Scene(new StackPane(work), 400, 300));
        WindowGeometry.install(stage, prefs); // reopens where it was, always within reach
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
```

It needs **JDK 27 with JavaFX bundled** (e.g. Azul Zulu FX 27) and compiles at release 27
(`<maven.compiler.release>27</maven.compiler.release>`). JavaFX comes from the JDK, so there is no
JavaFX dependency to add — only the launcher plugin; run it with `mvn compile javafx:run`:

```xml
<plugin>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-maven-plugin</artifactId>
    <version>0.0.8</version>
    <configuration>
        <mainClass>com.example.hello/com.example.hello.HelloApp</mainClass>
    </configuration>
</plugin>
```

## Using it

**No compatibility promises:** this library exists for druvu products, and its API moves when
they need it to — a major version marks every break. Issues are welcome; support is best-effort.

## Installation (GitHub Packages)

The toolkit is published to **GitHub Packages** as `com.druvu:druvu-lib-fx`. Use **2.0.0 or newer**.
GitHub Packages needs authentication even for public packages, so Maven needs a token.

**1. Generate a GitHub Personal Access Token:**

Create a **classic** token with the single `read:packages` scope — nothing more is needed to
consume a public package.
[This link](https://github.com/settings/tokens/new?scopes=read:packages&description=maven-read-packages)
opens the form with the right type and scope pre-selected.

> Note: it must be a *classic* token. GitHub's token page defaults to the newer fine-grained
> tokens, which the GitHub Packages Maven registry does not accept — the symptom is an
> unexplained `401 Unauthorized` from `maven.pkg.github.com`.

**2. Add the server to `~/.m2/settings.xml`:**

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_TOKEN</password>
    </server>
  </servers>
</settings>
```

**3. Add the repository and the dependency to your project's `pom.xml`:**

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/DenissLarka/druvu-lib-fx</url>
    </repository>
</repositories>
```

```xml
<dependency>
    <groupId>com.druvu</groupId>
    <artifactId>druvu-lib-fx</artifactId>
    <version>2.0.0</version>
</dependency>
```

## Build

Requires **JDK 27 with JavaFX bundled** (e.g. Azul Zulu FX). JavaFX modules come from the JDK —
there are deliberately no `org.openjfx` dependencies, because druvu apps ship their own JDK via
the install pipeline.

```
mvn verify                                  # build + tests + quality gates
mvn -pl druvu-lib-fx-example javafx:run     # launch the Market Watch showcase
```

## License

[Apache-2.0](LICENSE). The `com.druvu.lib.fx.dock` package is vendored from DockFX under
MPL-2.0, with per-file headers kept — provenance and modifications in [NOTICE.md](NOTICE.md).

---

Part of [druvu](https://druvu.com) — modular Java libraries and JVM tooling, built to last.
