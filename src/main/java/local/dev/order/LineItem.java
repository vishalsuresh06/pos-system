package local.dev.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import local.dev.menu.Customization;
import local.dev.menu.MenuItem;

public class LineItem {
    private final UUID id;
    private final MenuItem menuItem;
    private final int quantity;
    private final List<Customization> customizations;
    private final BigDecimal unitPrice;
    private final BigDecimal totalPrice;

    public LineItem(MenuItem menuItem, int quantity, List<Customization> customizations) {
        if (menuItem == null)
            throw new IllegalArgumentException("Menu item cannot be null");
        if (quantity <= 0)
            throw new IllegalArgumentException("Quantity must be greater than zero");
        if (customizations == null)
            customizations = new ArrayList<>();

        this.id = UUID.randomUUID();
        this.menuItem = menuItem;
        this.quantity = quantity;
        this.customizations = customizations;
        this.unitPrice = calculateUnitPrice();
        this.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    private BigDecimal calculateUnitPrice() {
        BigDecimal base = menuItem.getPrice();
        BigDecimal customizationCost = BigDecimal.ZERO;
        for (Customization cust : customizations) {
            customizationCost = customizationCost.add(cust.getPriceChange());
        }
        return base.add(customizationCost);
    }

    // Getters
    public UUID getId() { return id; }
    public MenuItem getMenuItem() { return menuItem; }
    public int getQuantity() { return quantity; }
    public List<Customization> getCustomizations() { return customizations; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotalPrice() { return totalPrice; }
}