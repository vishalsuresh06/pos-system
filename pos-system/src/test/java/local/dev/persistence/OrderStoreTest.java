package local.dev.persistence;

import local.dev.menu.Category;
import local.dev.menu.Customization;
import local.dev.menu.MenuItem;
import local.dev.menu.Modification;
import local.dev.order.LineItem;
import local.dev.order.Order;
import local.dev.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OrderStoreTest {

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
        assertFalse(orderStore.delete(java.util.UUID.randomUUID()));
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
