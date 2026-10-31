package com.druvu.lib.fx.update;

import com.druvu.lib.fx.updater.Decision;
import com.druvu.lib.fx.updater.Installed;
import com.druvu.lib.fx.updater.RestartChoice;
import com.druvu.lib.fx.updater.Update;
import com.druvu.lib.fx.updater.UpdateFailure;
import java.lang.module.ModuleDescriptor.Version;
import java.util.Optional;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * The five dialogs of the update conversation. Every method runs on the JavaFX thread and returns when the dialog is
 * closed; the words are the app's, so each takes the app's name.
 */
final class UpdateDialogs {

    private static final ButtonType INSTALL = new ButtonType("Install", ButtonData.OK_DONE);
    private static final ButtonType NOT_NOW = new ButtonType("Not now", ButtonData.CANCEL_CLOSE);
    private static final ButtonType SKIP_VERSION = new ButtonType("Skip this version", ButtonData.OTHER);
    private static final ButtonType RESTART_NOW = new ButtonType("Restart now", ButtonData.OK_DONE);
    private static final ButtonType LATER = new ButtonType("Later", ButtonData.CANCEL_CLOSE);
    private static final double NOTES_HEIGHT = 220;
    private static final double DIALOG_WIDTH = 520;

    private UpdateDialogs() {}

    static Decision offer(Window owner, String appName, Update update, BooleanProperty checkAtStart) {
        Alert alert = alert(owner, Alert.AlertType.CONFIRMATION, appName + " update");
        alert.setHeaderText(appName + " " + update.version() + " is available");
        TextArea notes = new TextArea(update.notes().isBlank() ? "No release notes." : update.notes());
        notes.setEditable(false);
        notes.setWrapText(true);
        notes.setPrefHeight(NOTES_HEIGHT);
        CheckBox atStart = new CheckBox("Check for updates when " + appName + " starts");
        atStart.selectedProperty().bindBidirectional(checkAtStart);
        VBox content = new VBox(12, new Label("What is new:"), notes, atStart);
        alert.getDialogPane().setContent(content);
        alert.getButtonTypes().setAll(INSTALL, NOT_NOW, SKIP_VERSION);
        Optional<ButtonType> answer = alert.showAndWait();
        atStart.selectedProperty().unbindBidirectional(checkAtStart);
        if (answer.isPresent() && answer.get() == INSTALL) {
            return Decision.INSTALL;
        }
        if (answer.isPresent() && answer.get() == SKIP_VERSION) {
            return Decision.SKIP_VERSION;
        }
        return Decision.NOT_NOW;
    }

    static RestartChoice installed(Window owner, String appName, Installed installed) {
        if (installed instanceof Installed.ManualSteps manual) {
            Alert alert = alert(owner, Alert.AlertType.INFORMATION, appName + " update");
            alert.setHeaderText(appName + " " + manual.version() + " is ready");
            alert.setContentText(manual.instructions());
            alert.showAndWait();
            return RestartChoice.LATER;
        }
        Alert alert = alert(owner, Alert.AlertType.CONFIRMATION, appName + " update");
        alert.setHeaderText(appName + " " + installed.version() + " is installed");
        alert.setContentText("It runs the next time " + appName + " starts. Restart now?");
        alert.getButtonTypes().setAll(RESTART_NOW, LATER);
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == RESTART_NOW ? RestartChoice.RESTART_NOW : RestartChoice.LATER;
    }

    static void upToDate(Window owner, String appName, Version current) {
        Alert alert = alert(owner, Alert.AlertType.INFORMATION, appName + " update");
        alert.setHeaderText("You have the latest version");
        alert.setContentText(appName + " " + current + " is the newest release.");
        alert.showAndWait();
    }

    static void failed(Window owner, String appName, UpdateFailure failure) {
        Alert alert = alert(owner, Alert.AlertType.WARNING, appName + " update");
        alert.setHeaderText("The update stopped");
        alert.setContentText(failure.message());
        alert.showAndWait();
    }

    static void unavailable(Window owner, String appName) {
        Alert alert = alert(owner, Alert.AlertType.INFORMATION, appName + " update");
        alert.setHeaderText("Updates are not available in this run");
        alert.setContentText("Only an installed " + appName + " checks for updates.");
        alert.showAndWait();
    }

    private static Alert alert(Window owner, Alert.AlertType type, String title) {
        Alert alert = new Alert(type);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.getDialogPane().setPrefWidth(DIALOG_WIDTH);
        return alert;
    }
}
