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

| Module                 | What                                                                          |
|------------------------|-------------------------------------------------------------------------------|
| `druvu-lib-fx`         | The toolkit (JPMS module `com.druvu.lib.fx`)                                  |
| `druvu-lib-fx-updater` | Self-update for an installed app, no JavaFX inside (`com.druvu.lib.fx.updater`); the toolkit brings it along |
| `druvu-lib-fx-example` | **Market Watch** — showcase app exercising every toolkit API. Never deployed. |

## What's in the toolkit

| Package  | Capability                                                                                    |
|----------|-----------------------------------------------------------------------------------------------|
| `bus`    | In-app event bus with selectable delivery — caller, FX thread, async (`FxBus`)                |
| `exec`   | Background task execution with lifecycle and progress events (`FxExec`, `TaskEvent`, `TaskProgress`) |
| `dock`   | Docking layout, vendored from [DockFX](https://github.com/RobertBColton/DockFX) (MPL-2.0) and made jlink-clean — see [NOTICE.md](NOTICE.md); `DockLayout` + `DockLayoutPersistence` keep the workspace across runs and put a hidden panel back where it was |
| `status` | Status-bar model: running tasks, the last activity, the progress of a task that reports it (`StatusBarModel`) |
| `auth`   | Self-contained sign-in control with a pluggable backend (`LoginPane`, `Authenticator`)        |
| `prefs`  | Per-app home directory, properties-backed preferences, window geometry that reopens where it was — or on a screen, when the display it was on is gone (`AppHome`, `Prefs`, `WindowGeometry`) |
| `notify` | Toast notifications — info/success/warning/error, auto-dismiss                                |
| `theme`  | Theme menu over [AtlantaFX](https://github.com/mkpaz/atlantafx) (MIT) — nine themes, AtlantaFX's seven plus druvu light and dark, persisted; kit widgets follow the current one (`FxTheme`, `ThemeManager`, `KitStyles`) |
| `os`     | macOS application menu (About / Settings / Quit) and Finder open-file events, on plain `java.awt.Desktop` — declines quietly elsewhere (`DesktopHooks`); logging set up in one call, the output in the app's log folder when installed (`AppLogs`) |
| `update` | Self-update in one call — the offer with the release notes, the download in the status bar, the SHA-256 check, the install, the restart; the user's choices in the preferences (`Updates`) |
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

JavaFX comes from the JDK Maven runs on, a JavaFX-bundled build such as Azul Zulu FX (see Build): the
plugin and the dependency add no `org.openjfx` artifacts.

## Self-update

An installed app checks a release source, offers what is newer, downloads it with progress in the
status bar, verifies the SHA-256 the release publishes, hands the package to the operating system
and offers the restart. One builder, once the stage is showing; the Help menu gets the manual check.
`requires com.druvu.lib.fx` is enough: the kit requires the updater module transitively.

```java
import com.druvu.lib.fx.update.Updates;
import com.druvu.lib.fx.updater.github.GitHubReleases;

// in start(), after stage.show()
Updates updates = Updates.builder()
        .appName("Hello")                                       // the name the dialogs use
        .app(HelloApp.class)                                    // its module's version is the running version
        .source(GitHubReleases.of("your-github-user", "hello")) // the newest release and its assets
        .owner(stage)
        .prefs(prefs)
        .exec(exec)                                             // the check runs as a task: the status bar shows it
        .build();
updates.checkAtStart();                                         // quiet unless an update is there to offer
helpMenu.getItems().add(updates.checkForUpdatesMenuItem());     // "Check for Updates..."
```

The running version is the app module's, so the module must be compiled with one:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <compilerArgs>
            <arg>--module-version=${project.version}</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

The download shows wherever the app shows its tasks. `StatusBarModel` follows the app's `FxExec`
through the bus; a status strip binds to it, and the bar appears while a task reports progress:

```java
FxBus bus = new FxBus();
FxExec exec = new FxExec(bus);                  // publishes every task's events on the bus
StatusBarModel status = new StatusBarModel(bus);

Label message = new Label();
message.textProperty().bind(status.messageProperty());
ProgressBar bar = new ProgressBar();
bar.progressProperty().bind(status.progressProperty());
bar.visibleProperty().bind(status.progressingProperty());
HBox strip = new HBox(16, message, bar);        // the app's status strip, at the bottom of its layout
```

Any task reports the same way: `exec.run("import", progress -> { ...; progress.report(0.5); ... })`.

What the user decides stays in the app's preferences: `update.check` (the automatic check, on by
default; `updates.checkAtStartProperty()` binds a check box to it) and `update.skip` (a version never
to offer again).

The release source is a GitHub repository's releases: the tag is the version (`v1.2.0` or `1.2.0`),
the assets are the installer packages — `.msix`, `.dmg`, `.AppImage` — each with the SHA-256
GitHub publishes per asset. An asset without a digest is never installed. A public repository's
releases need no token.

A development run is not an installed copy, so `updates.available()` is false and nothing happens;
the Help-menu item says so. Installed copies are recognised by the installer's layout: the AppImage
runtime's `APPIMAGE` variable, the `.app` bundle around the Java runtime, Windows' `WindowsApps`
folder. For a packaged build that has no release yet, `-Ddruvu.updater.release=<URL>` (or the
environment variable `DRUVU_UPDATER_RELEASE`, for an installed app started from a shell) points the
source at a JSON file of the API's shape (`file:` works); `-Ddruvu.updater.off=true` (or
`DRUVU_UPDATER_OFF=true`) switches updates off for a run. The Market Watch showcase runs the whole conversation on a pretend installation:
`mvn -pl druvu-lib-fx-example javafx:run -Djavafx.args=--demo-updates`.

