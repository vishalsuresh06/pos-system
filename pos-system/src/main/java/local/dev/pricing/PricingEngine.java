package local.dev.pricing;

import local.dev.order.Order;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class PricingEngine {
    private final TaxCalculator taxCalculator;
    private final List<DiscountRule> discountRules;
    private final BigDecimal discountCap;

    public PricingEngine(TaxCalculator taxCalculator) {
        this.taxCalculator = taxCalculator;
        this.discountRules = new ArrayList<>();
        this.discountCap = null;
    }

    public PricingEngine(TaxCalculator taxCalculator, List<DiscountRule> discountRules, BigDecimal discountCap) {
        this.taxCalculator = taxCalculator;
        this.discountRules = new ArrayList<>(discountRules);
        this.discountCap = discountCap;
    }

    public void addDiscountRule(DiscountRule rule) {
        discountRules.add(rule);
    }

    public PricingResult calculate(Order order) {
        BigDecimal subtotal = order.getSubTotalAmount();

        BigDecimal totalDiscount = BigDecimal.ZERO;
        List<String> appliedDiscounts = new ArrayList<>();
        for (DiscountRule rule : discountRules) {
            BigDecimal discount = rule.apply(order);
            if (discount.compareTo(BigDecimal.ZERO) > 0) {
                totalDiscount = totalDiscount.add(discount);
                appliedDiscounts.add(rule.getDescription() + ": -$" + discount.setScale(2, RoundingMode.HALF_UP));
            }
        }

        if (discountCap != null && totalDiscount.compareTo(discountCap) > 0) {
            totalDiscount = discountCap;
        }

        BigDecimal discountedSubtotal = subtotal.subtract(totalDiscount).max(BigDecimal.ZERO);
        BigDecimal tax = taxCalculator.calculateTax(discountedSubtotal);
        BigDecimal total = discountedSubtotal.add(tax).setScale(2, RoundingMode.HALF_UP);

        return new PricingResult(subtotal, totalDiscount, appliedDiscounts, tax, total);
    }

    public static class PricingResult {
        private final BigDecimal subtotal;
        private final BigDecimal discountAmount;
        private final List<String> appliedDiscounts;
        private final BigDecimal taxAmount;
        private final BigDecimal total;

        public PricingResult(BigDecimal subtotal, BigDecimal discountAmount, List<String> appliedDiscounts,
                             BigDecimal taxAmount, BigDecimal total) {
            this.subtotal = subtotal;
            this.discountAmount = discountAmount;
            this.appliedDiscounts = appliedDiscounts;
            this.taxAmount = taxAmount;
            this.total = total;
        }

        public BigDecimal getSubtotal() { return subtotal; }
        public BigDecimal getDiscountAmount() { return discountAmount; }
        public List<String> getAppliedDiscounts() { return appliedDiscounts; }
        public BigDecimal getTaxAmount() { return taxAmount; }
        public BigDecimal getTotal() { return total; }
    }
}
