/**
 * @file DockPane.java
 * @brief Class implementing a generic dock pane for the layout of dock nodes.
 *
 * @section License
 *          This Source Code Form is subject to the terms of the Mozilla Public
 *          License, v. 2.0. If a copy of the MPL was not distributed with this
 *          file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Adopted into druvu-lib-fx from DockFX (https://github.com/RobertBColton/DockFX), MPL-2.0.
 * Changes: repackaged org.dockfx -> com.druvu.lib.fx.dock; replaced the com.sun.* StyleManager
 * user-agent-stylesheet route with per-root getStylesheets() adds (jlink-clean on Java 25);
 * made the static dockPanes registry final. See NOTICE.md.
 **/

package com.druvu.lib.fx.dock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Popup;
import javafx.util.Duration;

/**
 * Base class for a dock pane that provides the layout of the dock nodes. Stacking the dock nodes to
 * the center in a TabPane will be added in a future release. For now the DockPane uses the relative
 * sizes of the dock nodes and lays them out in a tree of SplitPanes.
 * 
 * @since DockFX 0.1
 */
public class DockPane extends StackPane implements EventHandler<DockEvent> {
  /**
   * Package-private internal list of all DockPanes for event mouse picking.
   */
  static final List<DockPane> dockPanes = new ArrayList<DockPane>();

  /**
   * The current root node of this dock pane's layout.
   */
  private Node root;

  /**
   * Where hidden nodes go back to, by id (druvu addition).
   */
  private final Map<String, DockLayout.Placement> hiddenPlacements =
      new HashMap<String, DockLayout.Placement>();
  /**
   * Bumped on every change of the workspace shape (druvu addition).
   */
  private final ReadOnlyIntegerWrapper layoutRevision =
      new ReadOnlyIntegerWrapper(this, "layoutRevision", 0);

  /**
   * Whether a DOCK_ENTER event has been received by this dock pane since the last DOCK_EXIT event
   * was received.
   */
  private boolean receivedEnter = false;

  /**
   * The current node in this dock pane that we may be dragging over.
   */
  private Node dockNodeDrag;
  /**
   * The docking area of the current dock indicator button if any is selected. This is either the
   * root or equal to dock node drag.
   */
  private Node dockAreaDrag;
  /**
   * The docking position of the current dock indicator button if any is selected.
   */
  private DockPos dockPosDrag;

  /**
   * The docking area shape with a dotted animated border on the indicator overlay popup.
   */
  private Rectangle dockAreaIndicator;
  /**
   * The timeline used to animate the borer of the docking area indicator shape. Because JavaFX has
   * no CSS styling for timelines/animations yet we will make this private and offer an accessor for
   * the user to programmatically modify the animation or disable it.
   */
  private Timeline dockAreaStrokeTimeline;
  /**
   * The popup used to display the root dock indicator buttons and the docking area indicator.
   */
  private Popup dockIndicatorOverlay;

  /**
   * The grid pane used to lay out the local dock indicator buttons. This is the grid used to lay
   * out the buttons in the circular indicator.
   */
  private GridPane dockPosIndicator;
  /**
   * The popup used to display the local dock indicator buttons. This allows these indicator buttons
   * to be displayed outside the window of this dock pane.
   */
  private Popup dockIndicatorPopup;

  /**
   * Base class for a dock indicator button that allows it to be displayed during a dock event and
   * continue to receive input.
   * 
   * @since DockFX 0.1
   */
  public class DockPosButton extends Button {
    /**
     * Whether this dock indicator button is used for docking a node relative to the root of the
     * dock pane.
     */
    private boolean dockRoot = true;
    /**
     * The docking position indicated by this button.
     */
    private DockPos dockPos = DockPos.CENTER;

    /**
     * Creates a new dock indicator button.
     */
    public DockPosButton(boolean dockRoot, DockPos dockPos) {
      super();
      this.dockRoot = dockRoot;
      this.dockPos = dockPos;
    }

    /**
     * Whether this dock indicator button is used for docking a node relative to the root of the
     * dock pane.
     * 
     * @param dockRoot Whether this indicator button is used for docking a node relative to the root
     *        of the dock pane.
     */
    public final void setDockRoot(boolean dockRoot) {
      this.dockRoot = dockRoot;
    }

