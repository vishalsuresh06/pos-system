package local.dev.receipt;

import local.dev.menu.Modification;
import local.dev.order.LineItem;
import local.dev.order.Order;
import java.time.format.DateTimeFormatter;

public class KitchenTicket {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public String generate(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== KITCHEN TICKET ===\n");
        sb.append("Order: #").append(order.getId().toString().substring(0, 8).toUpperCase()).append("\n");
        sb.append("Time:  ").append(order.getOrderCreation().format(TIME_FMT)).append("\n");
        sb.append("----------------------\n");

        for (LineItem item : order.getLineItems()) {
            sb.append(item.getQuantity()).append("x ").append(item.getMenuItem().getName()).append("\n");
            for (var c : item.getCustomizations()) {
                String prefix = c.getModification() == Modification.ADD      ? "  + "
                              : c.getModification() == Modification.REMOVE    ? "  - "
                              :                                                  "  ~ ";
                sb.append(prefix).append(c.getName()).append("\n");
            }
        }

        sb.append("======================\n");
        return sb.toString();
    }
}
