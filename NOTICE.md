# Third-party notices — druvu-lib-fx

This product bundles third-party source and assets. Their licenses and provenance are recorded here.

## DockFX (docking layout) — Mozilla Public License 2.0

The package `com.druvu.lib.fx.dock` in the `druvu-lib-fx` module is vendored (copied in, not
depended on) from **DockFX** by Robert B. Colton — <https://github.com/RobertBColton/DockFX>.

- **License:** Mozilla Public License, v. 2.0 (MPL-2.0). Upstream relicensed to MPL-2.0 in 2020;
  every vendored `.java` file carries the MPL-2.0 per-file header. A copy of the MPL is at
  <https://mozilla.org/MPL/2.0/>.
- **Why vendored, not a dependency:** upstream is dead (no releases in years) and ships no
  `module-info`, so as a jar it resolves as an *automatic module* — which `jlink` refuses. Copying
  the five sources into a named module keeps the druvu app runtimes jlink-clean. MPL-2.0 is
  file-level copyleft, satisfied by keeping the headers and this notice.

### Vendored files and modifications

| File | Change from upstream |
|------|----------------------|
| `DockPos.java` | Repackaged `org.dockfx` → `com.druvu.lib.fx.dock` only. |
| `DockTitleBar.java` | Repackaged; the title-bar drag-to-float now checks `DockNode.isFloatable()`, so `setFloatable(false)` genuinely pins a node (upstream ignored the flag, letting a non-closable node be dragged out with no way to re-dock it). |
| `DockEvent.java` | Repackaged; inlined the JDK-internal `com.sun.javafx.scene.input.InputEventUtils.recomputeCoordinates(pick, null)` as a private helper to drop the `com.sun.*` dependency (Java 25 / jlink); marked the non-serializable `pickResult`/`contents` fields `transient`. |
| `DockPane.java` | Repackaged; replaced the `com.sun.javafx.css.StyleManager` user-agent-stylesheet route with per-scene-root `getStylesheets()` adds (`defaultStylesheet()`); made the static `dockPanes` registry `final`. |
| `DockNode.java` | Repackaged; a floating node's own `Scene` now receives the dock stylesheet via `getStylesheets()` (there is no user-agent stylesheet anymore). |

The two `com.sun.*` internal APIs were the *only* Java 25 breaks; the behaviour of both was preserved.

## DockFX default stylesheet and dock-indicator icons

Bundled at `com/druvu/lib/fx/dock/` in `druvu-lib-fx`:

- **`default.css`** — from DockFX. The upstream file still carried a stale **GPL-3.0** header from
  2015 even though the project as a whole is MPL-2.0; the header has been aligned to MPL-2.0 here.
  The `.dock-title-label` `-fx-graphic` rule was dropped — it referenced `docknode.png`, a
  demo-only asset that is **not** vendored.
- **Dock-indicator PNGs** (`bottom/center/close/left/maximize/restore/right/top.png`) — introduced
  upstream by the commit *"Cleanup Fix line endings and switch to free icons."* (2015-08-23),
  i.e. redistributable "free icons". `demo/docknode.png` is intentionally excluded.

If druvu-lib-fx is ever open-sourced, re-confirm the free-icon provenance before redistribution.
