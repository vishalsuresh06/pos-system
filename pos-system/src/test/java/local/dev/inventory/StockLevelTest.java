package local.dev.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StockLevelTest {

    @Test
    void construct_validArgs_succeeds() {
        StockLevel s = new StockLevel("patty", 50, 10);
        assertEquals("patty", s.getIngredientName());
        assertEquals(50, s.getQuantity());
        assertEquals(10, s.getLowStockThreshold());
    }

    @Test
    void construct_nullName_throws() {
        assertThrows(IllegalArgumentException.class, () -> new StockLevel(null, 50, 10));
    }

    @Test
    void construct_blankName_throws() {
        assertThrows(IllegalArgumentException.class, () -> new StockLevel("  ", 50, 10));
    }

    @Test
    void construct_negativeQuantity_throws() {
        assertThrows(IllegalArgumentException.class, () -> new StockLevel("patty", -1, 10));
    }

    @Test
    void construct_negativeThreshold_throws() {
        assertThrows(IllegalArgumentException.class, () -> new StockLevel("patty", 50, -1));
    }

    @Test
    void isInStock_sufficientQuantity_returnsTrue() {
        StockLevel s = new StockLevel("patty", 5, 2);
        assertTrue(s.isInStock(5));
        assertTrue(s.isInStock(1));
    }

    @Test
    void isInStock_insufficientQuantity_returnsFalse() {
        StockLevel s = new StockLevel("patty", 3, 2);
        assertFalse(s.isInStock(4));
    }

    @Test
    void deduct_valid_reducesQuantity() {
        StockLevel s = new StockLevel("patty", 10, 2);
        s.deduct(3);
        assertEquals(7, s.getQuantity());
    }

    @Test
    void deduct_exactAmount_leavesZero() {
        StockLevel s = new StockLevel("patty", 5, 2);
        s.deduct(5);
        assertEquals(0, s.getQuantity());
    }

    @Test
    void deduct_moreThanAvailable_throws() {
        StockLevel s = new StockLevel("patty", 3, 2);
        assertThrows(IllegalStateException.class, () -> s.deduct(4));
    }

    @Test
    void deduct_zeroAmount_throws() {
        StockLevel s = new StockLevel("patty", 10, 2);
        assertThrows(IllegalArgumentException.class, () -> s.deduct(0));
    }

    @Test
    void restock_addsToQuantity() {
        StockLevel s = new StockLevel("patty", 5, 2);
        s.restock(10);
        assertEquals(15, s.getQuantity());
    }

    @Test
    void restock_zeroAmount_throws() {
        StockLevel s = new StockLevel("patty", 5, 2);
        assertThrows(IllegalArgumentException.class, () -> s.restock(0));
    }

    @Test
    void isLowStock_atThreshold_returnsTrue() {
        StockLevel s = new StockLevel("patty", 2, 2);
        assertTrue(s.isLowStock());
    }

    @Test
    void isLowStock_belowThreshold_returnsTrue() {
        StockLevel s = new StockLevel("patty", 1, 2);
        assertTrue(s.isLowStock());
    }

    @Test
    void isLowStock_aboveThreshold_returnsFalse() {
        StockLevel s = new StockLevel("patty", 5, 2);
        assertFalse(s.isLowStock());
    }

    @Test
    void isOutOfStock_zeroQuantity_returnsTrue() {
        StockLevel s = new StockLevel("patty", 0, 2);
        assertTrue(s.isOutOfStock());
    }

    @Test
    void isOutOfStock_nonZeroQuantity_returnsFalse() {
        StockLevel s = new StockLevel("patty", 1, 2);
        assertFalse(s.isOutOfStock());
    }
}
