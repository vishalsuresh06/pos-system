package local.dev.order;

import local.dev.menu.Customization;
import local.dev.menu.MenuItem;
import local.dev.menu.MenuRegistry;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class OrderBuilder {
    private final MenuRegistry menuRegistry;
    private final List<LineItem> lineItems = new ArrayList<>();

    public OrderBuilder(MenuRegistry menuRegistry) {
        this.menuRegistry = menuRegistry;
    }

    public LineItem addItem(UUID menuItemId, int quantity, List<Customization> customizations) {
        MenuItem item = menuRegistry.getMenuItemById(menuItemId);
        if (item == null)
            throw new IllegalArgumentException("Menu item not found: " + menuItemId);

        if (!item.isAvailableAt(LocalTime.now()) || !item.isInSeason(LocalDate.now()))
            throw new IllegalStateException("Menu item is not currently available: " + item.getName());

        if (customizations != null) {
            Set<Customization> allowed = item.getCustomizations();
            for (Customization c : customizations) {
                if (!allowed.contains(c))
                    throw new IllegalArgumentException(
                        "Customization '" + c.getName() + "' is not allowed for " + item.getName());
            }
        }

        LineItem lineItem = new LineItem(item, quantity, customizations);
        lineItems.add(lineItem);
        return lineItem;
    }

    public LineItem addItem(UUID menuItemId, int quantity) {
        return addItem(menuItemId, quantity, new ArrayList<>());
    }

    public boolean removeItem(UUID lineItemId) {
        return lineItems.removeIf(li -> li.getId().equals(lineItemId));
    }

    public List<LineItem> getLineItems() {
        return Collections.unmodifiableList(lineItems);
    }

    public boolean isEmpty() {
        return lineItems.isEmpty();
    }

    public Order build() {
        if (lineItems.isEmpty())
            throw new IllegalStateException("Cannot build an order with no items");
        return new Order.Builder().lineItems(lineItems).build();
    }
}
