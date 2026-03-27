package local.dev;

import local.dev.inventory.IngredientMap;
import local.dev.inventory.InventoryManager;
import local.dev.inventory.StockLevel;
import local.dev.menu.*;
import local.dev.order.*;
import local.dev.persistence.InventoryStore;
import local.dev.persistence.OrderStore;
import local.dev.pricing.*;
import local.dev.receipt.KitchenTicket;
import local.dev.receipt.ReceiptGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class OrderFlowIntegrationTest {

    @TempDir Path tempDir;

    private MenuRegistry menuRegistry;
    private MenuItem burger;
    private MenuItem fries;
    private MenuItem cola;
    private Customization addBacon;
    private OrderManager orderManager;
    private PricingEngine pricingEngine;
    private InventoryManager inventoryManager;
    private ReceiptGenerator receiptGenerator;
    private KitchenTicket kitchenTicket;

    @BeforeEach
    void setUp() {
        // Menu
        menuRegistry = new MenuRegistry();
        addBacon = new Customization("Bacon", Modification.ADD, new BigDecimal("1.50"));

        burger = new MenuItem.Builder("Big Burger", new BigDecimal("8.99"), Category.ENTREE)
            .highProtein(true).addCustomization(addBacon).build();
        fries  = new MenuItem.Builder("Large Fries", new BigDecimal("3.49"), Category.SIDE).build();
        cola   = new MenuItem.Builder("Cola", new BigDecimal("1.99"), Category.DRINK).build();

        menuRegistry.addMenuItem(burger);
        menuRegistry.addMenuItem(fries);
        menuRegistry.addMenuItem(cola);

        // Inventory
        IngredientMap ingredientMap = new IngredientMap();
        ingredientMap.addItemIngredients(burger.getId(), Map.of("patty", 1, "bun", 1));
        ingredientMap.addItemIngredients(fries.getId(),  Map.of("potatoes", 1));
        ingredientMap.addItemIngredients(cola.getId(),   Map.of("cola_syrup", 1));
        ingredientMap.addCustomizationIngredients("Bacon", Map.of("bacon_strip", 1));

        inventoryManager = new InventoryManager(ingredientMap);
        inventoryManager.addIngredient(new StockLevel("patty",       5, 1));
        inventoryManager.addIngredient(new StockLevel("bun",         5, 1));
        inventoryManager.addIngredient(new StockLevel("potatoes",    5, 1));
        inventoryManager.addIngredient(new StockLevel("cola_syrup",  5, 1));
        inventoryManager.addIngredient(new StockLevel("bacon_strip", 3, 1));

        // Pricing — combo discount
        TaxCalculator taxCalc = new TaxCalculator(new BigDecimal("0.08"));
        pricingEngine = new PricingEngine(taxCalc);
        pricingEngine.addDiscountRule(new DiscountRule() {
            @Override
            public BigDecimal apply(Order order) {
                boolean hasEntree = order.getLineItems().stream().anyMatch(li -> li.getMenuItem().getCategory() == Category.ENTREE);
                boolean hasSide   = order.getLineItems().stream().anyMatch(li -> li.getMenuItem().getCategory() == Category.SIDE);
                boolean hasDrink  = order.getLineItems().stream().anyMatch(li -> li.getMenuItem().getCategory() == Category.DRINK);
                return (hasEntree && hasSide && hasDrink) ? new BigDecimal("2.00") : BigDecimal.ZERO;
            }
            @Override public String getDescription() { return "Combo Meal Discount"; }
        });

        OrderStore orderStore = new OrderStore(tempDir.resolve("orders").toString());
        orderManager = new OrderManager(pricingEngine, inventoryManager, orderStore);
        receiptGenerator = new ReceiptGenerator(pricingEngine);
        kitchenTicket = new KitchenTicket();
    }

    @Test
    void fullOrderLifecycle_completesSuccessfully() {
        OrderBuilder builder = new OrderBuilder(menuRegistry);
        builder.addItem(burger.getId(), 1);

        Order submitted = orderManager.submitOrder(builder.build());
        assertEquals(OrderStatus.SUBMITTED, submitted.getStatus());

        orderManager.updateStatus(submitted.getId(), OrderStatus.PREPARING);
        orderManager.updateStatus(submitted.getId(), OrderStatus.READY);
        orderManager.updateStatus(submitted.getId(), OrderStatus.COMPLETED);

        assertEquals(OrderStatus.COMPLETED, orderManager.getOrder(submitted.getId()).get().getStatus());
    }

    @Test
    void submitOrder_deductsInventory() {
        int pattyBefore = inventoryManager.getStockLevel("patty").getQuantity();

        OrderBuilder builder = new OrderBuilder(menuRegistry);
        builder.addItem(burger.getId(), 2);
        orderManager.submitOrder(builder.build());

        assertEquals(pattyBefore - 2, inventoryManager.getStockLevel("patty").getQuantity());
    }

    @Test
    void submitOrder_withCustomization_deductsCustomizationIngredients() {
        int baconBefore = inventoryManager.getStockLevel("bacon_strip").getQuantity();

        OrderBuilder builder = new OrderBuilder(menuRegistry);
        builder.addItem(burger.getId(), 1, List.of(addBacon));
        orderManager.submitOrder(builder.build());

        assertEquals(baconBefore - 1, inventoryManager.getStockLevel("bacon_strip").getQuantity());
    }

    @Test
    void submitOrder_insufficientStock_throwsAndDoesNotDeduct() {
        // Deplete patties
        inventoryManager.getAllStock().get("patty").deduct(5);

        OrderBuilder builder = new OrderBuilder(menuRegistry);
        builder.addItem(burger.getId(), 1);

        assertThrows(IllegalStateException.class, () -> orderManager.submitOrder(builder.build()));
        // Inventory should be unchanged
        assertEquals(0, inventoryManager.getStockLevel("patty").getQuantity());
    }

    @Test
    void comboDiscount_appliedOnReceiptWhenEntreeSideDrinkPresent() {
        OrderBuilder builder = new OrderBuilder(menuRegistry);
        builder.addItem(burger.getId(), 1);
        builder.addItem(fries.getId(),  1);
        builder.addItem(cola.getId(),   1);

        Order order = orderManager.submitOrder(builder.build());
        String receipt = receiptGenerator.generate(order);

        assertTrue(receipt.contains("Combo Meal Discount"));
        assertTrue(receipt.contains("Savings:"));
    }

    @Test
    void comboDiscount_notApplied_whenMissingDrink() {
        OrderBuilder builder = new OrderBuilder(menuRegistry);
        builder.addItem(burger.getId(), 1);
        builder.addItem(fries.getId(),  1);

        Order order = orderManager.submitOrder(builder.build());
        String receipt = receiptGenerator.generate(order);

        assertFalse(receipt.contains("Savings:"));
    }

    @Test
    void kitchenTicket_showsCorrectItems() {
        OrderBuilder builder = new OrderBuilder(menuRegistry);
        builder.addItem(burger.getId(), 2);
        builder.addItem(fries.getId(),  1);

        Order order = orderManager.submitOrder(builder.build());
        String ticket = kitchenTicket.generate(order);

        assertTrue(ticket.contains("2x Big Burger"));
        assertTrue(ticket.contains("1x Large Fries"));
        assertFalse(ticket.contains("$")); // no prices on kitchen ticket
    }

    @Test
    void cancelOrder_stopsAtCancelled() {
        OrderBuilder builder = new OrderBuilder(menuRegistry);
        builder.addItem(burger.getId(), 1);

        Order submitted = orderManager.submitOrder(builder.build());
        orderManager.updateStatus(submitted.getId(), OrderStatus.CANCELLED);
        assertEquals(OrderStatus.CANCELLED, submitted.getStatus());

        assertThrows(IllegalStateException.class, () ->
            orderManager.updateStatus(submitted.getId(), OrderStatus.PREPARING));
    }

    @Test
    void persistenceIntegration_savedOrderAppearsInLoadAllRaw() {
        OrderBuilder builder = new OrderBuilder(menuRegistry);
        builder.addItem(burger.getId(), 1);
        orderManager.submitOrder(builder.build());

        OrderStore store = new OrderStore(tempDir.resolve("orders").toString());
        assertEquals(1, store.loadAllRaw().size());
    }

    @Test
    void inventoryPersistence_savedAndLoaded() {
        InventoryStore invStore = new InventoryStore(tempDir.resolve("inventory").toString());
        invStore.saveStock(inventoryManager);

        var loaded = invStore.loadStock();
        assertEquals(inventoryManager.getAllStock().size(), loaded.size());
    }
}
