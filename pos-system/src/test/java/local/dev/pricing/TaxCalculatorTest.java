package local.dev.pricing;

import local.dev.menu.Category;
import local.dev.menu.MenuItem;
import local.dev.order.LineItem;
import local.dev.order.Order;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class TaxCalculatorTest {

    private MenuItem item(String price, Category category) {
        return new MenuItem.Builder("Item", new BigDecimal(price), category).build();
    }

    private Order orderWith(MenuItem menuItem) {
        return new Order.Builder().addLineItem(new LineItem(menuItem, 1, null)).build();
    }

    @Test
    void calculateTax_onSubtotal_usesDefaultRate() {
        TaxCalculator calc = new TaxCalculator(new BigDecimal("0.10"));
        assertEquals(new BigDecimal("1.00"), calc.calculateTax(new BigDecimal("10.00")));
    }

    @Test
    void calculateTax_onOrder_usesDefaultRate() {
        TaxCalculator calc = new TaxCalculator(new BigDecimal("0.08"));
        Order order = orderWith(item("10.00", Category.ENTREE));
        assertEquals(new BigDecimal("0.80"), calc.calculateTax(order));
    }

    @Test
    void calculateTax_onOrder_usesCategoryRateWhenSet() {
        TaxCalculator calc = new TaxCalculator(new BigDecimal("0.08"));
        calc.setCategoryRate(Category.DRINK, new BigDecimal("0.00")); // drinks untaxed
        Order order = orderWith(item("5.00", Category.DRINK));
        assertEquals(new BigDecimal("0.00"), calc.calculateTax(order));
    }

    @Test
    void calculateTax_mixedCategories_appliesCorrectRatePerItem() {
        TaxCalculator calc = new TaxCalculator(new BigDecimal("0.10"));
        calc.setCategoryRate(Category.DRINK, BigDecimal.ZERO);

        MenuItem entree = item("10.00", Category.ENTREE);
        MenuItem drink  = item("2.00",  Category.DRINK);
        Order order = new Order.Builder()
            .addLineItem(new LineItem(entree, 1, null))
            .addLineItem(new LineItem(drink,  1, null))
            .build();

        // Only entree ($10) taxed at 10% = $1.00; drink untaxed
        assertEquals(new BigDecimal("1.00"), calc.calculateTax(order));
    }

    @Test
    void construct_negativeRate_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new TaxCalculator(new BigDecimal("-0.01")));
    }

    @Test
    void construct_nullRate_throws() {
        assertThrows(IllegalArgumentException.class, () -> new TaxCalculator(null));
    }

    @Test
    void setCategoryRate_negativeRate_throws() {
        TaxCalculator calc = new TaxCalculator(new BigDecimal("0.08"));
        assertThrows(IllegalArgumentException.class, () ->
            calc.setCategoryRate(Category.DRINK, new BigDecimal("-0.01")));
    }

    @Test
    void calculateTax_roundsToTwoDecimalPlaces() {
        TaxCalculator calc = new TaxCalculator(new BigDecimal("0.07"));
        BigDecimal tax = calc.calculateTax(new BigDecimal("9.99"));
        assertEquals(2, tax.scale());
    }
}