    /**
     * The docking position indicated by this button.
     * 
     * @param dockPos The docking position indicated by this button.
     */
    public final void setDockPos(DockPos dockPos) {
      this.dockPos = dockPos;
    }

    /**
     * The docking position indicated by this button.
     * 
     * @return The docking position indicated by this button.
     */
    public final DockPos getDockPos() {
      return dockPos;
    }

    /**
     * Whether this dock indicator button is used for docking a node relative to the root of the
     * dock pane.
     * 
     * @return Whether this indicator button is used for docking a node relative to the root of the
     *         dock pane.
     */
    public final boolean isDockRoot() {
      return dockRoot;
    }
  }

  /**
   * A collection used to manage the indicator buttons and automate hit detection during DOCK_OVER
   * events.
   */
  private ObservableList<DockPosButton> dockPosButtons;


  /**
   * Creates a new DockPane adding event handlers for dock events and creating the indicator
   * overlays.
   */
  public DockPane() {
    super();
    DockPane.dockPanes.add(this);

    this.addEventHandler(DockEvent.ANY, this);
    this.addEventFilter(DockEvent.ANY, new EventHandler<DockEvent>() {

      @Override
      public void handle(DockEvent event) {

        if (event.getEventType() == DockEvent.DOCK_ENTER) {
          DockPane.this.receivedEnter = true;
        } else if (event.getEventType() == DockEvent.DOCK_OVER) {
          DockPane.this.dockNodeDrag = null;
        }
      }

    });

    dockIndicatorPopup = new Popup();
    dockIndicatorPopup.setAutoFix(false);

    dockIndicatorOverlay = new Popup();
    dockIndicatorOverlay.setAutoFix(false);

    StackPane dockRootPane = new StackPane();
    dockRootPane.prefWidthProperty().bind(this.widthProperty());
    dockRootPane.prefHeightProperty().bind(this.heightProperty());

    dockAreaIndicator = new Rectangle();
    dockAreaIndicator.setManaged(false);
    dockAreaIndicator.setMouseTransparent(true);

    dockAreaStrokeTimeline = new Timeline();
    dockAreaStrokeTimeline.setCycleCount(Timeline.INDEFINITE);
    // 12 is the cumulative offset of the stroke dash array in the default.css style sheet
    // RFE filed for CSS styled timelines/animations:
    // https://bugs.openjdk.java.net/browse/JDK-8133837
    KeyValue kv = new KeyValue(dockAreaIndicator.strokeDashOffsetProperty(), 12);
    KeyFrame kf = new KeyFrame(Duration.millis(500), kv);
    dockAreaStrokeTimeline.getKeyFrames().add(kf);
    dockAreaStrokeTimeline.play();

    DockPosButton dockCenter = new DockPosButton(false, DockPos.CENTER);
    dockCenter.getStyleClass().add("dock-center");

    DockPosButton dockTop = new DockPosButton(false, DockPos.TOP);
    dockTop.getStyleClass().add("dock-top");
    DockPosButton dockRight = new DockPosButton(false, DockPos.RIGHT);
    dockRight.getStyleClass().add("dock-right");
    DockPosButton dockBottom = new DockPosButton(false, DockPos.BOTTOM);
    dockBottom.getStyleClass().add("dock-bottom");
    DockPosButton dockLeft = new DockPosButton(false, DockPos.LEFT);
    dockLeft.getStyleClass().add("dock-left");

    DockPosButton dockTopRoot = new DockPosButton(true, DockPos.TOP);
    StackPane.setAlignment(dockTopRoot, Pos.TOP_CENTER);
    dockTopRoot.getStyleClass().add("dock-top-root");

    DockPosButton dockRightRoot = new DockPosButton(true, DockPos.RIGHT);
    StackPane.setAlignment(dockRightRoot, Pos.CENTER_RIGHT);
    dockRightRoot.getStyleClass().add("dock-right-root");

    DockPosButton dockBottomRoot = new DockPosButton(true, DockPos.BOTTOM);
    StackPane.setAlignment(dockBottomRoot, Pos.BOTTOM_CENTER);
    dockBottomRoot.getStyleClass().add("dock-bottom-root");

    DockPosButton dockLeftRoot = new DockPosButton(true, DockPos.LEFT);
    StackPane.setAlignment(dockLeftRoot, Pos.CENTER_LEFT);
    dockLeftRoot.getStyleClass().add("dock-left-root");

    // TODO: dockCenter goes first when tabs are added in a future version
    dockPosButtons = FXCollections.observableArrayList(dockTop, dockRight, dockBottom, dockLeft,
        dockTopRoot, dockRightRoot, dockBottomRoot, dockLeftRoot);

    dockPosIndicator = new GridPane();
    dockPosIndicator.add(dockTop, 1, 0);
    dockPosIndicator.add(dockRight, 2, 1);
    dockPosIndicator.add(dockBottom, 1, 2);
    dockPosIndicator.add(dockLeft, 0, 1);
    // dockPosIndicator.add(dockCenter, 1, 1);

    dockRootPane.getChildren().addAll(dockAreaIndicator, dockTopRoot, dockRightRoot, dockBottomRoot,
        dockLeftRoot);

    dockIndicatorOverlay.getContent().add(dockRootPane);
    dockIndicatorPopup.getContent().addAll(dockPosIndicator);

    this.getStyleClass().add("dock-pane");
    dockRootPane.getStyleClass().add("dock-root-pane");
    dockPosIndicator.getStyleClass().add("dock-pos-indicator");
    dockAreaIndicator.getStyleClass().add("dock-area-indicator");

    // Apply the dock stylesheet per scene-graph root. The two indicator overlays live in their own
    // Popup scenes, so styling the DockPane alone would not reach them.
    final String css = defaultStylesheet();
    this.getStylesheets().add(css);
    dockRootPane.getStylesheets().add(css);
    dockPosIndicator.getStylesheets().add(css);
  }

