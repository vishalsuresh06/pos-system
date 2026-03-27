package local.dev.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Order {
    private final UUID id;
    private final List<LineItem> lineItems;
    private OrderStatus status;
    private final LocalDateTime orderCreation;
    private final LocalDateTime orderCompleted;
    private final BigDecimal subTotalAmount;
    private final BigDecimal totalAmount;
    private final BigDecimal taxAmount;

    private Order(Builder builder) {
        this.id = builder.id;
        this.lineItems = Collections.unmodifiableList(new ArrayList<>(builder.lineItems));
        this.status = builder.status;
        this.orderCreation = builder.orderCreation;
        this.orderCompleted = builder.orderCompleted;
        this.subTotalAmount = builder.subTotalAmount;
        this.taxAmount = builder.taxAmount;
        this.totalAmount = builder.totalAmount;
    }

    public UUID getId() {
        return id;
    }

    public List<LineItem> getLineItems() {
        return lineItems;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getOrderCreation() {
        return orderCreation;
    }

    public LocalDateTime getOrderCompleted() {
        return orderCompleted;
    }

    public BigDecimal getSubTotalAmount() {
        return subTotalAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void updateStatus(OrderStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "Order status cannot be null");
    }

    public static class Builder {
        private UUID id = UUID.randomUUID();
        private List<LineItem> lineItems = new ArrayList<>();
        private OrderStatus status = OrderStatus.OPEN;
        private LocalDateTime orderCreation = LocalDateTime.now();
        private LocalDateTime orderCompleted;
        private BigDecimal subTotalAmount;
        private BigDecimal totalAmount;
        private BigDecimal taxAmount;
        private BigDecimal taxRate = BigDecimal.ZERO;

        public Builder id(UUID id) {
            this.id = Objects.requireNonNull(id, "Order ID cannot be null");
            return this;
        }

        public Builder lineItems(List<LineItem> lineItems) {
            Objects.requireNonNull(lineItems, "Line items cannot be null");
            this.lineItems = new ArrayList<>(lineItems);
            return this;
        }

        public Builder addLineItem(LineItem lineItem) {
            this.lineItems.add(Objects.requireNonNull(lineItem, "Line item cannot be null"));
            return this;
        }

        public Builder status(OrderStatus status) {
            this.status = Objects.requireNonNull(status, "Order status cannot be null");
            return this;
        }

        public Builder orderCreation(LocalDateTime orderCreation) {
            this.orderCreation = Objects.requireNonNull(orderCreation, "Order creation time cannot be null");
            return this;
        }

        public Builder orderCompleted(LocalDateTime orderCompleted) {
            this.orderCompleted = orderCompleted;
            return this;
        }

        public Builder taxRate(BigDecimal taxRate) {
            this.taxRate = requireNonNegative(taxRate, "Tax rate cannot be negative");
            return this;
        }

        public Builder taxAmount(BigDecimal taxRate) {
            this.taxRate = requireNonNegative(taxRate, "Tax rate cannot be negative");
            return this;
        }

        public Order build() {
            if (lineItems.isEmpty()) {
                throw new IllegalStateException("Order must contain at least one line item");
            }
            if (orderCompleted != null && orderCompleted.isBefore(orderCreation)) {
                throw new IllegalStateException("Order completion time cannot be before creation time");
            }

            this.subTotalAmount = calculateSubtotal(lineItems);
            this.taxAmount = subTotalAmount.multiply(taxRate);
            this.totalAmount = subTotalAmount.add(taxAmount);

            return new Order(this);
        }

        private static BigDecimal calculateSubtotal(List<LineItem> lineItems) {
            BigDecimal subtotal = BigDecimal.ZERO;
            for (LineItem lineItem : lineItems) {
                subtotal = subtotal.add(lineItem.getTotalPrice());
            }
            return subtotal;
        }

        private static BigDecimal requireNonNegative(BigDecimal amount, String message) {
            Objects.requireNonNull(amount, "Amount cannot be null");
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(message);
            }
            return amount;
        }
    }
}
