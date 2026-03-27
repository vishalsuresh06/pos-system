package local.dev.receipt;

import local.dev.menu.Category;
import local.dev.menu.Customization;
import local.dev.menu.MenuItem;
import local.dev.menu.Modification;
import local.dev.order.LineItem;
import local.dev.order.Order;
import local.dev.pricing.DiscountRule;
import local.dev.pricing.PricingEngine;
import local.dev.pricing.TaxCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReceiptGeneratorTest {

    private ReceiptGenerator receiptGenerator;
    private PricingEngine pricingEngine;
    private MenuItem burger;
    private Customization bacon;

    @BeforeEach
    void setUp() {
        TaxCalculator taxCalc = new TaxCalculator(new BigDecimal("0.10"));
        pricingEngine = new PricingEngine(taxCalc);
        receiptGenerator = new ReceiptGenerator(pricingEngine);

        bacon  = new Customization("Bacon", Modification.ADD, new BigDecimal("1.50"));
        burger = new MenuItem.Builder("Big Burger", new BigDecimal("8.99"), Category.ENTREE)
            .addCustomization(bacon).build();
    }

    private Order singleBurgerOrder() {
        return new Order.Builder().addLineItem(new LineItem(burger, 1, null)).build();
    }

    @Test
    void generate_containsOrderNumber() {
        Order order = singleBurgerOrder();
        String receipt = receiptGenerator.generate(order);
        assertTrue(receipt.contains(order.getId().toString().substring(0, 8).toUpperCase()));
    }

    @Test
    void generate_containsItemName() {
        String receipt = receiptGenerator.generate(singleBurgerOrder());
        assertTrue(receipt.contains("Big Burger"));
    }

    @Test
    void generate_containsSubtotal() {
        String receipt = receiptGenerator.generate(singleBurgerOrder());
        assertTrue(receipt.contains("Subtotal:"));
        assertTrue(receipt.contains("8.99"));
    }

    @Test
    void generate_containsTax() {
        String receipt = receiptGenerator.generate(singleBurgerOrder());
        assertTrue(receipt.contains("Tax:"));
    }

    @Test
    void generate_containsTotal() {
        String receipt = receiptGenerator.generate(singleBurgerOrder());
        assertTrue(receipt.contains("TOTAL:"));
    }

    @Test
    void generate_totalEqualsSubtotalPlusTax() {
        Order order = singleBurgerOrder();
        String receipt = receiptGenerator.generate(order);
        // $8.99 * 10% tax = $0.90; total = $9.89
        assertTrue(receipt.contains("9.89"));
    }

    @Test
    void generate_withDiscount_showsSavingsLine() {
        pricingEngine.addDiscountRule(new DiscountRule() {
            @Override public BigDecimal apply(Order o) { return new BigDecimal("2.00"); }
            @Override public String getDescription()   { return "Combo Discount"; }
        });
        String receipt = receiptGenerator.generate(singleBurgerOrder());
        assertTrue(receipt.contains("Savings:"));
        assertTrue(receipt.contains("Combo Discount"));
    }

    @Test
    void generate_noDiscount_savingsLineAbsent() {
        String receipt = receiptGenerator.generate(singleBurgerOrder());
        assertFalse(receipt.contains("Savings:"));
    }

    @Test
    void generate_customizationListed() {
        Order order = new Order.Builder().addLineItem(new LineItem(burger, 1, List.of(bacon))).build();
        String receipt = receiptGenerator.generate(order);
        assertTrue(receipt.contains("Bacon"));
        assertTrue(receipt.contains("+$1.50"));
    }

    @Test
    void generate_multipleItems_allListed() {
        MenuItem fries = new MenuItem.Builder("Large Fries", new BigDecimal("3.49"), Category.SIDE).build();
        Order order = new Order.Builder()
            .addLineItem(new LineItem(burger, 1, null))
            .addLineItem(new LineItem(fries,  2, null))
            .build();
        String receipt = receiptGenerator.generate(order);
        assertTrue(receipt.contains("Big Burger"));
        assertTrue(receipt.contains("Large Fries"));
        assertTrue(receipt.contains("x2"));
    }

    @Test
    void generate_containsStoreName() {
        String receipt = receiptGenerator.generate(singleBurgerOrder());
        assertTrue(receipt.contains("MANGO FAST FOOD"));
    }
}
