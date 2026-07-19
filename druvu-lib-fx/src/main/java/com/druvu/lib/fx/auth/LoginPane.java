package com.druvu.lib.fx.auth;

import com.druvu.lib.fx.exec.FxExec;
import com.druvu.lib.fx.util.FxThreads;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * A self-contained sign-in control. It collects a username and password, runs the app-supplied {@link Authenticator} on
 * a background virtual thread (via {@link FxExec}), and on success hands the principal to the app-supplied callback. It
 * deliberately does <em>not</em> decide what happens next - swapping scenes, opening a workspace, etc. is the app's
 * job, driven by {@code onSuccess}.
 *
 * <p>UX states are handled for you: while authenticating, the fields and button disable and the button reads "Signing
 * in..."; a rejected login shows the {@code Authenticator}'s exception message; a fresh attempt clears it.
 *
 * @param <P> the principal type produced by the {@link Authenticator}
 */
public final class LoginPane<P> extends VBox {

    private final FxExec exec;
    private final Authenticator<P> authenticator;
    private final Consumer<? super P> onSuccess;

    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Button loginButton = new Button("Sign in");
    private final Label errorLabel = new Label();
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(this, "busy", false);

    /**
     * @param exec background executor the authenticator runs on (not the FX thread)
     * @param authenticator verifies credentials and yields a principal; supplied by the app
     * @param onSuccess invoked on the FX thread with the principal after a successful login
     */
    public LoginPane(FxExec exec, Authenticator<P> authenticator, Consumer<? super P> onSuccess) {
        this.exec = Objects.requireNonNull(exec, "exec");
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
        this.onSuccess = Objects.requireNonNull(onSuccess, "onSuccess");

        usernameField.setPromptText("Username");
        passwordField.setPromptText("Password");
        errorLabel.setStyle("-fx-text-fill: #b00020;");
        errorLabel.setWrapText(true);

        // Login is disabled while busy or when no username is entered; Enter submits from either field.
        loginButton.disableProperty().bind(busy.or(usernameField.textProperty().isEmpty()));
        usernameField.disableProperty().bind(busy);
        passwordField.disableProperty().bind(busy);
        busy.addListener((_, _, b) -> loginButton.setText(b ? "Signing in..." : "Sign in"));

        loginButton.setDefaultButton(true);
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setOnAction(_ -> submitFromFields());
        usernameField.setOnAction(_ -> submitFromFields());
        passwordField.setOnAction(_ -> submitFromFields());

        final VBox form = new VBox(8, new Label("Sign in"), usernameField, passwordField, loginButton, errorLabel);
        form.setPadding(new Insets(24));
        form.setMaxWidth(320);
        form.setFillWidth(true);

        setAlignment(Pos.CENTER);
        getChildren().add(form);
    }

    /** @return {@code true} while an authentication attempt is in flight. */
    public ReadOnlyBooleanProperty busyProperty() {
        return busy.getReadOnlyProperty();
    }

    private void submitFromFields() {
        submit(usernameField.getText(), passwordField.getText().toCharArray());
    }

    /**
     * Attempts a login programmatically. No-op if an attempt is already in flight. Must be called on the FX thread.
     *
     * @param username the username to authenticate
     * @param password the password to authenticate; a copy is passed to the authenticator
     */
    public void submit(String username, char[] password) {
        FxThreads.requireFx();
        if (busy.get()) {
            return;
        }
        busy.set(true);
        errorLabel.setText("");
        exec.supply("login", () -> authenticator.authenticate(username, password))
                .whenCompleteAsync(
                        (principal, failure) -> {
                            busy.set(false);
                            if (failure == null) {
                                onSuccess.accept(principal);
                            } else {
                                errorLabel.setText(messageOf(failure));
                            }
                        },
                        FxThreads.fxExecutor());
    }

    private static String messageOf(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        final String message = cause.getMessage();
        return message != null ? message : cause.getClass().getSimpleName();
    }
}
