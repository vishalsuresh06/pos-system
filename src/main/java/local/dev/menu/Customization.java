package local.dev.menu;

import java.math.BigDecimal;

public class Customization {
    private final String name;
    private final Modification modification;
    private final BigDecimal priceChange;

    public Customization(String name, Modification modification, BigDecimal priceChange) {
        if (name == null || modification == null) 
            throw new IllegalArgumentException("Customization fields cannot be null");
        if (modification == Modification.ADD && priceChange.compareTo(BigDecimal.ZERO) < 0) 
            throw new IllegalArgumentException("Price change must be positive for ADD modifications");
        if (modification == Modification.REMOVE && priceChange.compareTo(BigDecimal.ZERO) > 0)
            throw new IllegalArgumentException("Price change cannot be positive for REMOVE modifications");

        this.name = name;
        this.modification = modification;
        this.priceChange = priceChange;
    }

    // Getters
    public String getName() { return name; }
    public Modification getModification() { return modification; }
    public BigDecimal getPriceChange() { return priceChange; }
}
