/**
 * druvu-lib-fx - JavaFX application toolkit.
 *
 * JavaFX modules are resolved from the JDK (a JavaFX-bundled build such as Azul Zulu FX);
 * there are deliberately no org.openjfx artifact dependencies.
 */
module com.druvu.lib.fx {
	requires transitive javafx.graphics;
	requires transitive javafx.controls;

	requires org.slf4j;

	// Lombok annotation processing (compile-time only)
	requires static lombok;

	exports com.druvu.lib.fx.auth;
	exports com.druvu.lib.fx.bus;
	exports com.druvu.lib.fx.dock;
	exports com.druvu.lib.fx.exec;
	exports com.druvu.lib.fx.notify;
	exports com.druvu.lib.fx.prefs;
	exports com.druvu.lib.fx.status;
	exports com.druvu.lib.fx.util;
}
