package local.dev;

import local.dev.inventory.IngredientMap;
import local.dev.inventory.InventoryManager;
import local.dev.inventory.StockLevel;
import local.dev.menu.Category;
import local.dev.menu.Customization;
import local.dev.menu.MenuItem;
import local.dev.menu.MenuRegistry;
import local.dev.menu.Modification;
import local.dev.order.LineItem;
import local.dev.order.Order;
import local.dev.order.OrderBuilder;
import local.dev.order.OrderManager;
import local.dev.order.OrderStatus;
import local.dev.persistence.InventoryStore;
import local.dev.persistence.OrderStore;
import local.dev.pricing.DiscountRule;
import local.dev.pricing.PricingEngine;
import local.dev.pricing.TaxCalculator;
import local.dev.receipt.KitchenTicket;
import local.dev.receipt.ReceiptGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class BaseTests {

    @Nested
    class MenuItemTest {

        private MenuItem.Builder baseBuilder() {
            return new MenuItem.Builder("Big Burger", new BigDecimal("8.99"), Category.ENTREE);
        }

        @Test
        void build_withRequiredFields_succeeds() {
            MenuItem item = baseBuilder().build();
            assertEquals("Big Burger", item.getName());
            assertEquals(new BigDecimal("8.99"), item.getPrice());
            assertEquals(Category.ENTREE, item.getCategory());
            assertNotNull(item.getId());
        }

        @Test
        void build_missingName_throws() {
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new MenuItem.Builder(null, new BigDecimal("8.99"), Category.ENTREE).build());
            assertNotNull(exception);
        }

        @Test
        void build_missingPrice_throws() {
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new MenuItem.Builder("Burger", null, Category.ENTREE).build());
            assertNotNull(exception);
        }

        @Test
        void build_missingCategory_throws() {
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new MenuItem.Builder("Burger", new BigDecimal("8.99"), null).build());
            assertNotNull(exception);
        }

        @Test
        void isAvailableAt_withinWindow_returnsTrue() {
            MenuItem item = baseBuilder()
                .availableFrom(LocalTime.of(9, 0))
                .availableTo(LocalTime.of(17, 0))
                .build();
            assertTrue(item.isAvailableAt(LocalTime.of(12, 0)));
            assertTrue(item.isAvailableAt(LocalTime.of(9, 0)));
            assertTrue(item.isAvailableAt(LocalTime.of(17, 0)));
        }

        @Test
        void isAvailableAt_outsideWindow_returnsFalse() {
            MenuItem item = baseBuilder()
                .availableFrom(LocalTime.of(9, 0))
                .availableTo(LocalTime.of(17, 0))
                .build();
            assertFalse(item.isAvailableAt(LocalTime.of(8, 59)));
            assertFalse(item.isAvailableAt(LocalTime.of(17, 1)));
        }

        @Test
        void isInSeason_withinDates_returnsTrue() {
            MenuItem item = baseBuilder()
                .seasonalStart(LocalDate.of(2026, 1, 1))
                .seasonalEnd(LocalDate.of(2026, 12, 31))
                .build();
            assertTrue(item.isInSeason(LocalDate.of(2026, 6, 15)));
            assertTrue(item.isInSeason(LocalDate.of(2026, 1, 1)));
            assertTrue(item.isInSeason(LocalDate.of(2026, 12, 31)));
        }

        @Test
        void isInSeason_outsideDates_returnsFalse() {
            MenuItem item = baseBuilder()
                .seasonalStart(LocalDate.of(2026, 6, 1))
                .seasonalEnd(LocalDate.of(2026, 8, 31))
                .build();
            assertFalse(item.isInSeason(LocalDate.of(2026, 5, 31)));
            assertFalse(item.isInSeason(LocalDate.of(2026, 9, 1)));
        }

        @Test
        void isInSeason_noSeasonalDates_alwaysReturnsTrue() {
            MenuItem item = baseBuilder().build();
            assertTrue(item.isInSeason(LocalDate.of(2020, 1, 1)));
            assertTrue(item.isInSeason(LocalDate.of(2099, 12, 31)));
        }

        @Test
        void dietaryFlags_defaultToFalse() {
            MenuItem item = baseBuilder().build();
            assertFalse(item.isKidsFriendly());
            assertFalse(item.isVegetarian());
            assertFalse(item.isHighProtein());
        }

        @Test
        void dietaryFlags_setCorrectly() {
            MenuItem item = baseBuilder().kidsFriendly(true).vegetarian(true).highProtein(true).build();
            assertTrue(item.isKidsFriendly());
            assertTrue(item.isVegetarian());
            assertTrue(item.isHighProtein());
        }

        @Test
        void addCustomization_appearsInSet() {
            Customization bacon = new Customization("Bacon", Modification.ADD, new BigDecimal("1.50"));
            MenuItem item = baseBuilder().addCustomization(bacon).build();
            assertTrue(item.getCustomizations().contains(bacon));
        }
    }

    @Nested
    class CustomizationTest {

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

    @Nested
    class MenuRegistryTest {

        private MenuRegistry registry;
        private MenuItem burger;
        private MenuItem fries;
        private MenuItem cola;

        @BeforeEach
        void setUp() {
            registry = new MenuRegistry();
            burger = new MenuItem.Builder("Big Burger", new BigDecimal("8.99"), Category.ENTREE).build();
            fries  = new MenuItem.Builder("Large Fries", new BigDecimal("3.49"), Category.SIDE).build();
            cola   = new MenuItem.Builder("Cola", new BigDecimal("1.99"), Category.DRINK).build();
            registry.addMenuItem(burger);
            registry.addMenuItem(fries);
            registry.addMenuItem(cola);
        }

        @Test
        void getMenuItemById_existingItem_returnsItem() {
            assertEquals(burger, registry.getMenuItemById(burger.getId()));
        }

        @Test
        void getMenuItemById_unknownId_returnsNull() {
            assertNull(registry.getMenuItemById(UUID.randomUUID()));
        }

        @Test
        void getMenuItemByName_existingItem_returnsItem() {
            assertEquals(fries, registry.getMenuItemByName("Large Fries"));
        }

        @Test
        void getMenuItemByName_unknownName_returnsNull() {
            assertNull(registry.getMenuItemByName("Nonexistent"));
        }

        @Test
        void getMenuItemsByCategory_returnsOnlyThatCategory() {
            List<MenuItem> entrees = registry.getMenuItemsByCategory(Category.ENTREE);
            assertEquals(1, entrees.size());
            assertTrue(entrees.contains(burger));
        }

        @Test
        void getMenuItemsByCategory_noItemsInCategory_returnsEmpty() {
            List<MenuItem> desserts = registry.getMenuItemsByCategory(Category.DESSERT);
            assertTrue(desserts.isEmpty());
        }

        @Test
        void removeMenuItem_existingItem_removesFromAllLookups() {
            assertTrue(registry.removeMenuItem(burger.getId()));
            assertNull(registry.getMenuItemById(burger.getId()));
            assertNull(registry.getMenuItemByName("Big Burger"));
            assertTrue(registry.getMenuItemsByCategory(Category.ENTREE).isEmpty());
        }

        @Test
        void removeMenuItem_unknownId_returnsFalse() {
            assertFalse(registry.removeMenuItem(UUID.randomUUID()));
        }

        @Test
        void getAvailableMenuItems_byTime_filtersUnavailable() {
            MenuItem breakfastItem = new MenuItem.Builder("Pancakes", new BigDecimal("5.99"), Category.ENTREE)
                .availableFrom(LocalTime.of(6, 0))
                .availableTo(LocalTime.of(11, 0))
                .build();
            registry.addMenuItem(breakfastItem);

            List<MenuItem> available = registry.getAvailableMenuItems(LocalTime.of(14, 0), LocalDate.now());
            assertTrue(available.contains(burger));
            assertFalse(available.contains(breakfastItem));
        }

        @Test
        void getAvailableMenuItems_byCategoryAndTime_returnsCorrect() {
            List<MenuItem> available = registry.getAvailableMenuItems(LocalTime.NOON, LocalDate.now(), Category.SIDE);
            assertEquals(1, available.size());
            assertTrue(available.contains(fries));
        }

        @Test
        void getAvailableMenuItems_outOfSeason_excluded() {
            MenuItem seasonal = new MenuItem.Builder("Pumpkin Soup", new BigDecimal("4.99"), Category.SIDE)
                .seasonalStart(LocalDate.of(2020, 1, 1))
                .seasonalEnd(LocalDate.of(2020, 3, 31))
                .build();
            registry.addMenuItem(seasonal);

            List<MenuItem> available = registry.getAvailableMenuItems(LocalTime.NOON, LocalDate.now());
            assertFalse(available.contains(seasonal));
        }
    }

    @Nested
    class LineItemTest {

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

    @Nested
    class OrderTest {

        private LineItem lineItem(String name, String price) {
            MenuItem item = new MenuItem.Builder(name, new BigDecimal(price), Category.ENTREE).build();
            return new LineItem(item, 1, null);
        }

        @Test
        void build_subtotalSumsAllLineItems() {
            LineItem a = lineItem("Burger", "8.99");
            LineItem b = lineItem("Fries",  "3.49");
            Order order = new Order.Builder().addLineItem(a).addLineItem(b).build();
            assertEquals(new BigDecimal("12.48"), order.getSubTotalAmount());
        }

        @Test
        void build_withTaxRate_computesTaxAndTotal() {
            LineItem a = lineItem("Burger", "10.00");
            Order order = new Order.Builder()
                .addLineItem(a)
                .taxRate(new BigDecimal("0.10"))
                .build();
            assertEquals(0, new BigDecimal("1.00").compareTo(order.getTaxAmount()));
            assertEquals(0, new BigDecimal("11.00").compareTo(order.getTotalAmount()));
        }

        @Test
        void build_noTaxRate_taxIsZero() {
            LineItem a = lineItem("Burger", "8.99");
            Order order = new Order.Builder().addLineItem(a).build();
            assertEquals(0, BigDecimal.ZERO.compareTo(order.getTaxAmount()));
            assertEquals(0, new BigDecimal("8.99").compareTo(order.getTotalAmount()));
        }

        @Test
        void build_defaultStatus_isOpen() {
            Order order = new Order.Builder().addLineItem(lineItem("Burger", "8.99")).build();
            assertEquals(OrderStatus.OPEN, order.getStatus());
        }

        @Test
        void build_noLineItems_throws() {
            assertThrows(IllegalStateException.class, () -> new Order.Builder().build());
        }

        @Test
        void build_completedBeforeCreation_throws() {
            LocalDateTime now = LocalDateTime.now();
            assertThrows(IllegalStateException.class, () ->
                new Order.Builder()
                    .addLineItem(lineItem("Burger", "8.99"))
                    .orderCreation(now)
                    .orderCompleted(now.minusMinutes(5))
                    .build());
        }

        @Test
        void updateStatus_changesStatus() {
            Order order = new Order.Builder().addLineItem(lineItem("Burger", "8.99")).build();
            order.updateStatus(OrderStatus.SUBMITTED);
            assertEquals(OrderStatus.SUBMITTED, order.getStatus());
        }

        @Test
        void updateStatus_nullStatus_throws() {
            Order order = new Order.Builder().addLineItem(lineItem("Burger", "8.99")).build();
            assertThrows(NullPointerException.class, () -> order.updateStatus(null));
        }

        @Test
        void lineItems_areImmutable() {
            LineItem a = lineItem("Burger", "8.99");
            Order order = new Order.Builder().addLineItem(a).build();
            assertThrows(UnsupportedOperationException.class, () ->
                order.getLineItems().add(lineItem("Fries", "3.49")));
        }

        @Test
        void build_lineItemsViaList_capturesAll() {
            List<LineItem> items = List.of(lineItem("A", "1.00"), lineItem("B", "2.00"), lineItem("C", "3.00"));
            Order order = new Order.Builder().lineItems(items).build();
            assertEquals(3, order.getLineItems().size());
            assertEquals(new BigDecimal("6.00"), order.getSubTotalAmount());
        }
    }

    @Nested
    class OrderBuilderTest {

        private MenuRegistry registry;
        private MenuItem burger;
        private Customization bacon;
        private Customization noPickles;
        private OrderBuilder orderBuilder;

        @BeforeEach
        void setUp() {
            registry = new MenuRegistry();
            bacon    = new Customization("Bacon",   Modification.ADD,    new BigDecimal("1.50"));
            noPickles = new Customization("Pickles", Modification.REMOVE, BigDecimal.ZERO);

            burger = new MenuItem.Builder("Big Burger", new BigDecimal("8.99"), Category.ENTREE)
                .addCustomization(bacon)
                .addCustomization(noPickles)
                .build();
            registry.addMenuItem(burger);
            orderBuilder = new OrderBuilder(registry);
        }

        @Test
        void addItem_byId_addsToLineItems() {
            orderBuilder.addItem(burger.getId(), 2);
            assertEquals(1, orderBuilder.getLineItems().size());
            assertEquals(2, orderBuilder.getLineItems().get(0).getQuantity());
        }

        @Test
        void addItem_withAllowedCustomization_succeeds() {
            assertDoesNotThrow(() -> orderBuilder.addItem(burger.getId(), 1, List.of(bacon)));
        }

        @Test
        void addItem_withDisallowedCustomization_throws() {
            Customization notAllowed = new Customization("Extra Bun", Modification.ADD, new BigDecimal("0.50"));
            assertThrows(IllegalArgumentException.class, () ->
                orderBuilder.addItem(burger.getId(), 1, List.of(notAllowed)));
        }

        @Test
        void addItem_unknownId_throws() {
            assertThrows(IllegalArgumentException.class, () ->
                orderBuilder.addItem(UUID.randomUUID(), 1));
        }

        @Test
        void removeItem_existingLineItem_removesIt() {
            LineItem li = orderBuilder.addItem(burger.getId(), 1);
            assertTrue(orderBuilder.removeItem(li.getId()));
            assertTrue(orderBuilder.isEmpty());
        }

        @Test
        void removeItem_unknownId_returnsFalse() {
            assertFalse(orderBuilder.removeItem(UUID.randomUUID()));
        }

        @Test
        void build_withItems_returnsOrder() {
            orderBuilder.addItem(burger.getId(), 1);
            Order order = orderBuilder.build();
            assertNotNull(order);
            assertEquals(1, order.getLineItems().size());
            assertEquals(OrderStatus.OPEN, order.getStatus());
        }

        @Test
        void build_withNoItems_throws() {
            assertThrows(IllegalStateException.class, () -> orderBuilder.build());
        }

        @Test
        void isEmpty_noItems_returnsTrue() {
            assertTrue(orderBuilder.isEmpty());
        }

        @Test
        void isEmpty_afterAddingItem_returnsFalse() {
            orderBuilder.addItem(burger.getId(), 1);
            assertFalse(orderBuilder.isEmpty());
        }

        @Test
        void getLineItems_isUnmodifiable() {
            orderBuilder.addItem(burger.getId(), 1);
            assertThrows(UnsupportedOperationException.class, () ->
                orderBuilder.getLineItems().clear());
        }
    }

    @Nested
    class OrderManagerTest {

        @TempDir Path tempDir;

        private OrderManager orderManager;
        private MenuItem burger;
        private InventoryManager inventoryManager;

        @BeforeEach
        public void setUp() {
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
            Exception exception = assertThrows(IllegalStateException.class, () -> orderManager.submitOrder(buildSingleBurgerOrder()));
            assertNotNull(exception);
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
            Exception exception = assertThrows(IllegalStateException.class, () ->
                orderManager.updateStatus(order.getId(), OrderStatus.COMPLETED));
            assertNotNull(exception);
        }

        @Test
        void updateStatus_cancelledOrder_cannotTransition() {
            Order order = orderManager.submitOrder(buildSingleBurgerOrder());
            orderManager.updateStatus(order.getId(), OrderStatus.CANCELLED);
            Exception exception = assertThrows(IllegalStateException.class, () ->
                orderManager.updateStatus(order.getId(), OrderStatus.PREPARING));
            assertNotNull(exception);
        }

        @Test
        void updateStatus_unknownOrderId_throws() {
            Exception exception = assertThrows(NoSuchElementException.class, () ->
                orderManager.updateStatus(UUID.randomUUID(), OrderStatus.PREPARING));
            assertNotNull(exception);
        }

        @Test
        void getOrdersByStatus_returnsMatchingOnly() {
            Order a = orderManager.submitOrder(buildSingleBurgerOrder());
            orderManager.submitOrder(buildSingleBurgerOrder());
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

    @Nested
    class StockLevelTest {

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

    @Nested
    class IngredientMapTest {

        private IngredientMap ingredientMap;
        private UUID burgerId;

        @BeforeEach
        void setUp() {
            ingredientMap = new IngredientMap();
            burgerId = UUID.randomUUID();
            ingredientMap.addItemIngredients(burgerId, Map.of("patty", 1, "bun", 1, "lettuce", 1));
            ingredientMap.addCustomizationIngredients("Bacon", Map.of("bacon_strip", 1));
        }

        @Test
        void getIngredientsForItem_knownId_returnsCorrectMap() {
            Map<String, Integer> ingredients = ingredientMap.getIngredientsForItem(burgerId);
            assertEquals(3, ingredients.size());
            assertEquals(1, ingredients.get("patty"));
            assertEquals(1, ingredients.get("bun"));
        }

        @Test
        void getIngredientsForItem_unknownId_returnsEmptyMap() {
            Map<String, Integer> ingredients = ingredientMap.getIngredientsForItem(UUID.randomUUID());
            assertTrue(ingredients.isEmpty());
        }

        @Test
        void getIngredientsForCustomization_knownName_returnsCorrectMap() {
            Map<String, Integer> ingredients = ingredientMap.getIngredientsForCustomization("Bacon");
            assertEquals(1, ingredients.get("bacon_strip"));
        }

        @Test
        void getIngredientsForCustomization_unknownName_returnsEmptyMap() {
            assertTrue(ingredientMap.getIngredientsForCustomization("Nonexistent").isEmpty());
        }

        @Test
        void hasMappingForItem_knownId_returnsTrue() {
            assertTrue(ingredientMap.hasMappingForItem(burgerId));
        }

        @Test
        void hasMappingForItem_unknownId_returnsFalse() {
            assertFalse(ingredientMap.hasMappingForItem(UUID.randomUUID()));
        }

        @Test
        void getAllItemIngredients_isUnmodifiable() {
            assertThrows(UnsupportedOperationException.class, () ->
                ingredientMap.getAllItemIngredients().put(UUID.randomUUID(), Map.of()));
        }

        @Test
        void getAllCustomizationIngredients_isUnmodifiable() {
            assertThrows(UnsupportedOperationException.class, () ->
                ingredientMap.getAllCustomizationIngredients().put("New", Map.of()));
        }

        @Test
        void addItemIngredients_overridesExistingEntry() {
            ingredientMap.addItemIngredients(burgerId, Map.of("patty", 2));
            assertEquals(2, ingredientMap.getIngredientsForItem(burgerId).get("patty"));
            assertEquals(1, ingredientMap.getIngredientsForItem(burgerId).size());
        }
    }

    @Nested
    class InventoryManagerTest {

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
            Set<UUID> outOfStock = inventoryManager.getOutOfStockItemIds(Set.of(burger.getId()));
            assertTrue(outOfStock.contains(burger.getId()));
        }

        @Test
        void getOutOfStockItemIds_inStockItem_notIncluded() {
            Set<UUID> outOfStock = inventoryManager.getOutOfStockItemIds(Set.of(burger.getId()));
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

    @Nested
    class TaxCalculatorTest {

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

    @Nested
    class PricingEngineTest {

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

    @Nested
    class ReceiptGeneratorTest {

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

    @Nested
    class KitchenTicketTest {

        private KitchenTicket kitchenTicket;
        private MenuItem burger;
        private Customization addBacon;
        private Customization noPickles;
        private Customization wheatBun;

        @BeforeEach
        void setUp() {
            kitchenTicket = new KitchenTicket();
            addBacon   = new Customization("Bacon",     Modification.ADD,        new BigDecimal("1.50"));
            noPickles  = new Customization("Pickles",   Modification.REMOVE,     BigDecimal.ZERO);
            wheatBun   = new Customization("Wheat Bun", Modification.SUBSTITUTE, BigDecimal.ZERO);
            burger = new MenuItem.Builder("Big Burger", new BigDecimal("8.99"), Category.ENTREE)
                .addCustomization(addBacon).addCustomization(noPickles).addCustomization(wheatBun).build();
        }

        private Order orderWith(LineItem... items) {
            Order.Builder b = new Order.Builder();
            for (LineItem li : items) b.addLineItem(li);
            return b.build();
        }

        @Test
        void generate_containsOrderNumber() {
            Order order = orderWith(new LineItem(burger, 1, null));
            String ticket = kitchenTicket.generate(order);
            assertTrue(ticket.contains(order.getId().toString().substring(0, 8).toUpperCase()));
        }

        @Test
        void generate_containsItemNameWithQuantity() {
            Order order = orderWith(new LineItem(burger, 3, null));
            String ticket = kitchenTicket.generate(order);
            assertTrue(ticket.contains("3x Big Burger"));
        }

        @Test
        void generate_addCustomization_hasPlusPrefix() {
            Order order = orderWith(new LineItem(burger, 1, List.of(addBacon)));
            String ticket = kitchenTicket.generate(order);
            assertTrue(ticket.contains("  + Bacon"));
        }

        @Test
        void generate_removeCustomization_hasMinusPrefix() {
            Order order = orderWith(new LineItem(burger, 1, List.of(noPickles)));
            String ticket = kitchenTicket.generate(order);
            assertTrue(ticket.contains("  - Pickles"));
        }

        @Test
        void generate_substituteCustomization_hasTildePrefix() {
            Order order = orderWith(new LineItem(burger, 1, List.of(wheatBun)));
            String ticket = kitchenTicket.generate(order);
            assertTrue(ticket.contains("  ~ Wheat Bun"));
        }

        @Test
        void generate_noPricesShown() {
            Order order = orderWith(new LineItem(burger, 1, List.of(addBacon)));
            String ticket = kitchenTicket.generate(order);
            assertFalse(ticket.contains("$"));
        }

        @Test
        void generate_multipleItems_allIncluded() {
            MenuItem fries = new MenuItem.Builder("Large Fries", new BigDecimal("3.49"), Category.SIDE).build();
            Order order = orderWith(
                new LineItem(burger, 2, null),
                new LineItem(fries,  1, null)
            );
            String ticket = kitchenTicket.generate(order);
            assertTrue(ticket.contains("2x Big Burger"));
            assertTrue(ticket.contains("1x Large Fries"));
        }
    }

    @Nested
    class OrderStoreTest {

        @TempDir Path tempDir;

        private OrderStore orderStore;
        private MenuItem burger;

        @BeforeEach
        void setUp() {
            orderStore = new OrderStore(tempDir.toString());
            burger = new MenuItem.Builder("Big Burger", new BigDecimal("8.99"), Category.ENTREE).build();
        }

        private Order singleItemOrder() {
            return new Order.Builder().addLineItem(new LineItem(burger, 1, null)).build();
        }

        @Test
        void save_createsFileOnDisk() {
            Order order = singleItemOrder();
            orderStore.save(order);
            assertTrue(Files.exists(tempDir.resolve(order.getId() + ".txt")));
        }

        @Test
        void save_fileContainsOrderId() throws Exception {
            Order order = singleItemOrder();
            orderStore.save(order);
            String content = Files.readString(tempDir.resolve(order.getId() + ".txt"));
            assertTrue(content.contains(order.getId().toString()));
        }

        @Test
        void save_fileContainsStatus() throws Exception {
            Order order = singleItemOrder();
            orderStore.save(order);
            String content = Files.readString(tempDir.resolve(order.getId() + ".txt"));
            assertTrue(content.contains("STATUS=OPEN"));
        }

        @Test
        void save_fileContainsItemName() throws Exception {
            Order order = singleItemOrder();
            orderStore.save(order);
            String content = Files.readString(tempDir.resolve(order.getId() + ".txt"));
            assertTrue(content.contains("Big Burger"));
        }

        @Test
        void save_withCustomization_customizationInFile() throws Exception {
            Customization bacon = new Customization("Bacon", Modification.ADD, new BigDecimal("1.50"));
            MenuItem item = new MenuItem.Builder("Burger", new BigDecimal("8.99"), Category.ENTREE)
                .addCustomization(bacon).build();
            Order order = new Order.Builder().addLineItem(new LineItem(item, 1, List.of(bacon))).build();
            orderStore.save(order);
            String content = Files.readString(tempDir.resolve(order.getId() + ".txt"));
            assertTrue(content.contains("Bacon"));
        }

        @Test
        void save_updatedStatus_overwritesFile() throws Exception {
            Order order = singleItemOrder();
            orderStore.save(order);
            order.updateStatus(OrderStatus.SUBMITTED);
            orderStore.save(order);
            String content = Files.readString(tempDir.resolve(order.getId() + ".txt"));
            assertTrue(content.contains("STATUS=SUBMITTED"));
        }

        @Test
        void delete_existingFile_removesIt() {
            Order order = singleItemOrder();
            orderStore.save(order);
            assertTrue(orderStore.delete(order.getId()));
            assertFalse(Files.exists(tempDir.resolve(order.getId() + ".txt")));
        }

        @Test
        void delete_unknownId_returnsFalse() {
            assertFalse(orderStore.delete(UUID.randomUUID()));
        }

        @Test
        void loadAllRaw_savedOrders_returnsCorrectCount() {
            orderStore.save(singleItemOrder());
            orderStore.save(singleItemOrder());
            assertEquals(2, orderStore.loadAllRaw().size());
        }

        @Test
        void loadAllRaw_emptyStore_returnsEmptyList() {
            assertTrue(orderStore.loadAllRaw().isEmpty());
        }

        @Test
        void constructor_invalidDirectory_throwsRuntimeException() {
            // On most systems a file can't be used as a directory path after being created
            assertDoesNotThrow(() -> new OrderStore(tempDir.resolve("subdir").toString()));
        }
    }

    @Nested
    class InventoryStoreTest {

        @TempDir Path tempDir;

        private InventoryStore inventoryStore;
        private InventoryManager inventoryManager;
        private IngredientMap ingredientMap;

        @BeforeEach
        void setUp() {
            inventoryStore = new InventoryStore(tempDir.toString());

            ingredientMap = new IngredientMap();
            UUID burgerId = UUID.randomUUID();
            ingredientMap.addItemIngredients(burgerId, Map.of("patty", 1, "bun", 1));
            ingredientMap.addCustomizationIngredients("Bacon", Map.of("bacon_strip", 1));

            inventoryManager = new InventoryManager(ingredientMap);
            inventoryManager.addIngredient(new StockLevel("patty", 50, 10));
            inventoryManager.addIngredient(new StockLevel("bun",   50, 10));
            inventoryManager.addIngredient(new StockLevel("bacon_strip", 30, 5));
        }

        @Test
        void saveAndLoadStock_roundTripsAllStockLevels() {
            inventoryStore.saveStock(inventoryManager);
            List<StockLevel> loaded = inventoryStore.loadStock();

            assertEquals(3, loaded.size());
            assertTrue(loaded.stream().anyMatch(s -> s.getIngredientName().equals("patty") && s.getQuantity() == 50));
            assertTrue(loaded.stream().anyMatch(s -> s.getIngredientName().equals("bun") && s.getQuantity() == 50));
            assertTrue(loaded.stream().anyMatch(s -> s.getIngredientName().equals("bacon_strip") && s.getQuantity() == 30));
        }

        @Test
        void loadStock_preservesLowStockThreshold() {
            inventoryStore.saveStock(inventoryManager);
            List<StockLevel> loaded = inventoryStore.loadStock();
            StockLevel patty = loaded.stream()
                .filter(s -> s.getIngredientName().equals("patty"))
                .findFirst().orElseThrow();
            assertEquals(10, patty.getLowStockThreshold());
        }

        @Test
        void loadStock_noFile_returnsEmptyList() {
            List<StockLevel> loaded = inventoryStore.loadStock();
            assertTrue(loaded.isEmpty());
        }

        @Test
        void saveAndLoadIngredientMap_roundTripsItemMappings() {
            inventoryStore.saveIngredientMap(ingredientMap);
            Map<String, Map<String, Integer>> raw = inventoryStore.loadIngredientMapRaw();
            assertFalse(raw.isEmpty());
        }

        @Test
        void loadIngredientMapRaw_noFile_returnsEmptyMap() {
            Map<String, Map<String, Integer>> raw = inventoryStore.loadIngredientMapRaw();
            assertTrue(raw.isEmpty());
        }

        @Test
        void saveStock_afterDeduction_reflectsUpdatedQuantity() {
            inventoryManager.getAllStock().get("patty").deduct(5);
            inventoryStore.saveStock(inventoryManager);
            List<StockLevel> loaded = inventoryStore.loadStock();
            StockLevel patty = loaded.stream()
                .filter(s -> s.getIngredientName().equals("patty"))
                .findFirst().orElseThrow();
            assertEquals(45, patty.getQuantity());
        }
    }

    @Nested
    class OrderFlowIntegrationTest {

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
}