  /**
   * The Timeline used to animate the docking area indicator in the dock indicator overlay for this
   * dock pane.
   * 
   * @return The Timeline used to animate the docking area indicator in the dock indicator overlay
   *         for this dock pane.
   */
  public final Timeline getDockAreaStrokeTimeline() {
    return dockAreaStrokeTimeline;
  }

  /**
   * The dock's default stylesheet as an external-form URL suitable for
   * {@code getStylesheets().add(..)}. The dock applies this per scene-graph root (the dock pane
   * itself, the two indicator popups, and each floating dock node) rather than installing it as a
   * JavaFX user-agent stylesheet: the old {@code StyleManager} route used a {@code com.sun.*} API
   * that is not exported on Java 25 and would break jlink.
   *
   * @return the external-form URL of the dock's default stylesheet
   */
  public static String defaultStylesheet() {
    return DockPane.class.getResource("default.css").toExternalForm();
  }

  /**
   * A cache of all dock node event handlers that we have created for tracking the current docking
   * area.
   */
  private ObservableMap<Node, DockNodeEventHandler> dockNodeEventFilters =
      FXCollections.observableHashMap();

  /**
   * A wrapper to the type parameterized generic EventHandler that allows us to remove it from its
   * listener when the dock node becomes detached. It is specifically used to monitor which dock
   * node in this dock pane's layout we are currently dragging over.
   * 
   * @since DockFX 0.1
   */
  private class DockNodeEventHandler implements EventHandler<DockEvent> {
    /**
     * The node associated with this event handler that reports to the encapsulating dock pane.
     */
    private Node node = null;

    /**
     * Creates a default dock node event handler that will help this dock pane track the current
     * docking area.
     * 
     * @param node The node that is to listen for docking events and report to the encapsulating
     *        docking pane.
     */
    public DockNodeEventHandler(Node node) {
      this.node = node;
    }

    @Override
    public void handle(DockEvent event) {
      DockPane.this.dockNodeDrag = node;
    }
  }

