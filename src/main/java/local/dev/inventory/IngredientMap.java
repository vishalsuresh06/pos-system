package local.dev.inventory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IngredientMap {
    // menuItemId -> ingredientName -> quantity needed per unit
    private final Map<UUID, Map<String, Integer>> itemIngredients = new HashMap<>();
    // customizationName -> ingredientName -> quantity needed per unit
    private final Map<String, Map<String, Integer>> customizationIngredients = new HashMap<>();

    public void addItemIngredients(UUID menuItemId, Map<String, Integer> ingredients) {
        itemIngredients.put(menuItemId, new HashMap<>(ingredients));
    }

    public void addCustomizationIngredients(String customizationName, Map<String, Integer> ingredients) {
        customizationIngredients.put(customizationName, new HashMap<>(ingredients));
    }

    public Map<String, Integer> getIngredientsForItem(UUID menuItemId) {
        return itemIngredients.getOrDefault(menuItemId, Collections.emptyMap());
    }

    public Map<String, Integer> getIngredientsForCustomization(String customizationName) {
        return customizationIngredients.getOrDefault(customizationName, Collections.emptyMap());
    }

    public boolean hasMappingForItem(UUID menuItemId) {
        return itemIngredients.containsKey(menuItemId);
    }

    public Map<UUID, Map<String, Integer>> getAllItemIngredients() {
        return Collections.unmodifiableMap(itemIngredients);
    }

    public Map<String, Map<String, Integer>> getAllCustomizationIngredients() {
        return Collections.unmodifiableMap(customizationIngredients);
    }
}
