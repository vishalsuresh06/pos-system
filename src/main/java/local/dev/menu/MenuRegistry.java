package local.dev.menu;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class MenuRegistry {
    private final HashMap<UUID, MenuItem> itemsById = new HashMap<>();
    private final HashMap<String, MenuItem> itemsByName = new HashMap<>();
    private final HashMap<Category, List< MenuItem>> itemsByCategory = new HashMap<>();

    public void addMenuItem(MenuItem item) {
        itemsById.put(item.getId(), item);
        itemsByName.put(item.getName(), item);
        itemsByCategory.computeIfAbsent(item.getCategory(), k -> new ArrayList<>()).add(item);
    }

    public boolean removeMenuItem(UUID id) {
        MenuItem item = itemsById.remove(id);
        if (item != null) {
            itemsByName.remove(item.getName());
            itemsByCategory.get(item.getCategory()).remove(item);
        }
        return item != null;
    }

    public MenuItem getMenuItemById(UUID id) {
        return itemsById.get(id);
    }

    public MenuItem getMenuItemByName(String name) {
        return itemsByName.get(name);
    }

    public List<MenuItem> getMenuItemsByCategory(Category category) {
        return itemsByCategory.getOrDefault(category, new ArrayList<>());
    }

    public List<MenuItem> getAvailableMenuItems(LocalTime time, LocalDate date, Category category) {
        List<MenuItem> availableItems = new ArrayList<>();
        List<MenuItem> categoryItems = itemsByCategory.get(category);
        if (categoryItems != null) {
            for (MenuItem item : categoryItems) 
                if (item.isAvailableAt(time) && item.isInSeason(date)) 
                    availableItems.add(item);
        }
        return availableItems;
    }

    public List<MenuItem> getAvailableMenuItems(LocalTime time, LocalDate date) {
        List<MenuItem> availableItems = new ArrayList<>();
        for (MenuItem item : itemsById.values()) 
            if (item.isAvailableAt(time) && item.isInSeason(date)) 
                availableItems.add(item);

        return availableItems;
    }
}