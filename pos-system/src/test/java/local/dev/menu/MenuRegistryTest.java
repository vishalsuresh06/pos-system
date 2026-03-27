package local.dev.menu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MenuRegistryTest {

    private MenuRegistry registry;
    private MenuItem burger;
    private MenuItem fries;
    private MenuItem cola;

    @BeforeEach
    void setUp() {
        registry = new MenuRegistry();
        burger = new MenuItem.Builder("Big Burger", new BigDecimal("8.99"), Category.ENTREE).build();
        fries  = new MenuItem.Builder("Large Fries", new BigDecimal("3.49"), Category.SIDE).build();
        cola   = new MenuItem.Builder("Cola", new BigDecimal("1.99"), Category.DRINK).build();
        registry.addMenuItem(burger);
        registry.addMenuItem(fries);
        registry.addMenuItem(cola);
    }

    @Test
    void getMenuItemById_existingItem_returnsItem() {
        assertEquals(burger, registry.getMenuItemById(burger.getId()));
    }

    @Test
    void getMenuItemById_unknownId_returnsNull() {
        assertNull(registry.getMenuItemById(java.util.UUID.randomUUID()));
    }

    @Test
    void getMenuItemByName_existingItem_returnsItem() {
        assertEquals(fries, registry.getMenuItemByName("Large Fries"));
    }

    @Test
    void getMenuItemByName_unknownName_returnsNull() {
        assertNull(registry.getMenuItemByName("Nonexistent"));
    }

    @Test
    void getMenuItemsByCategory_returnsOnlyThatCategory() {
        List<MenuItem> entrees = registry.getMenuItemsByCategory(Category.ENTREE);
        assertEquals(1, entrees.size());
        assertTrue(entrees.contains(burger));
    }

    @Test
    void getMenuItemsByCategory_noItemsInCategory_returnsEmpty() {
        List<MenuItem> desserts = registry.getMenuItemsByCategory(Category.DESSERT);
        assertTrue(desserts.isEmpty());
    }

    @Test
    void removeMenuItem_existingItem_removesFromAllLookups() {
        assertTrue(registry.removeMenuItem(burger.getId()));
        assertNull(registry.getMenuItemById(burger.getId()));
        assertNull(registry.getMenuItemByName("Big Burger"));
        assertTrue(registry.getMenuItemsByCategory(Category.ENTREE).isEmpty());
    }

    @Test
    void removeMenuItem_unknownId_returnsFalse() {
        assertFalse(registry.removeMenuItem(java.util.UUID.randomUUID()));
    }

    @Test
    void getAvailableMenuItems_byTime_filtersUnavailable() {
        MenuItem breakfastItem = new MenuItem.Builder("Pancakes", new BigDecimal("5.99"), Category.ENTREE)
            .availableFrom(LocalTime.of(6, 0))
            .availableTo(LocalTime.of(11, 0))
            .build();
        registry.addMenuItem(breakfastItem);

        List<MenuItem> available = registry.getAvailableMenuItems(LocalTime.of(14, 0), LocalDate.now());
        assertTrue(available.contains(burger));
        assertFalse(available.contains(breakfastItem));
    }

    @Test
    void getAvailableMenuItems_byCategoryAndTime_returnsCorrect() {
        List<MenuItem> available = registry.getAvailableMenuItems(LocalTime.NOON, LocalDate.now(), Category.SIDE);
        assertEquals(1, available.size());
        assertTrue(available.contains(fries));
    }

    @Test
    void getAvailableMenuItems_outOfSeason_excluded() {
        MenuItem seasonal = new MenuItem.Builder("Pumpkin Soup", new BigDecimal("4.99"), Category.SIDE)
            .seasonalStart(LocalDate.of(2020, 1, 1))
            .seasonalEnd(LocalDate.of(2020, 3, 31))
            .build();
        registry.addMenuItem(seasonal);

        List<MenuItem> available = registry.getAvailableMenuItems(LocalTime.NOON, LocalDate.now());
        assertFalse(available.contains(seasonal));
    }
}
