package com.druvu.lib.fx.example.instruments;

import com.druvu.lib.fx.exec.FxExec;
import com.druvu.lib.fx.util.FxThreads;
import java.util.Locale;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/**
 * FXML page controller - shows how the toolkit composes with FXML without owning it: the FXML/controller pair is plain
 * JavaFX; FxExec + FxThreads only enter through {@link #init(FxExec)}, called by the shell after loading.
 *
 * <p>Threading: everything here runs on the FX thread except the load callable, which FxExec runs on a virtual thread;
 * results hop back via FxThreads.fxExecutor().
 */
public final class InstrumentsController {

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<AssetClass> classFilter;

    @FXML
    private DatePicker listedAfter;

    @FXML
    private Spinner<Integer> minPrice;

    @FXML
    private Slider maxPrice;

    @FXML
    private Label maxPriceLabel;

    @FXML
    private CheckBox favOnly;

    @FXML
    private Button reloadButton;

    @FXML
    private TableView<Instrument> table;

    @FXML
    private TableColumn<Instrument, String> favColumn;

    @FXML
    private TableColumn<Instrument, String> symbolColumn;

    @FXML
    private TableColumn<Instrument, String> nameColumn;

    @FXML
    private TableColumn<Instrument, String> classColumn;

    @FXML
    private TableColumn<Instrument, Number> priceColumn;

    @FXML
    private TableColumn<Instrument, String> listedColumn;

    private final ObservableList<Instrument> master = FXCollections.observableArrayList();
    private final FilteredList<Instrument> filtered = new FilteredList<>(master);
    private FxExec exec;

    @FXML
    @SuppressWarnings("PMD.UnusedPrivateMethod") // invoked reflectively by FXMLLoader
    private void initialize() {
        classFilter.setItems(FXCollections.observableArrayList(AssetClass.values()));
        minPrice.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 2500, 0, 10));
        maxPriceLabel.textProperty().bind(maxPrice.valueProperty().asString(Locale.ROOT, "%.0f"));

        favColumn.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(c.getValue().favourite() ? "*" : ""));
        symbolColumn.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(c.getValue().symbol()));
        nameColumn.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(c.getValue().name()));
        classColumn.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(c.getValue().assetClass().name()));
        priceColumn.setCellValueFactory(
                c -> new ReadOnlyObjectWrapper<>(c.getValue().price()));
        listedColumn.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(c.getValue().listed().toString()));

        final SortedList<Instrument> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        searchField.textProperty().addListener((obs, old, val) -> applyFilters());
        classFilter.valueProperty().addListener((obs, old, val) -> applyFilters());
        listedAfter.valueProperty().addListener((obs, old, val) -> applyFilters());
        minPrice.valueProperty().addListener((obs, old, val) -> applyFilters());
        maxPrice.valueProperty().addListener((obs, old, val) -> applyFilters());
        favOnly.selectedProperty().addListener((obs, old, val) -> applyFilters());
    }

    /** Called by the shell after FXML load; triggers the initial background load. */
    public void init(FxExec exec) {
        this.exec = exec;
        onReload();
    }

    @FXML
    private void onReload() {
        reloadButton.setDisable(true);
        table.setPlaceholder(new Label("loading..."));
        exec.supply("load instruments", () -> {
                    Thread.sleep(700);
                    return Instrument.demoData();
                })
                .whenCompleteAsync(
                        (data, error) -> {
                            reloadButton.setDisable(false);
                            if (error == null) {
                                master.setAll(data);
                            } else {
                                table.setPlaceholder(new Label("load failed: " + error.getMessage()));
                            }
                        },
                        FxThreads.fxExecutor());
    }

    @FXML
    @SuppressWarnings("PMD.UnusedPrivateMethod") // invoked reflectively by FXMLLoader
    private void onReset() {
        searchField.clear();
        classFilter.setValue(null);
        listedAfter.setValue(null);
        minPrice.getValueFactory().setValue(0);
        maxPrice.setValue(maxPrice.getMax());
        favOnly.setSelected(false);
    }

    private void applyFilters() {
        final String text = searchField.getText() == null
                ? ""
                : searchField.getText().trim().toLowerCase(Locale.ROOT);
        final AssetClass assetClass = classFilter.getValue();
        final java.time.LocalDate after = listedAfter.getValue();
        final int min = minPrice.getValue();
        final double max = maxPrice.getValue();
        final boolean favouritesOnly = favOnly.isSelected();

        filtered.setPredicate(i -> (text.isEmpty()
                        || i.symbol().toLowerCase(Locale.ROOT).contains(text)
                        || i.name().toLowerCase(Locale.ROOT).contains(text))
                && (assetClass == null || i.assetClass() == assetClass)
                && (after == null || i.listed().isAfter(after))
                && i.price() >= min
                && i.price() <= max
                && (!favouritesOnly || i.favourite()));
    }
}
