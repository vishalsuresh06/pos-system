package local.dev;

import local.dev.inventory.*;
import local.dev.menu.*;
import local.dev.order.*;
import local.dev.persistence.*;
import local.dev.pricing.*;
import local.dev.receipt.*;
import java.math.BigDecimal;
import java.util.*;

public class App {
    public static void main(String[] args) {

        // --- Menu Setup ---
        MenuRegistry menuRegistry = new MenuRegistry();

        Customization addBacon    = new Customization("Bacon",     Modification.ADD,        new BigDecimal("1.50"));
        Customization noPickles   = new Customization("Pickles",   Modification.REMOVE,     BigDecimal.ZERO);
        Customization wheatBun    = new Customization("Wheat Bun", Modification.SUBSTITUTE, BigDecimal.ZERO);

        MenuItem bigBurger = new MenuItem.Builder("Big Burger", new BigDecimal("8.99"), Category.ENTREE)
                .highProtein(true)
                .addCustomization(addBacon)
                .addCustomization(noPickles)
                .addCustomization(wheatBun)
                .build();

        MenuItem largeFries = new MenuItem.Builder("Large Fries", new BigDecimal("3.49"), Category.SIDE)
                .vegetarian(true)
                .kidsFriendly(true)
                .build();

        MenuItem cola = new MenuItem.Builder("Cola", new BigDecimal("1.99"), Category.DRINK)
                .kidsFriendly(true)
                .build();

        menuRegistry.addMenuItem(bigBurger);
        menuRegistry.addMenuItem(largeFries);
        menuRegistry.addMenuItem(cola);

        // --- Inventory Setup ---
        IngredientMap ingredientMap = new IngredientMap();
        ingredientMap.addItemIngredients(bigBurger.getId(),  Map.of("burger_patty", 1, "bun", 1, "lettuce", 1, "tomato", 1));
        ingredientMap.addItemIngredients(largeFries.getId(), Map.of("potatoes", 1));
        ingredientMap.addItemIngredients(cola.getId(),       Map.of("cola_syrup", 1));
        ingredientMap.addCustomizationIngredients("Bacon",   Map.of("bacon_strip", 1));

        InventoryManager inventoryManager = new InventoryManager(ingredientMap);
        inventoryManager.addIngredient(new StockLevel("burger_patty", 50, 10));
        inventoryManager.addIngredient(new StockLevel("bun",          50, 10));
        inventoryManager.addIngredient(new StockLevel("lettuce",     100, 20));
        inventoryManager.addIngredient(new StockLevel("tomato",      100, 20));
        inventoryManager.addIngredient(new StockLevel("potatoes",    200, 30));
        inventoryManager.addIngredient(new StockLevel("cola_syrup",  100, 20));
        inventoryManager.addIngredient(new StockLevel("bacon_strip",  30,  5));

        // --- Pricing Setup ---
        TaxCalculator taxCalculator = new TaxCalculator(new BigDecimal("0.08"));
        PricingEngine pricingEngine = new PricingEngine(taxCalculator);

        // Combo discount: ENTREE + SIDE + DRINK = $2 off
        pricingEngine.addDiscountRule(new DiscountRule() {
            @Override
            public BigDecimal apply(Order order) {
                boolean hasEntree = order.getLineItems().stream().anyMatch(li -> li.getMenuItem().getCategory() == Category.ENTREE);
                boolean hasSide   = order.getLineItems().stream().anyMatch(li -> li.getMenuItem().getCategory() == Category.SIDE);
                boolean hasDrink  = order.getLineItems().stream().anyMatch(li -> li.getMenuItem().getCategory() == Category.DRINK);
                return (hasEntree && hasSide && hasDrink) ? new BigDecimal("2.00") : BigDecimal.ZERO;
            }
            @Override
            public String getDescription() { return "Combo Meal Discount"; }
        });

        // --- Persistence Setup ---
        OrderStore     orderStore     = new OrderStore("data/orders");
        InventoryStore inventoryStore = new InventoryStore("data/inventory");

        // --- Order Manager & Receipt ---
        OrderManager      orderManager      = new OrderManager(pricingEngine, inventoryManager, orderStore);
        ReceiptGenerator  receiptGenerator  = new ReceiptGenerator(pricingEngine);
        KitchenTicket     kitchenTicket     = new KitchenTicket();

        // --- Place an Order ---
        System.out.println("=== POS System Demo ===\n");

        OrderBuilder orderBuilder = new OrderBuilder(menuRegistry);
        orderBuilder.addItem(bigBurger.getId(),  1, List.of(addBacon));
        orderBuilder.addItem(largeFries.getId(), 1);
        orderBuilder.addItem(cola.getId(),       1);

        Order order     = orderBuilder.build();
        Order submitted = orderManager.submitOrder(order);
        System.out.println("Order submitted: #" + submitted.getId().toString().substring(0, 8).toUpperCase());
        System.out.println("Status: " + submitted.getStatus());

        System.out.println("\n" + kitchenTicket.generate(submitted));
        System.out.println(receiptGenerator.generate(submitted));

        // Advance through lifecycle
        orderManager.updateStatus(submitted.getId(), OrderStatus.PREPARING);
        orderManager.updateStatus(submitted.getId(), OrderStatus.READY);
        orderManager.updateStatus(submitted.getId(), OrderStatus.COMPLETED);
        System.out.println("Final status: " + orderManager.getOrder(submitted.getId()).get().getStatus());

        // Persist inventory state
        inventoryStore.saveStock(inventoryManager);
        inventoryStore.saveIngredientMap(ingredientMap);
        System.out.println("\nInventory saved to disk.");

        // Low-stock alerts
        List<StockLevel> alerts = inventoryManager.getLowStockAlerts();
        if (!alerts.isEmpty()) {
            System.out.println("\nLow stock alerts:");
            for (StockLevel s : alerts)
                System.out.println("  - " + s.getIngredientName() + ": " + s.getQuantity() + " remaining");
        }
    }
}