  /**
   * Dock the node into this dock pane at the given docking position relative to the sibling in the
   * layout. This is used to relatively position the dock nodes to other nodes given their preferred
   * size.
   * 
   * @param node The node that is to be docked into this dock pane.
   * @param dockPos The docking position of the node relative to the sibling.
   * @param sibling The sibling of this node in the layout.
   */
  public void dock(Node node, DockPos dockPos, Node sibling) {
    requireDockable(dockPos);
    track(node);

    SplitPane split = (SplitPane) root;
    if (split == null || split.getItems().isEmpty()) {
      // An emptied root (everything undocked) takes the first node like a fresh pane does.
      if (split == null) {
        split = new SplitPane();
        root = split;
        this.getChildren().add(root);
      }
      split.getItems().add(node);
      watchDividers();
      layoutChanged();
      return;
    }

    // find the parent of the sibling
    if (sibling != null && sibling != root) {
      Stack<Parent> stack = new Stack<Parent>();
      stack.push((Parent) root);
      while (!stack.isEmpty()) {
        Parent parent = stack.pop();

        ObservableList<Node> children = parent.getChildrenUnmodifiable();

        if (parent instanceof SplitPane) {
          SplitPane splitPane = (SplitPane) parent;
          children = splitPane.getItems();
        }

        for (int i = 0; i < children.size(); i++) {
          if (children.get(i) == sibling) {
            split = (SplitPane) parent;
          } else if (children.get(i) instanceof Parent) {
            stack.push((Parent) children.get(i));
          }
        }
      }
    }

    Orientation requestedOrientation = (dockPos == DockPos.LEFT || dockPos == DockPos.RIGHT)
        ? Orientation.HORIZONTAL : Orientation.VERTICAL;

    // if the orientation is different then reparent the split pane
    if (split.getOrientation() != requestedOrientation) {
      if (split.getItems().size() > 1) {
        SplitPane splitPane = new SplitPane();
        if (split == root && sibling == root) {
          this.getChildren().set(this.getChildren().indexOf(root), splitPane);
          splitPane.getItems().add(split);
          root = splitPane;
        } else {
          split.getItems().set(split.getItems().indexOf(sibling), splitPane);
          splitPane.getItems().add(sibling);
        }

        split = splitPane;
      }
      split.setOrientation(requestedOrientation);
    }

    // finally dock the node to the correct split pane
    ObservableList<Node> splitItems = split.getItems();

    double magnitude = 0;

    if (splitItems.size() > 0) {
      if (split.getOrientation() == Orientation.HORIZONTAL) {
        for (Node splitItem : splitItems) {
          magnitude += splitItem.prefWidth(0);
        }
      } else {
        for (Node splitItem : splitItems) {
          magnitude += splitItem.prefHeight(0);
        }
      }
    }

    if (dockPos == DockPos.LEFT || dockPos == DockPos.TOP) {
      int relativeIndex = 0;
      if (sibling != null && sibling != root) {
        relativeIndex = splitItems.indexOf(sibling);
      }

      splitItems.add(relativeIndex, node);

      if (splitItems.size() > 1) {
        if (split.getOrientation() == Orientation.HORIZONTAL) {
          split.setDividerPosition(relativeIndex,
              share(node.prefWidth(0), magnitude));
        } else {
          split.setDividerPosition(relativeIndex,
              share(node.prefHeight(0), magnitude));
        }
      }
    } else if (dockPos == DockPos.RIGHT || dockPos == DockPos.BOTTOM) {
      int relativeIndex = splitItems.size();
      if (sibling != null && sibling != root) {
        relativeIndex = splitItems.indexOf(sibling) + 1;
      }

      splitItems.add(relativeIndex, node);
      if (splitItems.size() > 1) {
        if (split.getOrientation() == Orientation.HORIZONTAL) {
          split.setDividerPosition(relativeIndex - 1,
              1 - share(node.prefWidth(0), magnitude));
        } else {
          split.setDividerPosition(relativeIndex - 1,
              1 - share(node.prefHeight(0), magnitude));
        }
      }
    }

    watchDividers();
    layoutChanged();
  }

  /**
   * Dock the node into this dock pane at the given docking position relative to the root in the
   * layout. This is used to relatively position the dock nodes to other nodes given their preferred
   * size.
   * 
   * @param node The node that is to be docked into this dock pane.
   * @param dockPos The docking position of the node relative to the sibling.
   */
  public void dock(Node node, DockPos dockPos) {
    dock(node, dockPos, root);
  }

