package local.dev.order;

import local.dev.menu.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class OrderBuilderTest {

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
