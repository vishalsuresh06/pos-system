package local.dev;

import local.dev.inventory.IngredientMap;
import local.dev.inventory.InventoryManager;
import local.dev.inventory.StockLevel;
import local.dev.menu.Category;
import local.dev.menu.MenuItem;
import local.dev.order.LineItem;
import local.dev.order.Order;
import local.dev.order.OrderManager;
import local.dev.order.OrderStatus;
import local.dev.persistence.OrderStore;
import local.dev.pricing.PricingEngine;
import local.dev.pricing.TaxCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ConcurrencyTests {

    @Nested
    class StockLevelReservationTest {

        @Test
        void getAvailableQuantity_noReservations_equalsTotal() {
            StockLevel s = new StockLevel("patty", 10, 2);
            assertEquals(10, s.getAvailableQuantity());
        }

        @Test
        void getReservedQuantity_noReservations_isZero() {
            StockLevel s = new StockLevel("patty", 10, 2);
            assertEquals(0, s.getReservedQuantity());
        }

        @Test
        void reserve_validAmount_reducesAvailableQuantity() {
            StockLevel s = new StockLevel("patty", 10, 2);
            s.reserve(3);
            assertEquals(7, s.getAvailableQuantity());
        }

        @Test
        void reserve_validAmount_increasesReservedQuantity() {
            StockLevel s = new StockLevel("patty", 10, 2);
            s.reserve(3);
            assertEquals(3, s.getReservedQuantity());
        }

        @Test
        void reserve_doesNotReduceTotalQuantity() {
            StockLevel s = new StockLevel("patty", 10, 2);
            s.reserve(3);
            assertEquals(10, s.getQuantity());
        }

        @Test
        void reserve_exactAvailableAmount_succeeds() {
            StockLevel s = new StockLevel("patty", 5, 2);
            assertDoesNotThrow(() -> s.reserve(5));
            assertEquals(0, s.getAvailableQuantity());
        }

        @Test
        void reserve_insufficientAvailable_throws() {
            StockLevel s = new StockLevel("patty", 5, 2);
            assertThrows(IllegalStateException.class, () -> s.reserve(6));
        }

        @Test
        void reserve_whenPartiallyReserved_checksRemainingAvailable() {
            StockLevel s = new StockLevel("patty", 5, 2);
            s.reserve(3); // available = 2
            assertThrows(IllegalStateException.class, () -> s.reserve(3)); // needs 3, only 2 left
        }

        @Test
        void release_validAmount_restoresAvailableQuantity() {
            StockLevel s = new StockLevel("patty", 10, 2);
            s.reserve(4);
            s.release(4);
            assertEquals(10, s.getAvailableQuantity());
        }

        @Test
        void release_partialAmount_reducesReservedQuantity() {
            StockLevel s = new StockLevel("patty", 10, 2);
            s.reserve(4);
            s.release(2);
            assertEquals(2, s.getReservedQuantity());
            assertEquals(8, s.getAvailableQuantity());
        }

        @Test
        void release_exceedsCurrentReservation_throws() {
            StockLevel s = new StockLevel("patty", 10, 2);
            s.reserve(2);
            assertThrows(IllegalStateException.class, () -> s.release(3));
        }

        @Test
        void release_withNoReservation_throws() {
            StockLevel s = new StockLevel("patty", 10, 2);
            assertThrows(IllegalStateException.class, () -> s.release(1));
        }

        @Test
        void isInStock_withReservation_checksAvailableNotTotal() {
            StockLevel s = new StockLevel("patty", 5, 2);
            s.reserve(4); // total=5, reserved=4, available=1
            assertFalse(s.isInStock(2)); // 2 > available(1), even though 2 <= total(5)
            assertTrue(s.isInStock(1));  // 1 == available(1)
        }

        @Test
        void isInStock_afterReleaseRestoresAvailability() {
            StockLevel s = new StockLevel("patty", 5, 2);
            s.reserve(5);
            assertFalse(s.isInStock(1));
            s.release(5);
            assertTrue(s.isInStock(5));
        }
    }

    @Nested
    class InventoryReservationTest {

        private InventoryManager inventoryManager;
        private MenuItem burger;

        @BeforeEach
        void setUp() {
            burger = new MenuItem.Builder("Big Burger", new BigDecimal("8.99"), Category.ENTREE).build();

            IngredientMap ingredientMap = new IngredientMap();
            ingredientMap.addItemIngredients(burger.getId(), Map.of("patty", 1, "bun", 1));

            inventoryManager = new InventoryManager(ingredientMap);
            inventoryManager.addIngredient(new StockLevel("patty", 5, 1));
            inventoryManager.addIngredient(new StockLevel("bun",   5, 1));
        }

        private Order orderForBurgers(int qty) {
            return new Order.Builder().addLineItem(new LineItem(burger, qty, null)).build();
        }

        @Test
        void reserveForOrder_reducesAvailableQuantity() {
            inventoryManager.reserveForOrder(orderForBurgers(2));
            assertEquals(3, inventoryManager.getStockLevel("patty").getAvailableQuantity());
            assertEquals(3, inventoryManager.getStockLevel("bun").getAvailableQuantity());
        }

        @Test
        void reserveForOrder_doesNotReduceTotalQuantity() {
            inventoryManager.reserveForOrder(orderForBurgers(2));
            assertEquals(5, inventoryManager.getStockLevel("patty").getQuantity());
            assertEquals(5, inventoryManager.getStockLevel("bun").getQuantity());
        }

        @Test
        void reserveForOrder_insufficientStock_throws() {
            assertThrows(IllegalStateException.class, () -> inventoryManager.reserveForOrder(orderForBurgers(6)));
        }

        @Test
        void reserveForOrder_allOrNothing_noPartialReservationOnFailure() {
            // deplete bun entirely so reservation will fail on that ingredient
            inventoryManager.getStockLevel("bun").deduct(5);

            assertThrows(IllegalStateException.class, () -> inventoryManager.reserveForOrder(orderForBurgers(1)));

            // patty must not have been partially reserved
            assertEquals(5, inventoryManager.getStockLevel("patty").getAvailableQuantity());
            assertEquals(0, inventoryManager.getStockLevel("patty").getReservedQuantity());
        }

        @Test
        void releaseReservation_restoresAllAvailableQuantity() {
            Order order = orderForBurgers(3);
            inventoryManager.reserveForOrder(order);
            inventoryManager.releaseReservation(order);
            assertEquals(5, inventoryManager.getStockLevel("patty").getAvailableQuantity());
            assertEquals(5, inventoryManager.getStockLevel("bun").getAvailableQuantity());
        }

        @Test
        void releaseReservation_setsReservedQuantityBackToZero() {
            Order order = orderForBurgers(3);
            inventoryManager.reserveForOrder(order);
            inventoryManager.releaseReservation(order);
            assertEquals(0, inventoryManager.getStockLevel("patty").getReservedQuantity());
        }

        @Test
        void canFulfillOrder_afterReservationExhaustsAvailableStock_returnsFalse() {
            inventoryManager.reserveForOrder(orderForBurgers(5)); // reserves all 5
            assertFalse(inventoryManager.canFulfillOrder(orderForBurgers(1)));
        }

        @Test
        void canFulfillOrder_checksAvailableNotTotal() {
            inventoryManager.reserveForOrder(orderForBurgers(4)); // available = 1
            assertTrue(inventoryManager.canFulfillOrder(orderForBurgers(1)));  // exactly 1 available
            assertFalse(inventoryManager.canFulfillOrder(orderForBurgers(2))); // 2 > available
        }

        @Test
        void multipleReservations_stackCorrectly() {
            Order orderA = orderForBurgers(2);
            Order orderB = orderForBurgers(2);
            inventoryManager.reserveForOrder(orderA);
            inventoryManager.reserveForOrder(orderB);
            assertEquals(1, inventoryManager.getStockLevel("patty").getAvailableQuantity());
            assertEquals(4, inventoryManager.getStockLevel("patty").getReservedQuantity());
        }

        @Test
        void releaseOneOfMultipleReservations_otherReservationIntact() {
            Order orderA = orderForBurgers(2);
            Order orderB = orderForBurgers(2);
            inventoryManager.reserveForOrder(orderA);
            inventoryManager.reserveForOrder(orderB);
            inventoryManager.releaseReservation(orderA);
            assertEquals(3, inventoryManager.getStockLevel("patty").getAvailableQuantity());
            assertEquals(2, inventoryManager.getStockLevel("patty").getReservedQuantity());
        }
    }

    @Nested
    class OnlineOrderTest {

        @TempDir Path tempDir;

        private OrderManager orderManager;
        private InventoryManager inventoryManager;
        private MenuItem burger;

        @BeforeEach
        void setUp() {
            burger = new MenuItem.Builder("Big Burger", new BigDecimal("8.99"), Category.ENTREE).build();

            IngredientMap ingredientMap = new IngredientMap();
            ingredientMap.addItemIngredients(burger.getId(), Map.of("patty", 1, "bun", 1));

            inventoryManager = new InventoryManager(ingredientMap);
            inventoryManager.addIngredient(new StockLevel("patty", 5, 1));
            inventoryManager.addIngredient(new StockLevel("bun",   5, 1));

            PricingEngine pricingEngine = new PricingEngine(new TaxCalculator(new BigDecimal("0.08")));
            OrderStore orderStore = new OrderStore(tempDir.toString());
            orderManager = new OrderManager(pricingEngine, inventoryManager, orderStore);
        }

        private Order buildBurgerOrder() {
            return new Order.Builder().addLineItem(new LineItem(burger, 1, null)).build();
        }

        // --- placeOnlineOrder ---

        @Test
        void placeOnlineOrder_setsStatusToReserved() {
            Order order = buildBurgerOrder();
            orderManager.placeOnlineOrder(order);
            assertEquals(OrderStatus.RESERVED, order.getStatus());
        }

        @Test
        void placeOnlineOrder_reservesIngredients_reducesAvailableQuantity() {
            orderManager.placeOnlineOrder(buildBurgerOrder());
            assertEquals(4, inventoryManager.getStockLevel("patty").getAvailableQuantity());
            assertEquals(4, inventoryManager.getStockLevel("bun").getAvailableQuantity());
        }

        @Test
        void placeOnlineOrder_doesNotDeductTotalStock() {
            orderManager.placeOnlineOrder(buildBurgerOrder());
            assertEquals(5, inventoryManager.getStockLevel("patty").getQuantity());
        }

        @Test
        void placeOnlineOrder_insufficientIngredients_throws() {
            inventoryManager.getStockLevel("patty").deduct(5);
            assertThrows(IllegalStateException.class, () -> orderManager.placeOnlineOrder(buildBurgerOrder()));
        }

        @Test
        void placeOnlineOrder_insufficientIngredients_statusRemainsOpen() {
            inventoryManager.getStockLevel("patty").deduct(5);
            Order order = buildBurgerOrder();
            assertThrows(IllegalStateException.class, () -> orderManager.placeOnlineOrder(order));
            assertEquals(OrderStatus.OPEN, order.getStatus());
        }

        @Test
        void placeOnlineOrder_blocksSubsequentInPersonOrder() {
            // deplete all but 1 unit
            inventoryManager.getStockLevel("patty").deduct(4);
            inventoryManager.getStockLevel("bun").deduct(4);

            orderManager.placeOnlineOrder(buildBurgerOrder()); // reserves the last unit

            assertThrows(IllegalStateException.class, () -> orderManager.submitOrder(buildBurgerOrder()));
        }

        // --- confirmOnlineOrder ---

        @Test
        void confirmOnlineOrder_transitionsToSubmitted() {
            Order order = buildBurgerOrder();
            orderManager.placeOnlineOrder(order);
            orderManager.confirmOnlineOrder(order.getId());
            assertEquals(OrderStatus.SUBMITTED, order.getStatus());
        }

        @Test
        void confirmOnlineOrder_deductsTotalStock() {
            Order order = buildBurgerOrder();
            orderManager.placeOnlineOrder(order);
            orderManager.confirmOnlineOrder(order.getId());
            assertEquals(4, inventoryManager.getStockLevel("patty").getQuantity());
        }

        @Test
        void confirmOnlineOrder_clearsReservation() {
            Order order = buildBurgerOrder();
            orderManager.placeOnlineOrder(order);
            orderManager.confirmOnlineOrder(order.getId());
            assertEquals(0, inventoryManager.getStockLevel("patty").getReservedQuantity());
        }

        @Test
        void confirmOnlineOrder_availableQuantityUnchangedRelativeToAfterReservation() {
            Order order = buildBurgerOrder();
            orderManager.placeOnlineOrder(order);
            int availableAfterReserve = inventoryManager.getStockLevel("patty").getAvailableQuantity();
            orderManager.confirmOnlineOrder(order.getId());
            assertEquals(availableAfterReserve, inventoryManager.getStockLevel("patty").getAvailableQuantity());
        }

        @Test
        void confirmOnlineOrder_whenNotReserved_throws() {
            Order order = buildBurgerOrder();
            // order is OPEN, not RESERVED
            assertThrows(IllegalStateException.class, () -> orderManager.confirmOnlineOrder(order.getId()));
        }

        @Test
        void confirmOnlineOrder_unknownId_throws() {
            assertThrows(Exception.class, () -> orderManager.confirmOnlineOrder(UUID.randomUUID()));
        }

        // --- cancelOnlineOrder ---

        @Test
        void cancelOnlineOrder_transitionsToCancelled() {
            Order order = buildBurgerOrder();
            orderManager.placeOnlineOrder(order);
            orderManager.cancelOnlineOrder(order.getId());
            assertEquals(OrderStatus.CANCELLED, order.getStatus());
        }

        @Test
        void cancelOnlineOrder_releasesIngredients_restoresAvailableQuantity() {
            Order order = buildBurgerOrder();
            orderManager.placeOnlineOrder(order);
            orderManager.cancelOnlineOrder(order.getId());
            assertEquals(5, inventoryManager.getStockLevel("patty").getAvailableQuantity());
            assertEquals(5, inventoryManager.getStockLevel("bun").getAvailableQuantity());
        }

        @Test
        void cancelOnlineOrder_whenNotReserved_throws() {
            Order order = buildBurgerOrder();
            assertThrows(IllegalStateException.class, () -> orderManager.cancelOnlineOrder(order.getId()));
        }

        @Test
        void cancelOnlineOrder_unknownId_throws() {
            assertThrows(Exception.class, () -> orderManager.cancelOnlineOrder(UUID.randomUUID()));
        }

        @Test
        void cancelOnlineOrder_thenSubmitOrder_succeedsWithReleasedIngredients() {
            // deplete all but 1 unit
            inventoryManager.getStockLevel("patty").deduct(4);
            inventoryManager.getStockLevel("bun").deduct(4);

            Order onlineOrder = buildBurgerOrder();
            orderManager.placeOnlineOrder(onlineOrder);

            // cashier cannot claim reserved stock
            assertThrows(IllegalStateException.class, () -> orderManager.submitOrder(buildBurgerOrder()));

            // online order abandoned — reservation released
            orderManager.cancelOnlineOrder(onlineOrder.getId());

            // cashier can now submit
            assertDoesNotThrow(() -> orderManager.submitOrder(buildBurgerOrder()));
        }

        // --- RESERVED status transitions ---

        @Test
        void reservedOrder_cannotTransitionBackToOpen() {
            Order order = buildBurgerOrder();
            orderManager.placeOnlineOrder(order);
            assertThrows(IllegalStateException.class, () -> orderManager.updateStatus(order.getId(), OrderStatus.OPEN));
        }

        @Test
        void reservedOrder_cannotSkipDirectlyToCompleted() {
            Order order = buildBurgerOrder();
            orderManager.placeOnlineOrder(order);
            assertThrows(IllegalStateException.class, () -> orderManager.updateStatus(order.getId(), OrderStatus.COMPLETED));
        }
    }

    @Nested
    class ConcurrentOrderTest {

        @TempDir Path tempDir;

        private OrderManager orderManager;
        private InventoryManager inventoryManager;
        private MenuItem burger;
        private ExecutorService executor;

        @BeforeEach
        void setUp() {
            burger = new MenuItem.Builder("Big Burger", new BigDecimal("8.99"), Category.ENTREE).build();

            IngredientMap ingredientMap = new IngredientMap();
            ingredientMap.addItemIngredients(burger.getId(), Map.of("patty", 1, "bun", 1));

            inventoryManager = new InventoryManager(ingredientMap);
            inventoryManager.addIngredient(new StockLevel("patty", 5, 1));
            inventoryManager.addIngredient(new StockLevel("bun",   5, 1));

            PricingEngine pricingEngine = new PricingEngine(new TaxCalculator(new BigDecimal("0.08")));
            orderManager = new OrderManager(pricingEngine, inventoryManager, new OrderStore(tempDir.toString()));
            executor = Executors.newFixedThreadPool(12);
        }

        @AfterEach
        void tearDown() throws InterruptedException {
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }

        private Order buildBurgerOrder() {
            return new Order.Builder().addLineItem(new LineItem(burger, 1, null)).build();
        }

        @Test
        void concurrentSubmitOrder_exactlyAvailableStockSucceeds() throws InterruptedException {
            int stockCount  = 5;
            int threadCount = 10;

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        orderManager.submitOrder(buildBurgerOrder());
                        successCount.incrementAndGet();
                    } catch (IllegalStateException ignored) {
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads did not complete in time");
            assertEquals(stockCount, successCount.get(),
                "Expected exactly " + stockCount + " successful orders — race condition likely present");
        }

        @Test
        void concurrentSubmitOrder_availableQuantityNeverNegative() throws InterruptedException {
            int threadCount = 20;

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        orderManager.submitOrder(buildBurgerOrder());
                    } catch (IllegalStateException ignored) {
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads did not complete in time");

            assertTrue(inventoryManager.getStockLevel("patty").getAvailableQuantity() >= 0,
                "patty available quantity went negative — race condition detected");
            assertTrue(inventoryManager.getStockLevel("bun").getAvailableQuantity() >= 0,
                "bun available quantity went negative — race condition detected");
        }

        @Test
        void concurrentPlaceOnlineOrder_neverExceedsAvailableStock() throws InterruptedException {
            int stockCount  = 5;
            int threadCount = 10;

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        orderManager.placeOnlineOrder(buildBurgerOrder());
                        successCount.incrementAndGet();
                    } catch (IllegalStateException ignored) {
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads did not complete in time");

            assertTrue(successCount.get() <= stockCount,
                "More reservations succeeded than available stock — race condition detected");
            assertTrue(inventoryManager.getStockLevel("patty").getAvailableQuantity() >= 0,
                "patty available quantity went negative");
        }

        @Test
        void concurrentSubmitAndOnlineOrder_reservationRespectedUnderContention() throws InterruptedException {
            // deplete all but 1 unit
            inventoryManager.getStockLevel("patty").deduct(4);
            inventoryManager.getStockLevel("bun").deduct(4);

            // online order holds the last unit
            orderManager.placeOnlineOrder(buildBurgerOrder());

            int cashierCount = 5;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(cashierCount);
            AtomicInteger cashierSuccess = new AtomicInteger(0);

            for (int i = 0; i < cashierCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        orderManager.submitOrder(buildBurgerOrder());
                        cashierSuccess.incrementAndGet();
                    } catch (IllegalStateException ignored) {
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads did not complete in time");

            assertEquals(0, cashierSuccess.get(),
                "Cashier orders should all fail — the only available unit is reserved by an online order");
            assertTrue(inventoryManager.getStockLevel("patty").getAvailableQuantity() >= 0);
        }

        @Test
        void noDeadlock_twoOrdersCompetingForSameIngredients() throws InterruptedException {
            // Give enough stock so neither order fails due to stock shortage —
            // only a deadlock would prevent completion
            inventoryManager.addIngredient(new StockLevel("patty", 10, 1));
            inventoryManager.addIngredient(new StockLevel("bun",   10, 1));

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(2);

            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        orderManager.submitOrder(buildBurgerOrder());
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS),
                "Deadlock detected: threads did not complete within 5 seconds");
        }

        @Test
        void noDeadlock_manyOrdersCompetingForOverlappingIngredients() throws InterruptedException {
            inventoryManager.addIngredient(new StockLevel("patty", 100, 1));
            inventoryManager.addIngredient(new StockLevel("bun",   100, 1));

            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        orderManager.submitOrder(buildBurgerOrder());
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS),
                "Deadlock detected: threads did not complete within 10 seconds");
        }

        @Test
        void noDeadlock_mixedOnlineAndInPersonOrdersConcurrently() throws InterruptedException {
            inventoryManager.addIngredient(new StockLevel("patty", 100, 1));
            inventoryManager.addIngredient(new StockLevel("bun",   100, 1));

            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final boolean isOnline = i % 2 == 0;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (isOnline) {
                            orderManager.placeOnlineOrder(buildBurgerOrder());
                        } else {
                            orderManager.submitOrder(buildBurgerOrder());
                        }
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS),
                "Deadlock detected: mixed online and in-person orders did not complete within 10 seconds");
        }

        @Test
        void throughput_50ConcurrentOrders_completesWithin2Seconds() throws InterruptedException {
            inventoryManager.addIngredient(new StockLevel("patty", 100, 1));
            inventoryManager.addIngredient(new StockLevel("bun",   100, 1));

            int threadCount = 50;
            ExecutorService largePool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                largePool.submit(() -> {
                    try {
                        startLatch.await();
                        orderManager.submitOrder(buildBurgerOrder());
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(2, TimeUnit.SECONDS),
                "50 concurrent orders did not complete within 2 seconds — global lock likely causing excessive serialization");
            largePool.shutdownNow();
        }

        @Test
        void noDeadlock_reverseIngredientOrderAcrossItems() throws InterruptedException {
            // Two items whose ingredient sets overlap but would be acquired in opposite
            // insertion orders. Without consistent lexicographic lock ordering, threads
            // competing on itemA and itemB would deadlock ("aaa" < "zzz").
            MenuItem itemA = new MenuItem.Builder("Item A", new BigDecimal("5.00"), Category.ENTREE).build();
            MenuItem itemB = new MenuItem.Builder("Item B", new BigDecimal("5.00"), Category.ENTREE).build();

            IngredientMap map = new IngredientMap();
            map.addItemIngredients(itemA.getId(), Map.of("aaa", 1, "zzz", 1));
            map.addItemIngredients(itemB.getId(), Map.of("zzz", 1, "aaa", 1));

            InventoryManager im = new InventoryManager(map);
            im.addIngredient(new StockLevel("aaa", 100, 1));
            im.addIngredient(new StockLevel("zzz", 100, 1));

            PricingEngine pe = new PricingEngine(new TaxCalculator(new BigDecimal("0.08")));
            OrderManager om = new OrderManager(pe, im, new OrderStore(tempDir.toString()));

            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final MenuItem item = i % 2 == 0 ? itemA : itemB;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        om.submitOrder(new Order.Builder().addLineItem(new LineItem(item, 1, null)).build());
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS),
                "Deadlock detected: per-ingredient lock ordering is not consistent across ingredient sets");
        }
    }
}