  /**
   * Detach the node from this dock pane removing it from the layout.
   * 
   * @param node The node that is to be removed from this dock pane.
   */
  public void undock(DockNode node) {
    rememberPlacement(node);
    untrack(node);
    if (root == null) {
      return;
    }

    // depth first search to find the parent of the node
    Stack<Parent> findStack = new Stack<Parent>();
    findStack.push((Parent) root);
    while (!findStack.isEmpty()) {
      Parent parent = findStack.pop();

      ObservableList<Node> children = parent.getChildrenUnmodifiable();

      if (parent instanceof SplitPane) {
        SplitPane split = (SplitPane) parent;
        children = split.getItems();
      }

      for (int i = 0; i < children.size(); i++) {
        if (children.get(i) == node) {
          children.remove(i);

          // start from the root again and remove any SplitPane's with no children in them
          Stack<Parent> clearStack = new Stack<Parent>();
          clearStack.push((Parent) root);
          while (!clearStack.isEmpty()) {
            parent = clearStack.pop();

            children = parent.getChildrenUnmodifiable();

            if (parent instanceof SplitPane) {
              SplitPane split = (SplitPane) parent;
              children = split.getItems();
            }

            for (i = 0; i < children.size(); i++) {
              if (children.get(i) instanceof SplitPane) {
                SplitPane split = (SplitPane) children.get(i);
                if (split.getItems().size() < 1) {
                  children.remove(i);
                  continue;
                } else {
                  clearStack.push(split);
                }
              }

            }
          }

          collapseSingleChildSplits();
          watchDividers();
          layoutChanged();
          return;
        } else if (children.get(i) instanceof Parent) {
          findStack.push((Parent) children.get(i));
        }
      }
    }
  }


  // ---------------------------------------------------------------------------------------------
  // druvu additions (see NOTICE.md): the workspace as a value (DockLayout), where hidden nodes go
  // back to, and one change signal for a persistence layer to listen to.
  // ---------------------------------------------------------------------------------------------

  /**
   * Shares smaller than this are noise from a divider that has not been laid out yet.
   */
  private static final double MIN_SHARE = 0.001;

  /**
   * The workspace as a value: every docked node by its JavaFX id, in the tree of splits that lays
   * them out, with the dividers as shares. A split with a single child is not part of the value.
   *
   * @return the layout, or empty when nothing is docked
   * @throws IllegalStateException when a docked node has no id
   */
  public Optional<DockLayout> dockLayout() {
    if (root == null || ((SplitPane) root).getItems().isEmpty()) {
      return Optional.empty();
    }
    for (DockNode node : dockedNodes()) {
      if (!hasId(node)) {
        throw new IllegalStateException("dock node '" + node.getTitle()
            + "' has no id - setId(...) before taking layouts");
      }
    }
    return Optional.of(snapshot(root));
  }

  /**
   * Rebuilds the workspace from a layout. Leaves are matched to {@code nodes} by id: a leaf with
   * no matching node is left out, a node absent from the layout ends up undocked (hidden), and a
   * floating node the layout names is brought back in. The nodes keep their identity and state;
   * only the splits around them are built anew.
   *
   * @throws IllegalArgumentException when two of the nodes share an id
   */
  public void apply(DockLayout layout, Collection<? extends DockNode> nodes) {
    final Map<String, DockNode> byId = new HashMap<String, DockNode>();
    for (DockNode node : nodes) {
      if (hasId(node) && byId.put(node.getId(), node) != null) {
        throw new IllegalArgumentException("two dock nodes share the id " + node.getId());
      }
    }
    DockLayout usable = layout;
    for (String id : layout.ids()) {
      if (usable != null && !byId.containsKey(id)) {
        usable = usable.remove(id).orElse(null);
      }
    }

    final Set<DockNode> before = dockedNodes();
    final Set<DockNode> after = new LinkedHashSet<DockNode>();
    if (usable != null) {
      for (String id : usable.ids()) {
        after.add(byId.get(id));
      }
    }
    clearTree();
    for (DockNode node : before) {
      if (!after.contains(node)) {
        untrack(node);
        node.detach();
      }
    }
    for (DockNode node : after) {
      if (!before.contains(node)) {
        track(node);
      }
      node.attach(this);
    }
    if (usable != null) {
      root = build(usable, byId);
      this.getChildren().add(root);
    }
    watchDividers();
    layoutChanged();
  }

