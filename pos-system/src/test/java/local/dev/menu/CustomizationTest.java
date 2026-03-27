package local.dev.menu;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class CustomizationTest {

    @Test
    void add_withPositivePrice_succeeds() {
        Customization c = new Customization("Bacon", Modification.ADD, new BigDecimal("1.50"));
        assertEquals("Bacon", c.getName());
        assertEquals(Modification.ADD, c.getModification());
        assertEquals(new BigDecimal("1.50"), c.getPriceChange());
    }

    @Test
    void add_withZeroPrice_succeeds() {
        assertDoesNotThrow(() -> new Customization("Cheese", Modification.ADD, BigDecimal.ZERO));
    }

    @Test
    void add_withNegativePrice_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new Customization("Bacon", Modification.ADD, new BigDecimal("-1.00")));
    }

    @Test
    void remove_withZeroPrice_succeeds() {
        assertDoesNotThrow(() -> new Customization("Pickles", Modification.REMOVE, BigDecimal.ZERO));
    }

    @Test
    void remove_withNegativePrice_succeeds() {
        assertDoesNotThrow(() -> new Customization("Sauce", Modification.REMOVE, new BigDecimal("-0.50")));
    }

    @Test
    void remove_withPositivePrice_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new Customization("Pickles", Modification.REMOVE, new BigDecimal("0.50")));
    }

    @Test
    void substitute_withZeroPrice_succeeds() {
        assertDoesNotThrow(() -> new Customization("Wheat Bun", Modification.SUBSTITUTE, BigDecimal.ZERO));
    }

    @Test
    void nullName_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new Customization(null, Modification.ADD, BigDecimal.ZERO));
    }

    @Test
    void nullModification_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new Customization("Bacon", null, BigDecimal.ZERO));
    }
}
