package local.dev.persistence;

import local.dev.inventory.IngredientMap;
import local.dev.inventory.InventoryManager;
import local.dev.inventory.StockLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class InventoryStoreTest {

    @TempDir Path tempDir;

    private InventoryStore inventoryStore;
    private InventoryManager inventoryManager;
    private IngredientMap ingredientMap;

    @BeforeEach
    void setUp() {
        inventoryStore = new InventoryStore(tempDir.toString());

        ingredientMap = new IngredientMap();
        UUID burgerId = UUID.randomUUID();
        ingredientMap.addItemIngredients(burgerId, Map.of("patty", 1, "bun", 1));
        ingredientMap.addCustomizationIngredients("Bacon", Map.of("bacon_strip", 1));

        inventoryManager = new InventoryManager(ingredientMap);
        inventoryManager.addIngredient(new StockLevel("patty", 50, 10));
        inventoryManager.addIngredient(new StockLevel("bun",   50, 10));
        inventoryManager.addIngredient(new StockLevel("bacon_strip", 30, 5));
    }

    @Test
    void saveAndLoadStock_roundTripsAllStockLevels() {
        inventoryStore.saveStock(inventoryManager);
        List<StockLevel> loaded = inventoryStore.loadStock();

        assertEquals(3, loaded.size());
        assertTrue(loaded.stream().anyMatch(s -> s.getIngredientName().equals("patty") && s.getQuantity() == 50));
        assertTrue(loaded.stream().anyMatch(s -> s.getIngredientName().equals("bun") && s.getQuantity() == 50));
        assertTrue(loaded.stream().anyMatch(s -> s.getIngredientName().equals("bacon_strip") && s.getQuantity() == 30));
    }

    @Test
    void loadStock_preservesLowStockThreshold() {
        inventoryStore.saveStock(inventoryManager);
        List<StockLevel> loaded = inventoryStore.loadStock();
        StockLevel patty = loaded.stream()
            .filter(s -> s.getIngredientName().equals("patty"))
            .findFirst().orElseThrow();
        assertEquals(10, patty.getLowStockThreshold());
    }

    @Test
    void loadStock_noFile_returnsEmptyList() {
        List<StockLevel> loaded = inventoryStore.loadStock();
        assertTrue(loaded.isEmpty());
    }

    @Test
    void saveAndLoadIngredientMap_roundTripsItemMappings() {
        inventoryStore.saveIngredientMap(ingredientMap);
        Map<String, Map<String, Integer>> raw = inventoryStore.loadIngredientMapRaw();
        assertFalse(raw.isEmpty());
    }

    @Test
    void loadIngredientMapRaw_noFile_returnsEmptyMap() {
        Map<String, Map<String, Integer>> raw = inventoryStore.loadIngredientMapRaw();
        assertTrue(raw.isEmpty());
    }

    @Test
    void saveStock_afterDeduction_reflectsUpdatedQuantity() {
        inventoryManager.getAllStock().get("patty").deduct(5);
        inventoryStore.saveStock(inventoryManager);
        List<StockLevel> loaded = inventoryStore.loadStock();
        StockLevel patty = loaded.stream()
            .filter(s -> s.getIngredientName().equals("patty"))
            .findFirst().orElseThrow();
        assertEquals(45, patty.getQuantity());
    }
}