  /**
   * Docks a hidden node back where it was: beside the neighbours it sat next to, with its old
   * share, as remembered when it left or as told by {@link #rememberPlacement}. Falls back to
   * docking on {@code side} of the whole workspace when nothing it sat next to is docked any more,
   * or when nothing is remembered.
   *
   * @throws IllegalStateException when the node is docked already
   */
  public void redock(DockNode node, DockPos side) {
    if (node.isDocked()) {
      throw new IllegalStateException("already docked: " + node.getTitle());
    }
    final DockLayout.Placement placement =
        hasId(node) ? hiddenPlacements.get(node.getId()) : null;
    final Optional<DockLayout> current = placement == null ? Optional.empty() : dockLayout();
    if (current.isPresent()) {
      final Optional<DockLayout> grown = current.get().insert(node.getId(), placement);
      if (grown.isPresent()) {
        final Set<DockNode> nodes = dockedNodes();
        nodes.add(node);
        apply(grown.get(), nodes);
        return;
      }
    }
    node.dock(this, side);
  }

  /**
   * Where hidden nodes go back to, by id: what a persistence layer keeps between runs.
   */
  public Map<String, DockLayout.Placement> hiddenPlacements() {
    return Collections.unmodifiableMap(hiddenPlacements);
  }

  /**
   * Tells the pane where a hidden node belongs - typically what {@link #hiddenPlacements()} said
   * in an earlier run. Forgotten again the moment the node docks.
   */
  public void rememberPlacement(String id, DockLayout.Placement placement) {
    DockLayoutText.requireId(id);
    hiddenPlacements.put(id, java.util.Objects.requireNonNull(placement, "placement"));
  }

  /**
   * Bumped on every change of the workspace shape - dock, undock, apply, and every divider move -
   * so a persistence layer listens here once instead of watching the tree.
   */
  public ReadOnlyIntegerProperty layoutRevisionProperty() {
    return layoutRevision.getReadOnlyProperty();
  }

  /**
   * CENTER only means something for the first node in an empty pane; upstream silently dropped
   * the node otherwise (registered, marked docked, never laid out).
   */
  void requireDockable(DockPos dockPos) {
    if (dockPos == DockPos.CENTER && root != null && !((SplitPane) root).getItems().isEmpty()) {
      throw new IllegalArgumentException(
          "CENTER only docks into an empty pane; use LEFT, RIGHT, TOP or BOTTOM");
    }
  }

  private void track(Node node) {
    final DockNodeEventHandler handler = new DockNodeEventHandler(node);
    dockNodeEventFilters.put(node, handler);
    node.addEventFilter(DockEvent.DOCK_OVER, handler);
    if (node instanceof DockNode dockNode && hasId(dockNode)) {
      hiddenPlacements.remove(dockNode.getId());
    }
  }

  private void untrack(Node node) {
    final DockNodeEventHandler handler = dockNodeEventFilters.remove(node);
    if (handler != null) {
      node.removeEventFilter(DockEvent.DOCK_OVER, handler);
    }
  }

  /**
   * Before a node leaves: where it sat, so redock() can put it back. Only possible while every
   * docked node has an id; an app that never set ids simply gets no memory.
   */
  private void rememberPlacement(DockNode node) {
    if (!hasId(node) || root == null) {
      return;
    }
    final Set<DockNode> docked = dockedNodes();
    if (!docked.contains(node)) {
      return;
    }
    for (DockNode each : docked) {
      if (!hasId(each)) {
        return;
      }
    }
    snapshot(root).placementOf(node.getId())
        .ifPresent(placement -> hiddenPlacements.put(node.getId(), placement));
  }

  private static boolean hasId(DockNode node) {
    return node.getId() != null && !node.getId().isBlank();
  }

  private Set<DockNode> dockedNodes() {
    final Set<DockNode> nodes = new LinkedHashSet<DockNode>();
    if (root != null) {
      collectDockNodes(root, nodes);
    }
    return nodes;
  }

  private static void collectDockNodes(Node node, Set<DockNode> into) {
    if (node instanceof SplitPane split) {
      for (Node item : split.getItems()) {
        collectDockNodes(item, into);
      }
    } else if (node instanceof DockNode dockNode) {
      into.add(dockNode);
    }
  }

