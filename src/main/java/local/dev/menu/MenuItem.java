package local.dev.menu;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MenuItem {
    private final UUID id;
    private final String name;
    private final BigDecimal price;
    private final Category category;
    private final LocalTime availableFrom;
    private final LocalTime availableTo;
    private final LocalDate seasonalStart;
    private final LocalDate seasonalEnd;
    private final boolean kidsFriendly;
    private final boolean vegetarian;
    private final boolean highProtein;
    private final HashSet<Customization> customizations;

    private MenuItem(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.price = builder.price;
        this.category = builder.category;
        this.availableFrom = builder.availableFrom;
        this.availableTo = builder.availableTo;
        this.seasonalStart = builder.seasonalStart;
        this.seasonalEnd = builder.seasonalEnd;
        this.kidsFriendly = builder.kidsFriendly;
        this.vegetarian = builder.vegetarian;
        this.highProtein = builder.highProtein;
        this.customizations = builder.customizations;
    }

    // Getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public Category getCategory() { return category; }
    public LocalTime getAvailableFrom() { return availableFrom; }
    public LocalTime getAvailableTo() { return availableTo; }
    public LocalDate getSeasonalStart() { return seasonalStart; }
    public LocalDate getSeasonalEnd() { return seasonalEnd; }
    public boolean isKidsFriendly() { return kidsFriendly; }
    public boolean isVegetarian() { return vegetarian; }
    public boolean isHighProtein() { return highProtein; }
    public Set<Customization> getCustomizations() { return customizations; }

    public boolean isAvailableAt(LocalTime time) {
        return !time.isBefore(availableFrom) && !time.isAfter(availableTo);
    }

    public boolean isInSeason(LocalDate date) {
        if (seasonalStart == null || seasonalEnd == null) {
            return true; // not seasonal, always available
        }
        return !date.isBefore(seasonalStart) && !date.isAfter(seasonalEnd);
    }

    public static class Builder {
        private UUID id = UUID.randomUUID();
        private String name;
        private BigDecimal price;
        private Category category;
        private LocalTime availableFrom = LocalTime.of(0, 0);
        private LocalTime availableTo = LocalTime.of(23, 59);
        private LocalDate seasonalStart;
        private LocalDate seasonalEnd;
        private boolean kidsFriendly = false;
        private boolean vegetarian = false;
        private boolean highProtein = false;
        private HashSet<Customization> customizations = new HashSet<>();

        public Builder(String name, BigDecimal price, Category category) {
            this.name = name;
            this.price = price;
            this.category = category;
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder availableFrom(LocalTime availableFrom) {
            this.availableFrom = availableFrom;
            return this;
        }

        public Builder availableTo(LocalTime availableTo) {
            this.availableTo = availableTo;
            return this;
        }

        public Builder seasonalStart(LocalDate seasonalStart) {
            this.seasonalStart = seasonalStart;
            return this;
        }

        public Builder seasonalEnd(LocalDate seasonalEnd) {
            this.seasonalEnd = seasonalEnd;
            return this;
        }

        public Builder kidsFriendly(boolean kidsFriendly) {
            this.kidsFriendly = kidsFriendly;
            return this;
        }

        public Builder vegetarian(boolean vegetarian) {
            this.vegetarian = vegetarian;
            return this;
        }

        public Builder highProtein(boolean highProtein) {
            this.highProtein = highProtein;
            return this;
        }

        public Builder addCustomization(Customization customization) {
            this.customizations.add(customization);
            return this;
        }

        public MenuItem build() {
            if (name == null || price == null || category == null) {
                throw new IllegalStateException("Name, Price, and Category are required fields.");
            }
            return new MenuItem(this);
        }
    }
}