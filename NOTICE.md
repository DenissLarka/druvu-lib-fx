# Third-party notices — druvu-lib-fx

This product bundles third-party source and assets. Their licenses and provenance are recorded here.

**Licensing summary:** druvu-lib-fx is licensed under the **Apache License 2.0** (see `LICENSE`), *except* the
vendored `com.druvu.lib.fx.dock` package and its resources under `com/druvu/lib/fx/dock/`, which are under the
**Mozilla Public License 2.0** (see `licenses/MPL-2.0.txt`). Both license texts are bundled in the published
jar under `META-INF/`.

## DockFX (docking layout) — Mozilla Public License 2.0

The package `com.druvu.lib.fx.dock` in the `druvu-lib-fx` module is vendored (copied in, not
depended on) from **DockFX** by Robert B. Colton — <https://github.com/RobertBColton/DockFX>.

- **License:** Mozilla Public License, v. 2.0 (MPL-2.0). Upstream relicensed to MPL-2.0 in 2020;
  every vendored `.java` file carries the MPL-2.0 per-file header. The full MPL-2.0 text is in
  `licenses/MPL-2.0.txt` (bundled in the jar at `META-INF/licenses/MPL-2.0.txt`); also at
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
| `DockPane.java` | Repackaged; replaced the `com.sun.javafx.css.StyleManager` user-agent-stylesheet route with per-scene-root `getStylesheets()` adds (`defaultStylesheet()`); made the static `dockPanes` registry `final`. **2026-09-26, layout as a value:** `dockLayout()` / `apply(DockLayout, nodes)` snapshot and rebuild the split tree (see the new, non-vendored `DockLayout`); `redock(node, side)` puts a hidden node back beside the neighbours it left, from a placement remembered on `undock` (or told via `rememberPlacement`); `layoutRevisionProperty()` bumps on every dock/undock/apply/divider move. Three upstream defects fixed on the way: `undock` now folds the single-child `SplitPane` it left behind (the tree grew one level per hide/show); docking `CENTER` into an occupied pane throws instead of silently dropping the node; and the preferred-size divider math no longer divides by zero (a `NaN` divider when nothing had a preferred size yet) - an unsized pair splits in half. **Dividers keep their shares across resizes** (package-private `SplitShares`, attached to every split by the pane): the skin's round-robin resize distribution walked every divider toward the middle, so a restored layout drifted a step per start. |
| `DockNode.java` | Repackaged; a floating node's own `Scene` now receives the dock stylesheet via `getStylesheets()` (there is no user-agent stylesheet anymore) **and inherits the stylesheets of the scene it detached from and of its `DockPane`**, so application styling (fonts, semantic colours, density) survives a detach — upstream floats rendered unstyled. **2026-09-26:** the private `dockImpl` became package-private `attach(DockPane)` and gained a `detach()` twin, so `DockPane.apply` can move nodes between split trees without going through `dock()`; both `dock(..)` overloads ask the pane `requireDockable(pos)` first, so a refused `CENTER` leaves the node unmarked. |

The two `com.sun.*` internal APIs were the *only* Java 25 breaks; the behaviour of both was preserved.

## DockFX default stylesheet and dock-indicator icons

Bundled at `com/druvu/lib/fx/dock/` in `druvu-lib-fx`:

- **`default.css`** — from DockFX. The upstream file still carried a stale **GPL-3.0** header from
  2015 even though the project as a whole is MPL-2.0; the header has been aligned to MPL-2.0 here.
  The `.dock-title-label` `-fx-graphic` rule was dropped — it referenced `docknode.png`, a
  demo-only asset that is **not** vendored. Every Modena-only colour lookup was replaced with its
  AtlantaFX equivalent, so the dock follows the theme applied via `ThemeManager` instead of
  rendering unstyled under it: `.dock-node` / `.dock-title-bar` (`-fx-background`,
  `-fx-outer-border` → `-color-bg-default`, `-color-bg-subtle`, `-color-border-default`),
  `.dock-title-label` (`-fx-text-base-color` → `-color-fg-default`) and `.dock-area-indicator`
  (`-fx-selection-bar` → `-color-accent-emphasis`). **2026-08-22: the title-bar button icons
  (`close/maximize/restore.png`, fixed-colour bitmaps) were replaced with `-fx-shape` glyph paths
  from Phosphor Icons (see below), painted with theme colours; the three PNGs were removed.**
- **Dock-indicator PNGs — none remain (2026-08-22).** Upstream's icon bitmaps (title-bar
  `close/maximize/restore.png` and position indicators `top/right/bottom/left/center.png`,
  introduced by the 2015-08-23 *"switch to free icons"* commit) were all replaced with Phosphor
  glyph shapes (see below) and deleted; `demo/docknode.png` was never vendored. The vendored dock
  resources are now `default.css` alone.

## Phosphor Icons (dock glyphs) — MIT

The `-fx-shape` path data for the dock buttons in `com/druvu/lib/fx/dock/default.css` — title
bar: `x` = close, `browser` = maximize, `browsers` = detach/restore; drag position indicators:
`align-top` / `align-right` / `align-bottom` / `align-left` / `square` = center (all bold
weight) — is copied verbatim from
[Phosphor Icons](https://github.com/phosphor-icons/core), Copyright (c) 2023 Phosphor Icons,
MIT License.

These dock-indicator icons are redistributed as part of the vendored MPL-2.0 DockFX sources; the demo-only
`demo/docknode.png` is intentionally excluded.

## AtlantaFX theme SCSS (druvu themes) — MIT

The bundled stylesheets `com/druvu/lib/fx/theme/druvu-dark.css` and `druvu-light.css` are **compiled
at build time from AtlantaFX's SCSS sources** (`io.github.mkpaz:atlantafx-styles`, MIT,
<https://github.com/mkpaz/atlantafx>), configured with the druvu colour ramps
(`src/main/scss/druvu-*.scss`, Apache-2.0). The generated CSS is therefore a derivative of
MIT-licensed material; this notice records that provenance. The SCSS sources themselves are not
redistributed — the build unpacks them from the published `atlantafx-styles` artifact at the same
version as the `atlantafx-base` runtime dependency.
