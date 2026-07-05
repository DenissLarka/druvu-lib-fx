/**
 * @file DockPos.java
 * @brief Enumeration of dock area alignment constants.
 *
 * @section License
 *          This Source Code Form is subject to the terms of the Mozilla Public
 *          License, v. 2.0. If a copy of the MPL was not distributed with this
 *          file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Adopted into druvu-lib-fx from DockFX (https://github.com/RobertBColton/DockFX), MPL-2.0.
 * Changes: repackaged org.dockfx -> com.druvu.lib.fx.dock. See NOTICE.md.
 **/

package com.druvu.lib.fx.dock;

/**
 * DockPos
 *
 * @since DockFX 0.1
 */
public enum DockPos {
  /**
   * Dock to the center by stacking inside a TabPane. This should be considered the equivalent of
   * null.
   */
  CENTER,

  /**
   * Dock to the left using a splitter.
   */
  LEFT,

  /**
   * Dock to the right using a splitter.
   */
  RIGHT,

  /**
   * Dock to the top using a splitter.
   */
  TOP,

  /**
   * Dock to the bottom using a splitter.
   */
  BOTTOM;
}
