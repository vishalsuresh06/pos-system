package local.dev.order;

import local.dev.menu.Category;
import local.dev.menu.MenuItem;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OrderTest {

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
