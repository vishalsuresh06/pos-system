package local.dev.menu;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

public class MenuItemTest {

    private MenuItem.Builder baseBuilder() {
        return new MenuItem.Builder("Big Burger", new BigDecimal("8.99"), Category.ENTREE);
    }

    @Test
    void build_withRequiredFields_succeeds() {
        MenuItem item = baseBuilder().build();
        assertEquals("Big Burger", item.getName());
        assertEquals(new BigDecimal("8.99"), item.getPrice());
        assertEquals(Category.ENTREE, item.getCategory());
        assertNotNull(item.getId());
    }

    @Test
    void build_missingName_throws() {
        assertThrows(IllegalStateException.class, () ->
            new MenuItem.Builder(null, new BigDecimal("8.99"), Category.ENTREE).build());
    }

    @Test
    void build_missingPrice_throws() {
        assertThrows(IllegalStateException.class, () ->
            new MenuItem.Builder("Burger", null, Category.ENTREE).build());
    }

    @Test
    void build_missingCategory_throws() {
        assertThrows(IllegalStateException.class, () ->
            new MenuItem.Builder("Burger", new BigDecimal("8.99"), null).build());
    }

    @Test
    void isAvailableAt_withinWindow_returnsTrue() {
        MenuItem item = baseBuilder()
            .availableFrom(LocalTime.of(9, 0))
            .availableTo(LocalTime.of(17, 0))
            .build();
        assertTrue(item.isAvailableAt(LocalTime.of(12, 0)));
        assertTrue(item.isAvailableAt(LocalTime.of(9, 0)));
        assertTrue(item.isAvailableAt(LocalTime.of(17, 0)));
    }

    @Test
    void isAvailableAt_outsideWindow_returnsFalse() {
        MenuItem item = baseBuilder()
            .availableFrom(LocalTime.of(9, 0))
            .availableTo(LocalTime.of(17, 0))
            .build();
        assertFalse(item.isAvailableAt(LocalTime.of(8, 59)));
        assertFalse(item.isAvailableAt(LocalTime.of(17, 1)));
    }

    @Test
    void isInSeason_withinDates_returnsTrue() {
        MenuItem item = baseBuilder()
            .seasonalStart(LocalDate.of(2026, 1, 1))
            .seasonalEnd(LocalDate.of(2026, 12, 31))
            .build();
        assertTrue(item.isInSeason(LocalDate.of(2026, 6, 15)));
        assertTrue(item.isInSeason(LocalDate.of(2026, 1, 1)));
        assertTrue(item.isInSeason(LocalDate.of(2026, 12, 31)));
    }

    @Test
    void isInSeason_outsideDates_returnsFalse() {
        MenuItem item = baseBuilder()
            .seasonalStart(LocalDate.of(2026, 6, 1))
            .seasonalEnd(LocalDate.of(2026, 8, 31))
            .build();
        assertFalse(item.isInSeason(LocalDate.of(2026, 5, 31)));
        assertFalse(item.isInSeason(LocalDate.of(2026, 9, 1)));
    }

    @Test
    void isInSeason_noSeasonalDates_alwaysReturnsTrue() {
        MenuItem item = baseBuilder().build();
        assertTrue(item.isInSeason(LocalDate.of(2020, 1, 1)));
        assertTrue(item.isInSeason(LocalDate.of(2099, 12, 31)));
    }

    @Test
    void dietaryFlags_defaultToFalse() {
        MenuItem item = baseBuilder().build();
        assertFalse(item.isKidsFriendly());
        assertFalse(item.isVegetarian());
        assertFalse(item.isHighProtein());
    }

    @Test
    void dietaryFlags_setCorrectly() {
        MenuItem item = baseBuilder().kidsFriendly(true).vegetarian(true).highProtein(true).build();
        assertTrue(item.isKidsFriendly());
        assertTrue(item.isVegetarian());
        assertTrue(item.isHighProtein());
    }

    @Test
    void addCustomization_appearsInSet() {
        Customization bacon = new Customization("Bacon", Modification.ADD, new BigDecimal("1.50"));
        MenuItem item = baseBuilder().addCustomization(bacon).build();
        assertTrue(item.getCustomizations().contains(bacon));
    }
}
