package local.dev.pricing;

import local.dev.menu.Category;
import local.dev.order.LineItem;
import local.dev.order.Order;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

public class TaxCalculator {
    private final BigDecimal defaultRate;
    private final Map<Category, BigDecimal> categoryRates = new HashMap<>();

    public TaxCalculator(BigDecimal defaultRate) {
        if (defaultRate == null || defaultRate.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Tax rate must be non-negative");
        this.defaultRate = defaultRate;
    }

    public void setCategoryRate(Category category, BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Tax rate must be non-negative");
        categoryRates.put(category, rate);
    }

    public BigDecimal calculateTax(Order order) {
        BigDecimal totalTax = BigDecimal.ZERO;
        for (LineItem item : order.getLineItems()) {
            BigDecimal rate = categoryRates.getOrDefault(item.getMenuItem().getCategory(), defaultRate);
            totalTax = totalTax.add(item.getTotalPrice().multiply(rate));
        }
        return totalTax.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateTax(BigDecimal amount) {
        return amount.multiply(defaultRate).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getDefaultRate() { return defaultRate; }
}
