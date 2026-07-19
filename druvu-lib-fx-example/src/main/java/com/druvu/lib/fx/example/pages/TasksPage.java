package com.druvu.lib.fx.example.pages;

import com.druvu.lib.fx.bus.Delivery;
import com.druvu.lib.fx.bus.FxBus;
import com.druvu.lib.fx.exec.FxExec;
import com.druvu.lib.fx.exec.TaskEvent;
import com.druvu.lib.fx.notify.Notifications;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Background-work playground: launch tasks, watch their {@link TaskEvent} lifecycle stream. Buttons submit from the FX
 * thread and return instantly; the log is filled by an FX-mode bus subscription, so event handling is single-threaded
 * by construction.
 */
public final class TasksPage {

    private static final int LOG_LIMIT = 200;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ObservableList<String> log = FXCollections.observableArrayList();
    private final Node node;

    public TasksPage(FxBus bus, FxExec exec, Notifications notifications) {
        final Button quick = new Button("Quick task (0.5 s)");
        quick.setOnAction(e -> exec.run("quick task", () -> Thread.sleep(500)));

        final Button slow = new Button("Slow task (5 s)");
        slow.setOnAction(e -> exec.run("slow task", () -> Thread.sleep(5_000)));

        final Button failing = new Button("Failing task");
        failing.setOnAction(e -> exec.run("doomed task", () -> {
            Thread.sleep(800);
            throw new IllegalStateException("simulated blow-up");
        }));

        final HBox buttons = new HBox(8, quick, slow, failing, new Label("- fire several in parallel"));
        buttons.setPadding(new Insets(8));

        // One button per toast level, so all four Notifications colours can be seen on demand.
        final Button info = new Button("Info");
        info.setOnAction(e -> notifications.info("Market data refreshed."));
        final Button success = new Button("Success");
        success.setOnAction(e -> notifications.success("Trade booked successfully."));
        final Button warning = new Button("Warning");
        warning.setOnAction(e -> notifications.warning("Latency above threshold."));
        final Button error = new Button("Error");
        error.setOnAction(e -> notifications.error("Connection to the market feed was lost."));

        final HBox toasts = new HBox(8, new Label("Toasts:"), info, success, warning, error);
        toasts.setPadding(new Insets(0, 8, 8, 8));

        final ListView<String> logView = new ListView<>(log);
        VBox.setVgrow(logView, Priority.ALWAYS);

        final VBox box = new VBox(buttons, toasts, logView);
        this.node = box;

        // page lives as long as the app: subscription intentionally not closed
        bus.subscribe(TaskEvent.class, Delivery.FX, this::onEvent);
    }

    public Node node() {
        return node;
    }

    private void onEvent(TaskEvent event) {
        log.add(0, LocalTime.now().format(TIME) + "  " + describe(event));
        if (log.size() > LOG_LIMIT) {
            log.remove(log.size() - 1);
        }
    }

    private static String describe(TaskEvent event) {
        return switch (event) {
            case TaskEvent.Started s -> "started   #%d %s".formatted(s.id(), s.title());
            case TaskEvent.Finished f ->
                "finished  #%d %s in %d ms"
                        .formatted(f.id(), f.title(), f.elapsed().toMillis());
            case TaskEvent.Failed x ->
                "FAILED    #%d %s after %d ms: %s"
                        .formatted(
                                x.id(),
                                x.title(),
                                x.elapsed().toMillis(),
                                x.error().getMessage());
        };
    }
}
