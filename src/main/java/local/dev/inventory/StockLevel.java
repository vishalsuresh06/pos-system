package local.dev.inventory;

public class StockLevel {
    private final String ingredientName;
    private int quantity;
    private final int lowStockThreshold;

    public StockLevel(String ingredientName, int quantity, int lowStockThreshold) {
        if (ingredientName == null || ingredientName.isBlank())
            throw new IllegalArgumentException("Ingredient name cannot be null or blank");
        if (quantity < 0)
            throw new IllegalArgumentException("Quantity cannot be negative");
        if (lowStockThreshold < 0)
            throw new IllegalArgumentException("Low stock threshold cannot be negative");
        this.ingredientName = ingredientName;
        this.quantity = quantity;
        this.lowStockThreshold = lowStockThreshold;
    }

    public String getIngredientName() { return ingredientName; }
    public int getQuantity() { return quantity; }
    public int getLowStockThreshold() { return lowStockThreshold; }

    public boolean isInStock(int needed) { return quantity >= needed; }
    public boolean isLowStock() { return quantity > 0 && quantity <= lowStockThreshold; }
    public boolean isOutOfStock() { return quantity == 0; }

    public void deduct(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Deduction amount must be positive");
        if (amount > quantity)
            throw new IllegalStateException("Insufficient stock for: " + ingredientName);
        quantity -= amount;
    }

    public void restock(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Restock amount must be positive");
        quantity += amount;
    }
}
