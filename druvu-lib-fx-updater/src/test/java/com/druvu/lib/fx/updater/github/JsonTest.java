package com.druvu.lib.fx.updater.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** The JSON reader: every value kind, escapes, nesting, whitespace, and the malformed documents it refuses. */
public class JsonTest {

    @Test
    public void readsAnObjectWithEveryKindOfValue() {
        Object value = Json.read("""
                { "text": "a\\"b\\\\c\\n\\u00e9", "whole": 42, "negative": -7, "real": 1.5e2,
                  "yes": true, "no": false, "nothing": null, "list": [1, "two", {"three": 3}], "empty": {}, "none": [] }
                """);

        Map<String, Object> object = Json.object(value);
        assertThat(object.get("text")).isEqualTo("a\"b\\c\n\u00e9");
        assertThat(object.get("whole")).isEqualTo(42L);
        assertThat(object.get("negative")).isEqualTo(-7L);
        assertThat(object.get("real")).isEqualTo(150.0);
        assertThat(object.get("yes")).isEqualTo(Boolean.TRUE);
        assertThat(object.get("no")).isEqualTo(Boolean.FALSE);
        assertThat(object).containsKey("nothing");
        assertThat(object.get("nothing")).isNull();
        assertThat(object.get("list")).isEqualTo(List.of(1L, "two", Map.of("three", 3L)));
        assertThat(object.get("empty")).isEqualTo(Map.of());
        assertThat(object.get("none")).isEqualTo(List.of());
    }

    @Test
    public void theHelpersAnswerWithDefaultsForWhatIsMissing() {
        Map<String, Object> object =
                Json.object(Json.read("{\"name\": \"x\", \"size\": 3, \"assets\": [{\"a\": 1}, 2]}"));

        assertThat(Json.string(object, "name")).isEqualTo("x");
        assertThat(Json.string(object, "absent")).isEmpty();
        assertThat(Json.string(object, "size")).as("not a string").isEmpty();
        assertThat(Json.number(object, "size", -1)).isEqualTo(3);
        assertThat(Json.number(object, "name", -1)).isEqualTo(-1);
        assertThat(Json.objects(object, "assets")).containsExactly(Map.of("a", 1L));
        assertThat(Json.objects(object, "absent")).isEmpty();
    }

    @DataProvider
    public Object[][] malformed() {
        return new Object[][] {
            {"{\"a\":}"},
            {"[1,]"},
            {"{\"a\" 1}"},
            {"\"open"},
            {"tru"},
            {"{} x"},
            {""},
            {"{1: 2}"},
            {"\"\\q\""},
            {"1.2.3"}
        };
    }

    @Test(dataProvider = "malformed")
    public void refusesAMalformedDocumentNamingThePosition(String text) {
        assertThatThrownBy(() -> Json.read(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("malformed JSON at ");
    }

    @Test
    public void anObjectIsExpectedWhereOneIsRequired() {
        assertThatThrownBy(() -> Json.object(Json.read("[]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("an object was expected");
    }
}
