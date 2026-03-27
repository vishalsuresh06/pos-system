package local.dev.order;

import local.dev.inventory.IngredientMap;
import local.dev.inventory.InventoryManager;
import local.dev.inventory.StockLevel;
import local.dev.menu.Category;
import local.dev.menu.MenuItem;
import local.dev.persistence.OrderStore;
import local.dev.pricing.PricingEngine;
import local.dev.pricing.TaxCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class OrderManagerTest {

    @TempDir Path tempDir;

    private OrderManager orderManager;
    private MenuItem burger;
    private InventoryManager inventoryManager;

    @BeforeEach
    void setUp() {
        burger = new MenuItem.Builder("Big Burger", new BigDecimal("8.99"), Category.ENTREE).build();

        IngredientMap ingredientMap = new IngredientMap();
        ingredientMap.addItemIngredients(burger.getId(), Map.of("patty", 1, "bun", 1));

        inventoryManager = new InventoryManager(ingredientMap);
        inventoryManager.addIngredient(new StockLevel("patty", 10, 2));
        inventoryManager.addIngredient(new StockLevel("bun",   10, 2));

        PricingEngine pricingEngine = new PricingEngine(new TaxCalculator(new BigDecimal("0.08")));
        OrderStore    orderStore    = new OrderStore(tempDir.toString());
        orderManager = new OrderManager(pricingEngine, inventoryManager, orderStore);
    }

    private Order buildSingleBurgerOrder() {
        LineItem li = new LineItem(burger, 1, null);
        return new Order.Builder().addLineItem(li).build();
    }

    @Test
    void submitOrder_valid_changesStatusToSubmitted() {
        Order submitted = orderManager.submitOrder(buildSingleBurgerOrder());
        assertEquals(OrderStatus.SUBMITTED, submitted.getStatus());
    }

    @Test
    void submitOrder_valid_storesOrder() {
        Order submitted = orderManager.submitOrder(buildSingleBurgerOrder());
        assertTrue(orderManager.getOrder(submitted.getId()).isPresent());
    }

    @Test
    void submitOrder_insufficientStock_throws() {
        inventoryManager.getAllStock().get("patty").deduct(10); // deplete stock
        assertThrows(IllegalStateException.class, () -> orderManager.submitOrder(buildSingleBurgerOrder()));
    }

    @Test
    void updateStatus_validTransition_succeeds() {
        Order order = orderManager.submitOrder(buildSingleBurgerOrder());
        Order preparing = orderManager.updateStatus(order.getId(), OrderStatus.PREPARING);
        assertEquals(OrderStatus.PREPARING, preparing.getStatus());
    }

    @Test
    void updateStatus_fullLifecycle_succeeds() {
        Order order = orderManager.submitOrder(buildSingleBurgerOrder());
        orderManager.updateStatus(order.getId(), OrderStatus.PREPARING);
        orderManager.updateStatus(order.getId(), OrderStatus.READY);
        orderManager.updateStatus(order.getId(), OrderStatus.COMPLETED);
        assertEquals(OrderStatus.COMPLETED, orderManager.getOrder(order.getId()).get().getStatus());
    }

    @Test
    void updateStatus_invalidTransition_throws() {
        Order order = orderManager.submitOrder(buildSingleBurgerOrder());
        // SUBMITTED -> COMPLETED is not a valid direct transition
        assertThrows(IllegalStateException.class, () ->
            orderManager.updateStatus(order.getId(), OrderStatus.COMPLETED));
    }

    @Test
    void updateStatus_cancelledOrder_cannotTransition() {
        Order order = orderManager.submitOrder(buildSingleBurgerOrder());
        orderManager.updateStatus(order.getId(), OrderStatus.CANCELLED);
        assertThrows(IllegalStateException.class, () ->
            orderManager.updateStatus(order.getId(), OrderStatus.PREPARING));
    }

    @Test
    void updateStatus_unknownOrderId_throws() {
        assertThrows(NoSuchElementException.class, () ->
            orderManager.updateStatus(UUID.randomUUID(), OrderStatus.PREPARING));
    }

    @Test
    void getOrdersByStatus_returnsMatchingOnly() {
        Order a = orderManager.submitOrder(buildSingleBurgerOrder());
        Order b = orderManager.submitOrder(buildSingleBurgerOrder());
        orderManager.updateStatus(a.getId(), OrderStatus.PREPARING);

        assertEquals(1, orderManager.getOrdersByStatus(OrderStatus.PREPARING).size());
        assertEquals(1, orderManager.getOrdersByStatus(OrderStatus.SUBMITTED).size());
    }

    @Test
    void getAllOrders_returnsAllTrackedOrders() {
        orderManager.submitOrder(buildSingleBurgerOrder());
        orderManager.submitOrder(buildSingleBurgerOrder());
        assertEquals(2, orderManager.getAllOrders().size());
    }

    @Test
    void getOrder_unknownId_returnsEmpty() {
        assertTrue(orderManager.getOrder(UUID.randomUUID()).isEmpty());
    }
}
