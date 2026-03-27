package local.dev.pricing;

import local.dev.menu.Category;
import local.dev.menu.MenuItem;
import local.dev.order.LineItem;
import local.dev.order.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PricingEngineTest {

    private TaxCalculator taxCalculator;

    @BeforeEach
    void setUp() {
        taxCalculator = new TaxCalculator(new BigDecimal("0.10"));
    }

    private Order orderWithTotal(String price) {
        MenuItem item = new MenuItem.Builder("Item", new BigDecimal(price), Category.ENTREE).build();
        return new Order.Builder().addLineItem(new LineItem(item, 1, null)).build();
    }

    private DiscountRule flatDiscount(String amount, String description) {
        return new DiscountRule() {
            @Override public BigDecimal apply(Order order) { return new BigDecimal(amount); }
            @Override public String getDescription()       { return description; }
        };
    }

    @Test
    void calculate_noDiscounts_subtotalAndTaxCorrect() {
        PricingEngine engine = new PricingEngine(taxCalculator);
        Order order = orderWithTotal("10.00");
        PricingEngine.PricingResult result = engine.calculate(order);

        assertEquals(new BigDecimal("10.00"), result.getSubtotal());
        assertEquals(BigDecimal.ZERO, result.getDiscountAmount());
        assertEquals(new BigDecimal("1.00"), result.getTaxAmount());
        assertEquals(new BigDecimal("11.00"), result.getTotal());
    }

    @Test
    void calculate_singleDiscount_reducesTotalCorrectly() {
        PricingEngine engine = new PricingEngine(taxCalculator);
        engine.addDiscountRule(flatDiscount("2.00", "Test Discount"));
        Order order = orderWithTotal("10.00");
        PricingEngine.PricingResult result = engine.calculate(order);

        assertEquals(new BigDecimal("2.00"), result.getDiscountAmount());
        // tax on ($10 - $2) = $8 → $0.80; total = $8 + $0.80 = $8.80
        assertEquals(new BigDecimal("0.80"), result.getTaxAmount());
        assertEquals(new BigDecimal("8.80"), result.getTotal());
    }

    @Test
    void calculate_multipleDiscounts_stack() {
        PricingEngine engine = new PricingEngine(taxCalculator);
        engine.addDiscountRule(flatDiscount("1.00", "Discount A"));
        engine.addDiscountRule(flatDiscount("2.00", "Discount B"));
        Order order = orderWithTotal("10.00");
        PricingEngine.PricingResult result = engine.calculate(order);

        assertEquals(new BigDecimal("3.00"), result.getDiscountAmount());
    }

    @Test
    void calculate_discountCap_limitsTotal() {
        PricingEngine engine = new PricingEngine(taxCalculator,
            List.of(flatDiscount("5.00", "Big Discount")), new BigDecimal("2.00"));
        Order order = orderWithTotal("10.00");
        PricingEngine.PricingResult result = engine.calculate(order);

        assertEquals(new BigDecimal("2.00"), result.getDiscountAmount());
    }

    @Test
    void calculate_discountBelowCap_notCapped() {
        PricingEngine engine = new PricingEngine(taxCalculator,
            List.of(flatDiscount("1.00", "Small Discount")), new BigDecimal("5.00"));
        Order order = orderWithTotal("10.00");
        PricingEngine.PricingResult result = engine.calculate(order);

        assertEquals(new BigDecimal("1.00"), result.getDiscountAmount());
    }

    @Test
    void calculate_discountExceedsSubtotal_totalIsNotNegative() {
        PricingEngine engine = new PricingEngine(taxCalculator);
        engine.addDiscountRule(flatDiscount("100.00", "Huge Discount"));
        Order order = orderWithTotal("5.00");
        PricingEngine.PricingResult result = engine.calculate(order);

        assertFalse(result.getTotal().compareTo(BigDecimal.ZERO) < 0);
    }

    @Test
    void calculate_appliedDiscounts_listPopulated() {
        PricingEngine engine = new PricingEngine(taxCalculator);
        engine.addDiscountRule(flatDiscount("2.00", "Combo Deal"));
        Order order = orderWithTotal("10.00");
        PricingEngine.PricingResult result = engine.calculate(order);

        assertEquals(1, result.getAppliedDiscounts().size());
        assertTrue(result.getAppliedDiscounts().get(0).contains("Combo Deal"));
    }

    @Test
    void calculate_zeroValueDiscount_notInAppliedList() {
        DiscountRule zeroDiscount = new DiscountRule() {
            @Override public BigDecimal apply(Order order) { return BigDecimal.ZERO; }
            @Override public String getDescription()       { return "No Match"; }
        };
        PricingEngine engine = new PricingEngine(taxCalculator);
        engine.addDiscountRule(zeroDiscount);
        Order order = orderWithTotal("10.00");
        PricingEngine.PricingResult result = engine.calculate(order);

        assertTrue(result.getAppliedDiscounts().isEmpty());
    }
}
