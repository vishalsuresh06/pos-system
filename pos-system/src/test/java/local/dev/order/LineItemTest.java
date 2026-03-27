package local.dev.order;

import local.dev.menu.Category;
import local.dev.menu.Customization;
import local.dev.menu.MenuItem;
import local.dev.menu.Modification;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LineItemTest {

    private MenuItem burger() {
        return new MenuItem.Builder("Big Burger", new BigDecimal("8.99"), Category.ENTREE).build();
    }

    @Test
    void noCustomizations_totalPriceIsBaseTimesQuantity() {
        LineItem item = new LineItem(burger(), 2, null);
        assertEquals(new BigDecimal("8.99"), item.getUnitPrice());
        assertEquals(new BigDecimal("17.98"), item.getTotalPrice());
    }

    @Test
    void withAddCustomization_unitPriceIncreasesCorrectly() {
        Customization bacon = new Customization("Bacon", Modification.ADD, new BigDecimal("1.50"));
        LineItem item = new LineItem(burger(), 1, List.of(bacon));
        assertEquals(new BigDecimal("10.49"), item.getUnitPrice());
        assertEquals(new BigDecimal("10.49"), item.getTotalPrice());
    }

    @Test
    void withRemoveCustomization_unitPriceUnchangedWhenPriceIsZero() {
        Customization noPickles = new Customization("Pickles", Modification.REMOVE, BigDecimal.ZERO);
        LineItem item = new LineItem(burger(), 1, List.of(noPickles));
        assertEquals(new BigDecimal("8.99"), item.getUnitPrice());
    }

    @Test
    void multipleCustomizations_allApplied() {
        Customization bacon = new Customization("Bacon",   Modification.ADD,    new BigDecimal("1.50"));
        Customization extra = new Customization("Cheese",  Modification.ADD,    new BigDecimal("0.75"));
        LineItem item = new LineItem(burger(), 3, List.of(bacon, extra));
        BigDecimal expectedUnit = new BigDecimal("8.99").add(new BigDecimal("1.50")).add(new BigDecimal("0.75"));
        assertEquals(expectedUnit, item.getUnitPrice());
        assertEquals(expectedUnit.multiply(BigDecimal.valueOf(3)), item.getTotalPrice());
    }

    @Test
    void nullMenuItem_throws() {
        assertThrows(IllegalArgumentException.class, () -> new LineItem(null, 1, null));
    }

    @Test
    void zeroQuantity_throws() {
        assertThrows(IllegalArgumentException.class, () -> new LineItem(burger(), 0, null));
    }

    @Test
    void negativeQuantity_throws() {
        assertThrows(IllegalArgumentException.class, () -> new LineItem(burger(), -1, null));
    }

    @Test
    void id_isNonNull() {
        LineItem item = new LineItem(burger(), 1, null);
        assertNotNull(item.getId());
    }
}