  /**
   * The tree as a value; the caller has checked that every DockNode in it has an id.
   */
  private static DockLayout snapshot(Node node) {
    if (node instanceof SplitPane split) {
      final List<Node> items = split.getItems();
      if (items.size() == 1) {
        return snapshot(items.get(0));
      }
      final double[] dividers = SplitShares.positions(split);
      final List<DockLayout.Entry> entries = new ArrayList<DockLayout.Entry>();
      double previous = 0;
      for (int i = 0; i < items.size(); i++) {
        final double next = i < dividers.length ? dividers[i] : 1;
        final double share = next - previous;
        // NaN (a divider never laid out) and negatives count as unknown: equal shares fall out.
        entries.add(new DockLayout.Entry(snapshot(items.get(i)), share > MIN_SHARE ? share : MIN_SHARE));
        previous = next;
      }
      return new DockLayout.Split(split.getOrientation(), entries);
    }
    if (node instanceof DockNode dockNode) {
      return new DockLayout.Leaf(dockNode.getId());
    }
    throw new IllegalStateException("not a dock node: " + node);
  }

  /**
   * The tree for a layout; the root is always a SplitPane because dock() relies on that.
   */
  private static SplitPane build(DockLayout layout, Map<String, DockNode> byId) {
    if (layout instanceof DockLayout.Split split) {
      return buildSplit(split, byId);
    }
    final SplitPane single = new SplitPane();
    single.getItems().add(byId.get(((DockLayout.Leaf) layout).id()));
    return single;
  }

  private static SplitPane buildSplit(DockLayout.Split split, Map<String, DockNode> byId) {
    final SplitPane pane = new SplitPane();
    pane.setOrientation(split.orientation());
    final double[] positions = new double[split.entries().size() - 1];
    double running = 0;
    for (int i = 0; i < split.entries().size(); i++) {
      final DockLayout.Entry entry = split.entries().get(i);
      if (entry.child() instanceof DockLayout.Split inner) {
        pane.getItems().add(buildSplit(inner, byId));
      } else {
        pane.getItems().add(byId.get(((DockLayout.Leaf) entry.child()).id()));
      }
      if (i < positions.length) {
        running += entry.share();
        positions[i] = running;
      }
    }
    pane.setDividerPositions(positions);
    return pane;
  }

  private void clearTree() {
    if (root == null) {
      return;
    }
    clearItems(root);
    this.getChildren().remove(root);
    root = null;
  }

  private static void clearItems(Node node) {
    if (node instanceof SplitPane split) {
      for (Node item : new ArrayList<Node>(split.getItems())) {
        clearItems(item);
      }
      split.getItems().clear();
    }
  }

  /**
   * Upstream's undock left every emptied wrapper behind: each hide/show cycle added one nesting
   * level (a SplitPane with a single child) and the tree grew without bound. Fold them away,
   * keeping the divider positions of the split around each folded wrapper.
   */
  private void collapseSingleChildSplits() {
    if (root == null) {
      return;
    }
    final SplitPane rootSplit = (SplitPane) root;
    collapseUnder(rootSplit);
    // The root stays a SplitPane (dock() relies on it), but a lone nested split can take its place.
    if (rootSplit.getItems().size() == 1 && rootSplit.getItems().get(0) instanceof SplitPane inner) {
      rootSplit.getItems().clear();
      this.getChildren().set(this.getChildren().indexOf(rootSplit), inner);
      root = inner;
    }
  }

  private static void collapseUnder(SplitPane split) {
    final ObservableList<Node> items = split.getItems();
    for (int i = 0; i < items.size(); i++) {
      if (items.get(i) instanceof SplitPane child) {
        collapseUnder(child);
        if (child.getItems().size() == 1) {
          final Node grandchild = child.getItems().get(0);
          final double[] positions = split.getDividerPositions();
          child.getItems().clear();
          items.set(i, grandchild);
          split.setDividerPositions(positions);
        }
      }
    }
  }

  private void watchDividers() {
    if (root != null) {
      watchDividersUnder(root);
    }
  }

  /**
   * Every split gets a SplitShares keeper: dividers hold their shares across resizes, and a moved
   * divider reports here as a layout change.
   */
  private void watchDividersUnder(Node node) {
    if (!(node instanceof SplitPane split)) {
      return;
    }
    SplitShares.attach(split, this::layoutChanged);
    for (Node item : split.getItems()) {
      watchDividersUnder(item);
    }
  }

  private void layoutChanged() {
    layoutRevision.set(layoutRevision.get() + 1);
  }

