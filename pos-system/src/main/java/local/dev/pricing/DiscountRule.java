package local.dev.pricing;

import local.dev.order.Order;
import java.math.BigDecimal;

public interface DiscountRule {
    BigDecimal apply(Order order);
    String getDescription();
}
