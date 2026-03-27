package local.dev.inventory;

import local.dev.menu.Category;
import local.dev.menu.Customization;
import local.dev.menu.MenuItem;
import local.dev.menu.Modification;
import local.dev.order.LineItem;
import local.dev.order.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class InventoryManagerTest {

    private InventoryManager inventoryManager;
    private MenuItem burger;
    private Customization bacon;

    @BeforeEach
    void setUp() {
        bacon  = new Customization("Bacon", Modification.ADD, new BigDecimal("1.50"));
        burger = new MenuItem.Builder("Big Burger", new BigDecimal("8.99"), Category.ENTREE)
            .addCustomization(bacon).build();

        IngredientMap ingredientMap = new IngredientMap();
        ingredientMap.addItemIngredients(burger.getId(), Map.of("patty", 1, "bun", 1));
        ingredientMap.addCustomizationIngredients("Bacon", Map.of("bacon_strip", 1));

        inventoryManager = new InventoryManager(ingredientMap);
        inventoryManager.addIngredient(new StockLevel("patty",      10, 2));
        inventoryManager.addIngredient(new StockLevel("bun",        10, 2));
        inventoryManager.addIngredient(new StockLevel("bacon_strip", 5, 1));
    }

    private Order orderWith(int burgerQty) {
        return new Order.Builder().addLineItem(new LineItem(burger, burgerQty, null)).build();
    }

    private Order orderWithBacon(int qty) {
        return new Order.Builder().addLineItem(new LineItem(burger, qty, List.of(bacon))).build();
    }

    @Test
    void isItemInStock_sufficientIngredients_returnsTrue() {
        assertTrue(inventoryManager.isItemInStock(burger.getId()));
    }

    @Test
    void isItemInStock_depletedIngredient_returnsFalse() {
        inventoryManager.getAllStock().get("patty").deduct(10);
        assertFalse(inventoryManager.isItemInStock(burger.getId()));
    }

    @Test
    void canFulfillOrder_sufficientStock_returnsTrue() {
        assertTrue(inventoryManager.canFulfillOrder(orderWith(5)));
    }

    @Test
    void canFulfillOrder_insufficientStock_returnsFalse() {
        assertFalse(inventoryManager.canFulfillOrder(orderWith(11)));
    }

    @Test
    void canFulfillOrder_withCustomization_checksCustomizationIngredients() {
        assertTrue(inventoryManager.canFulfillOrder(orderWithBacon(5)));
        assertFalse(inventoryManager.canFulfillOrder(orderWithBacon(6))); // needs 6 bacon_strips, only 5
    }

    @Test
    void deductForOrder_reducesStock() {
        inventoryManager.deductForOrder(orderWith(3));
        assertEquals(7, inventoryManager.getStockLevel("patty").getQuantity());
        assertEquals(7, inventoryManager.getStockLevel("bun").getQuantity());
    }

    @Test
    void deductForOrder_withCustomization_reducesCustomizationIngredients() {
        inventoryManager.deductForOrder(orderWithBacon(2));
        assertEquals(8, inventoryManager.getStockLevel("patty").getQuantity());
        assertEquals(3, inventoryManager.getStockLevel("bacon_strip").getQuantity());
    }

    @Test
    void deductForOrder_insufficientStock_throws() {
        assertThrows(IllegalStateException.class, () ->
            inventoryManager.deductForOrder(orderWith(11)));
    }

    @Test
    void getOutOfStockItemIds_depletedItem_includesInResult() {
        inventoryManager.getAllStock().get("patty").deduct(10);
        Set<java.util.UUID> outOfStock = inventoryManager.getOutOfStockItemIds(Set.of(burger.getId()));
        assertTrue(outOfStock.contains(burger.getId()));
    }

    @Test
    void getOutOfStockItemIds_inStockItem_notIncluded() {
        Set<java.util.UUID> outOfStock = inventoryManager.getOutOfStockItemIds(Set.of(burger.getId()));
        assertFalse(outOfStock.contains(burger.getId()));
    }

    @Test
    void getLowStockAlerts_returnsLowStockIngredients() {
        inventoryManager.getAllStock().get("patty").deduct(9); // patty now at 1, threshold is 2 → low
        List<StockLevel> alerts = inventoryManager.getLowStockAlerts();
        assertTrue(alerts.stream().anyMatch(s -> s.getIngredientName().equals("patty")));
    }

    @Test
    void getLowStockAlerts_noLowStock_returnsEmpty() {
        List<StockLevel> alerts = inventoryManager.getLowStockAlerts();
        assertTrue(alerts.isEmpty());
    }
}
