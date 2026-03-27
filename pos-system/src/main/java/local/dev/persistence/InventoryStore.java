package local.dev.persistence;

import local.dev.inventory.IngredientMap;
import local.dev.inventory.InventoryManager;
import local.dev.inventory.StockLevel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class InventoryStore {
    private final Path stockFile;
    private final Path ingredientMapFile;

    public InventoryStore(String directoryPath) {
        Path dir = Paths.get(directoryPath);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create inventory storage directory", e);
        }
        this.stockFile = dir.resolve("stock.txt");
        this.ingredientMapFile = dir.resolve("ingredient_map.txt");
    }

    public void saveStock(InventoryManager inventoryManager) {
        List<String> lines = new ArrayList<>();
        for (StockLevel level : inventoryManager.getAllStock().values()) {
            lines.add(level.getIngredientName() + "|" + level.getQuantity() + "|" + level.getLowStockThreshold());
        }
        try {
            Files.write(stockFile, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save stock levels", e);
        }
    }

    public List<StockLevel> loadStock() {
        if (!Files.exists(stockFile)) return Collections.emptyList();
        List<StockLevel> levels = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(stockFile, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|");
                if (parts.length == 3) {
                    levels.add(new StockLevel(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load stock levels", e);
        }
        return levels;
    }

    public void saveIngredientMap(IngredientMap ingredientMap) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, Integer>> entry : ingredientMap.getAllItemIngredients().entrySet()) {
            lines.add("ITEM|" + entry.getKey() + "|" + formatIngredients(entry.getValue()));
        }
        for (Map.Entry<String, Map<String, Integer>> entry : ingredientMap.getAllCustomizationIngredients().entrySet()) {
            lines.add("CUST|" + entry.getKey() + "|" + formatIngredients(entry.getValue()));
        }
        try {
            Files.write(ingredientMapFile, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save ingredient map", e);
        }
    }

    public Map<String, Map<String, Integer>> loadIngredientMapRaw() {
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
        if (!Files.exists(ingredientMapFile)) return result;
        try {
            for (String line : Files.readAllLines(ingredientMapFile, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|", 3);
                if (parts.length == 3) {
                    result.put(parts[0] + "|" + parts[1], parseIngredients(parts[2]));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ingredient map", e);
        }
        return result;
    }

    private String formatIngredients(Map<String, Integer> ingredients) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : ingredients.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(e.getKey()).append(":").append(e.getValue());
        }
        return sb.toString();
    }

    private Map<String, Integer> parseIngredients(String raw) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String pair : raw.split(",")) {
            String[] kv = pair.split(":");
            if (kv.length == 2) map.put(kv[0], Integer.parseInt(kv[1]));
        }
        return map;
    }
}