An installed app has no console; its output - the updater's lines included - goes to the app's log
folder when `main` starts with `AppLogs.setUp` (Logging, below).

The updater module (`com.druvu.lib.fx.updater`) has no JavaFX in it: `Updater`, `UpdateHost`,
`UpdateSettings` and `Installation` are the contract, so a console or another toolkit can host the
same conversation.

## Logging

One API, the JDK's: the kit and its updater log through `System.Logger`, and so can the app. The
backend is the JDK's own, `java.util.logging`; the kit requires that module, so every runtime image
built on the kit carries it, and no logging dependency ships. `main` sets it up in one call, before
anything logs:

```java
import com.druvu.lib.fx.os.AppLogs;
import com.druvu.lib.fx.prefs.AppHome;

public static void main(String[] args) {
    AppLogs.setUp(AppHome.of("hello"));
    launch(args);
}
```

One line per record, the stack trace under it when there is one:

```text
2026-10-31 14:02:03.123 INFO    com.druvu.lib.fx.update.Updates - Checking for updates
```

A development run keeps its console. An installed copy has none, so its output - the updater's lines
included - goes to `~/.druvu.com/hello/logs/hello-<date>.log`. `-Ddruvu.log=debug` sets the level
(any `System.Logger.Level` name; `INFO` by default). Below `INFO` it opens up druvu's own loggers
(`com.druvu.*`) only: the JDK's and the dependencies' stay at `INFO`, or a debug run would list every
trusted certificate as TLS starts. `-Ddruvu.log.file=true` sends a development run to the file too,
and a run that configures the backend itself (`-Djava.util.logging.config.file=...`) keeps its
configuration.

A dependency that logs through another API brings its own bridge to the JDK backend, at runtime
scope: `org.slf4j:slf4j-jdk14` for SLF4J, `org.apache.logging.log4j:log4j-to-jul` for the Log4j API.
Without its bridge, a facade prints in its own format to the console, or complains that it found no
provider. A dependency that brings an older `slf4j-api` wins over the bridge's otherwise, so exclude
the API from that dependency; a managed version does not reach a packaging pom downstream.

## Using it

**No compatibility promises:** this library exists for druvu products, and its API moves when
they need it to — a major version marks every break. Issues are welcome; support is best-effort.

## Installation (GitHub Packages)

The toolkit is published to **GitHub Packages** as `com.druvu:druvu-lib-fx`; it brings `druvu-lib-fx-updater` with it. Use **2.1.0 or newer**.
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
    <version>2.1.0</version>
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