  /**
   * The new node's share of a split, by preferred size. Upstream divided by zero here - a NaN
   * divider whenever nothing had a preferred size yet - so an unsized pair now splits in half.
   */
  private static double share(double preferred, double magnitude) {
    final double total = magnitude + preferred;
    return total > 0 ? preferred / total : 0.5;
  }

  @Override
  public void handle(DockEvent event) {
    if (event.getEventType() == DockEvent.DOCK_ENTER) {
      if (!dockIndicatorOverlay.isShowing()) {
        Point2D topLeft = DockPane.this.localToScreen(0, 0);
        dockIndicatorOverlay.show(DockPane.this, topLeft.getX(), topLeft.getY());
      }
    } else if (event.getEventType() == DockEvent.DOCK_OVER) {
      this.receivedEnter = false;

      dockPosDrag = null;
      dockAreaDrag = dockNodeDrag;

      for (DockPosButton dockIndicatorButton : dockPosButtons) {
        if (dockIndicatorButton
            .contains(dockIndicatorButton.screenToLocal(event.getScreenX(), event.getScreenY()))) {
          dockPosDrag = dockIndicatorButton.getDockPos();
          if (dockIndicatorButton.isDockRoot()) {
            dockAreaDrag = root;
          }
          dockIndicatorButton.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
          break;
        } else {
          dockIndicatorButton.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), false);
        }
      }

      if (dockPosDrag != null) {
        Point2D originToScene = dockAreaDrag.localToScene(0, 0).subtract(this.localToScene(0, 0));

        dockAreaIndicator.setVisible(true);
        dockAreaIndicator.relocate(originToScene.getX(), originToScene.getY());
        if (dockPosDrag == DockPos.RIGHT) {
          dockAreaIndicator.setTranslateX(dockAreaDrag.getLayoutBounds().getWidth() / 2);
        } else {
          dockAreaIndicator.setTranslateX(0);
        }

        if (dockPosDrag == DockPos.BOTTOM) {
          dockAreaIndicator.setTranslateY(dockAreaDrag.getLayoutBounds().getHeight() / 2);
        } else {
          dockAreaIndicator.setTranslateY(0);
        }

        if (dockPosDrag == DockPos.LEFT || dockPosDrag == DockPos.RIGHT) {
          dockAreaIndicator.setWidth(dockAreaDrag.getLayoutBounds().getWidth() / 2);
        } else {
          dockAreaIndicator.setWidth(dockAreaDrag.getLayoutBounds().getWidth());
        }
        if (dockPosDrag == DockPos.TOP || dockPosDrag == DockPos.BOTTOM) {
          dockAreaIndicator.setHeight(dockAreaDrag.getLayoutBounds().getHeight() / 2);
        } else {
          dockAreaIndicator.setHeight(dockAreaDrag.getLayoutBounds().getHeight());
        }
      } else {
        dockAreaIndicator.setVisible(false);
      }

      if (dockNodeDrag != null) {
        Point2D originToScreen = dockNodeDrag.localToScreen(0, 0);

        double posX = originToScreen.getX() + dockNodeDrag.getLayoutBounds().getWidth() / 2
            - dockPosIndicator.getWidth() / 2;
        double posY = originToScreen.getY() + dockNodeDrag.getLayoutBounds().getHeight() / 2
            - dockPosIndicator.getHeight() / 2;

        if (!dockIndicatorPopup.isShowing()) {
          dockIndicatorPopup.show(DockPane.this, posX, posY);
        } else {
          dockIndicatorPopup.setX(posX);
          dockIndicatorPopup.setY(posY);
        }

        // set visible after moving the popup
        dockPosIndicator.setVisible(true);
      } else {
        dockPosIndicator.setVisible(false);
      }
    }

    if (event.getEventType() == DockEvent.DOCK_RELEASED && event.getContents() != null) {
      if (dockPosDrag != null && dockIndicatorOverlay.isShowing()) {
        DockNode dockNode = (DockNode) event.getContents();
        dockNode.dock(this, dockPosDrag, dockAreaDrag);
      }
    }

    if ((event.getEventType() == DockEvent.DOCK_EXIT && !this.receivedEnter)
        || event.getEventType() == DockEvent.DOCK_RELEASED) {
      if (dockIndicatorPopup.isShowing()) {
        dockIndicatorOverlay.hide();
        dockIndicatorPopup.hide();
      }
    }
  }
}
