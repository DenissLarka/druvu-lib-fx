/**
 * Market Watch - the druvu-lib-fx showcase application.
 */
module com.druvu.lib.fx.example {
	requires com.druvu.lib.fx;
	requires javafx.controls;
	requires javafx.fxml;

	// javafx.graphics reflectively instantiates the Application subclass
	exports com.druvu.lib.fx.example to javafx.graphics;

	// FXML injects @FXML fields and calls handlers reflectively
	opens com.druvu.lib.fx.example.instruments to javafx.fxml;
}
