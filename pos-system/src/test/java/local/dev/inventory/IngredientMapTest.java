package local.dev.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class IngredientMapTest {

    private IngredientMap ingredientMap;
    private UUID burgerId;

    @BeforeEach
    void setUp() {
        ingredientMap = new IngredientMap();
        burgerId = UUID.randomUUID();
        ingredientMap.addItemIngredients(burgerId, Map.of("patty", 1, "bun", 1, "lettuce", 1));
        ingredientMap.addCustomizationIngredients("Bacon", Map.of("bacon_strip", 1));
    }

    @Test
    void getIngredientsForItem_knownId_returnsCorrectMap() {
        Map<String, Integer> ingredients = ingredientMap.getIngredientsForItem(burgerId);
        assertEquals(3, ingredients.size());
        assertEquals(1, ingredients.get("patty"));
        assertEquals(1, ingredients.get("bun"));
    }

    @Test
    void getIngredientsForItem_unknownId_returnsEmptyMap() {
        Map<String, Integer> ingredients = ingredientMap.getIngredientsForItem(UUID.randomUUID());
        assertTrue(ingredients.isEmpty());
    }

    @Test
    void getIngredientsForCustomization_knownName_returnsCorrectMap() {
        Map<String, Integer> ingredients = ingredientMap.getIngredientsForCustomization("Bacon");
        assertEquals(1, ingredients.get("bacon_strip"));
    }

    @Test
    void getIngredientsForCustomization_unknownName_returnsEmptyMap() {
        assertTrue(ingredientMap.getIngredientsForCustomization("Nonexistent").isEmpty());
    }

    @Test
    void hasMappingForItem_knownId_returnsTrue() {
        assertTrue(ingredientMap.hasMappingForItem(burgerId));
    }

    @Test
    void hasMappingForItem_unknownId_returnsFalse() {
        assertFalse(ingredientMap.hasMappingForItem(UUID.randomUUID()));
    }

    @Test
    void getAllItemIngredients_isUnmodifiable() {
        assertThrows(UnsupportedOperationException.class, () ->
            ingredientMap.getAllItemIngredients().put(UUID.randomUUID(), Map.of()));
    }

    @Test
    void getAllCustomizationIngredients_isUnmodifiable() {
        assertThrows(UnsupportedOperationException.class, () ->
            ingredientMap.getAllCustomizationIngredients().put("New", Map.of()));
    }

    @Test
    void addItemIngredients_overridesExistingEntry() {
        ingredientMap.addItemIngredients(burgerId, Map.of("patty", 2));
        assertEquals(2, ingredientMap.getIngredientsForItem(burgerId).get("patty"));
        assertEquals(1, ingredientMap.getIngredientsForItem(burgerId).size());
    }
}
