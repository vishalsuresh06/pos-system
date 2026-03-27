package local.dev.order;

import local.dev.inventory.InventoryManager;
import local.dev.persistence.OrderStore;
import local.dev.pricing.PricingEngine;
import java.util.*;

public class OrderManager {
    private final Map<UUID, Order> orders = new LinkedHashMap<>();
    private final PricingEngine pricingEngine;
    private final InventoryManager inventoryManager;
    private final OrderStore orderStore;

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = new HashMap<>();
    static {
        VALID_TRANSITIONS.put(OrderStatus.OPEN,      EnumSet.of(OrderStatus.SUBMITTED, OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(OrderStatus.SUBMITTED,  EnumSet.of(OrderStatus.PREPARING, OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(OrderStatus.PREPARING,  EnumSet.of(OrderStatus.READY,     OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(OrderStatus.READY,      EnumSet.of(OrderStatus.COMPLETED));
        VALID_TRANSITIONS.put(OrderStatus.COMPLETED,  Collections.emptySet());
        VALID_TRANSITIONS.put(OrderStatus.CANCELLED,  Collections.emptySet());
    }

    public OrderManager(PricingEngine pricingEngine, InventoryManager inventoryManager, OrderStore orderStore) {
        this.pricingEngine = pricingEngine;
        this.inventoryManager = inventoryManager;
        this.orderStore = orderStore;
    }

    public Order submitOrder(Order order) {
        if (!inventoryManager.canFulfillOrder(order))
            throw new IllegalStateException("Cannot fulfill order — insufficient ingredients");

        inventoryManager.deductForOrder(order);
        order.updateStatus(OrderStatus.SUBMITTED);
        orders.put(order.getId(), order);
        orderStore.save(order);
        return order;
    }

    public Order updateStatus(UUID orderId, OrderStatus newStatus) {
        Order order = orders.get(orderId);
        if (order == null)
            throw new NoSuchElementException("Order not found: " + orderId);

        Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(order.getStatus(), Collections.emptySet());
        if (!allowed.contains(newStatus))
            throw new IllegalStateException(
                "Cannot transition from " + order.getStatus() + " to " + newStatus);

        order.updateStatus(newStatus);
        orderStore.save(order);
        return order;
    }

    public void addOrder(Order order) {
        orders.put(order.getId(), order);
    }

    public Optional<Order> getOrder(UUID orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    public List<Order> getAllOrders() {
        return new ArrayList<>(orders.values());
    }

    public List<Order> getOrdersByStatus(OrderStatus status) {
        List<Order> result = new ArrayList<>();
        for (Order o : orders.values()) {
            if (o.getStatus() == status) result.add(o);
        }
        return result;
    }

    public PricingEngine getPricingEngine() {
        return pricingEngine;
    }
}
