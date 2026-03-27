package local.dev.inventory;

import local.dev.menu.Customization;
import local.dev.order.LineItem;
import local.dev.order.Order;
import java.util.*;

public class InventoryManager {
    private final Map<String, StockLevel> stock = new HashMap<>();
    private final IngredientMap ingredientMap;

    public InventoryManager(IngredientMap ingredientMap) {
        this.ingredientMap = ingredientMap;
    }

    public void addIngredient(StockLevel stockLevel) {
        stock.put(stockLevel.getIngredientName(), stockLevel);
    }

    public StockLevel getStockLevel(String ingredientName) {
        return stock.get(ingredientName);
    }

    public Map<String, StockLevel> getAllStock() {
        return Collections.unmodifiableMap(stock);
    }

    public IngredientMap getIngredientMap() {
        return ingredientMap;
    }

    public boolean isItemInStock(UUID menuItemId) {
        for (Map.Entry<String, Integer> entry : ingredientMap.getIngredientsForItem(menuItemId).entrySet()) {
            StockLevel level = stock.get(entry.getKey());
            if (level == null || !level.isInStock(entry.getValue())) return false;
        }
        return true;
    }

    public boolean canFulfillOrder(Order order) {
        Map<String, Integer> totalNeeded = new HashMap<>();
        for (LineItem item : order.getLineItems()) {
            int qty = item.getQuantity();
            for (Map.Entry<String, Integer> e : ingredientMap.getIngredientsForItem(item.getMenuItem().getId()).entrySet()) {
                totalNeeded.merge(e.getKey(), e.getValue() * qty, Integer::sum);
            }
            for (Customization c : item.getCustomizations()) {
                for (Map.Entry<String, Integer> e : ingredientMap.getIngredientsForCustomization(c.getName()).entrySet()) {
                    totalNeeded.merge(e.getKey(), e.getValue() * qty, Integer::sum);
                }
            }
        }
        for (Map.Entry<String, Integer> entry : totalNeeded.entrySet()) {
            StockLevel level = stock.get(entry.getKey());
            if (level == null || !level.isInStock(entry.getValue())) return false;
        }
        return true;
    }

    public void deductForOrder(Order order) {
        if (!canFulfillOrder(order))
            throw new IllegalStateException("Insufficient stock to fulfill order: " + order.getId());

        for (LineItem item : order.getLineItems()) {
            int qty = item.getQuantity();
            for (Map.Entry<String, Integer> e : ingredientMap.getIngredientsForItem(item.getMenuItem().getId()).entrySet()) {
                stock.get(e.getKey()).deduct(e.getValue() * qty);
            }
            for (Customization c : item.getCustomizations()) {
                for (Map.Entry<String, Integer> e : ingredientMap.getIngredientsForCustomization(c.getName()).entrySet()) {
                    stock.get(e.getKey()).deduct(e.getValue() * qty);
                }
            }
        }
    }

    public Set<UUID> getOutOfStockItemIds(Set<UUID> menuItemIds) {
        Set<UUID> outOfStock = new HashSet<>();
        for (UUID id : menuItemIds) {
            if (!isItemInStock(id)) outOfStock.add(id);
        }
        return outOfStock;
    }

    public List<StockLevel> getLowStockAlerts() {
        List<StockLevel> alerts = new ArrayList<>();
        for (StockLevel level : stock.values()) {
            if (level.isLowStock()) alerts.add(level);
        }
        return alerts;
    }
}
