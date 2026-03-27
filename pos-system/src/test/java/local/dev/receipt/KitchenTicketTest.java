package local.dev.receipt;

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

import static org.junit.jupiter.api.Assertions.*;

public class KitchenTicketTest {

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
