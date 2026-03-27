package local.dev.receipt;

import local.dev.order.LineItem;
import local.dev.order.Order;
import local.dev.pricing.PricingEngine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

public class ReceiptGenerator {
    private static final int LINE_WIDTH = 40;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");

    private final PricingEngine pricingEngine;

    public ReceiptGenerator(PricingEngine pricingEngine) {
        this.pricingEngine = pricingEngine;
    }

    public String generate(Order order) {
        PricingEngine.PricingResult pricing = pricingEngine.calculate(order);
        StringBuilder sb = new StringBuilder();

        sb.append(center("MANGO FAST FOOD", LINE_WIDTH)).append("\n");
        sb.append(center("========================", LINE_WIDTH)).append("\n");
        sb.append("Order #: ").append(order.getId().toString().substring(0, 8).toUpperCase()).append("\n");
        sb.append("Date:    ").append(order.getOrderCreation().format(DATE_FMT)).append("\n");
        sb.append("-".repeat(LINE_WIDTH)).append("\n");

        for (LineItem item : order.getLineItems()) {
            String label = item.getMenuItem().getName() + " x" + item.getQuantity();
            String price = "$" + item.getTotalPrice().setScale(2, RoundingMode.HALF_UP);
            sb.append(padRight(label, LINE_WIDTH - price.length())).append(price).append("\n");

            for (var c : item.getCustomizations()) {
                sb.append("  ").append(c.getModification().name().toLowerCase())
                  .append(" ").append(c.getName());
                if (c.getPriceChange().compareTo(BigDecimal.ZERO) != 0) {
                    String sign = c.getPriceChange().compareTo(BigDecimal.ZERO) > 0 ? "+" : "-";
                    sb.append(" (").append(sign).append("$")
                      .append(c.getPriceChange().abs().setScale(2, RoundingMode.HALF_UP)).append(")");
                }
                sb.append("\n");
            }
        }

        sb.append("-".repeat(LINE_WIDTH)).append("\n");
        sb.append(priceLine("Subtotal:", "$" + pricing.getSubtotal().setScale(2, RoundingMode.HALF_UP))).append("\n");

        if (pricing.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            for (String desc : pricing.getAppliedDiscounts()) {
                sb.append("  ").append(desc).append("\n");
            }
            sb.append(priceLine("Savings:", "-$" + pricing.getDiscountAmount().setScale(2, RoundingMode.HALF_UP))).append("\n");
        }

        sb.append(priceLine("Tax:", "$" + pricing.getTaxAmount().setScale(2, RoundingMode.HALF_UP))).append("\n");
        sb.append("=".repeat(LINE_WIDTH)).append("\n");
        sb.append(priceLine("TOTAL:", "$" + pricing.getTotal().setScale(2, RoundingMode.HALF_UP))).append("\n");
        sb.append("=".repeat(LINE_WIDTH)).append("\n");
        sb.append(center("Thank you for dining with us!", LINE_WIDTH)).append("\n");

        return sb.toString();
    }

    private String priceLine(String label, String price) {
        return padRight(label, LINE_WIDTH - price.length()) + price;
    }

    private String padRight(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }

    private String center(String s, int width) {
        if (s.length() >= width) return s;
        int padding = (width - s.length()) / 2;
        return " ".repeat(padding) + s;
    }
}
