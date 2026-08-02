/** Market Watch - the druvu-lib-fx showcase application. */
module com.druvu.lib.fx.example {
    requires com.druvu.lib.fx;
    requires javafx.controls;
    requires javafx.fxml;

    // Opts in to com.druvu.lib.fx.os.DesktopHooks (macOS application menu + open-file events). The
    // toolkit only declares 'requires static java.desktop', because AWT costs ~34 MB in a jlink image
    // and an app that wants none of this should not pay for it - so the opt-in lives here.
    requires java.desktop;

    // javafx.graphics reflectively instantiates the Application subclass
    exports com.druvu.lib.fx.example to
            javafx.graphics;

    // FXML injects @FXML fields and calls handlers reflectively
    opens com.druvu.lib.fx.example.instruments to
            javafx.fxml;
}
