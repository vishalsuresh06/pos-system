# POS System Architecture

## Overview
A fast food point-of-sale system that handles menu management, order processing, pricing with discounts, inventory tracking, persistence, and receipt generation.

---

## Package: `local.dev.menu`
**Purpose:** Defines what the restaurant sells. This is the foundation — every other package depends on it.

### Files

- **Category.java** — Enum defining the types of menu items: `ENTREE`, `SIDE`, `DRINK`, `DESSERT`, `CONDIMENT`. Used to classify items and enforce combo rules (e.g., a combo needs one ENTREE + one SIDE + one DRINK).

- **MenuItem.java** — Represents a single item on the menu (e.g., Big Burger, Large Fries). Holds the name, price, category, availability window (time of day), seasonal dates, dietary flags (kids-friendly, vegetarian, high-protein), and allowed customizations. Immutable — once built via the Builder pattern, it cannot be changed.

- **Customization.java** — Represents a modification to a menu item (e.g., add bacon +$1.50, remove pickles, substitute wheat bun). Has a name, price modifier, and type (ADD, REMOVE, SUBSTITUTE). Linked to menu items to define what customizations are allowed for each item.

- **MenuRegistry.java** — The central catalog of all available menu items. Supports lookup by ID, name, or category. Handles availability checks (time of day, seasonal). This is what the order system asks "does this item exist and can it be ordered right now?"

---

## Package: `local.dev.order`
**Purpose:** Manages the process of building and tracking customer orders.

### Files

- **Order.java** — Represents a customer's full order. Contains a list of line items, an order status (OPEN, SUBMITTED, IN_PROGRESS, READY, COMPLETED, CANCELLED), timestamps, and the calculated total. Tracks the order through its entire lifecycle.

- **LineItem.java** — A single entry in an order: one menu item with its selected customizations and quantity. Captures the price at the time of ordering so menu price changes don't affect existing orders. This is the bridge between what's on the menu and what the customer actually ordered.

- **OrderBuilder.java** — Handles the process of assembling an order. Add items, remove items, apply customizations, change quantities. Validates that items are available, customizations are allowed, and the order makes sense before submission.

- **OrderManager.java** — Tracks all orders in the system (open, in-progress, completed). Manages status transitions with validation (e.g., can't go from CANCELLED back to OPEN). Coordinates with pricing, inventory, and persistence when an order is submitted.

---

## Package: `local.dev.pricing`
**Purpose:** All money-related calculations, kept separate from order logic.

### Files

- **PricingEngine.java** — The main entry point for price calculation. Takes an order and computes the subtotal, applies discounts, adds tax, and returns the final total. Coordinates between discount rules and tax calculation.

- **DiscountRule.java** — Interface (or base class) for discount logic. Implementations could include combo meal discounts (burger + fries + drink = $2 off), percentage-off promotions (10% off orders over $20), or buy-one-get-one deals. Multiple rules can stack with a configurable cap.

- **TaxCalculator.java** — Calculates sales tax on an order. Handles different tax rates if needed (e.g., some states don't tax food but do tax drinks). Separated from the pricing engine so tax logic can change independently.

---

## Package: `local.dev.inventory`
**Purpose:** Tracks ingredient stock levels and links menu items to the raw ingredients they consume.

### Files

- **InventoryManager.java** — The main inventory controller. Checks if items are in stock, decrements ingredients when an order is submitted, and marks menu items as unavailable when ingredients run out. Coordinates with the menu system to keep availability accurate.

- **IngredientMap.java** — Maps menu items and customizations to the raw ingredients they consume. A Big Burger needs: 1 patty, 1 bun, 1 lettuce, 1 tomato. Adding bacon consumes: 1 bacon strip. This mapping is how the system knows what to decrement from stock.

- **StockLevel.java** — Represents the current quantity of a single ingredient (e.g., "burger patties: 45 remaining"). Simple data class holding ingredient name, current quantity, and a low-stock threshold for alerts.

---

## Package: `local.dev.persistence`
**Purpose:** Saves and loads system state to/from disk. Ensures nothing is lost on restart.

### Files

- **OrderStore.java** — Serializes orders to JSON files and loads them back. Handles both active and completed orders. Writes on every state change (order created, submitted, completed) so data is never lost.

- **InventoryStore.java** — Saves and loads inventory state (stock levels and ingredient mappings) to JSON. Updated when stock changes and loaded on startup to restore the last known inventory state.

---

## Package: `local.dev.receipt`
**Purpose:** Generates formatted output from completed orders.

### Files

- **ReceiptGenerator.java** — Creates a customer-facing receipt from a completed order. Shows each line item with customizations indented underneath, subtotal, discounts, tax, and total. Formatted as a text string mimicking a printed receipt.

- **KitchenTicket.java** — Creates a simplified ticket for the kitchen. Shows only item names, customizations, and quantities — no prices. This is what the kitchen staff sees to know what to prepare.

---

## Package: `local.dev` (root)
- **App.java** — The application entry point. Wires all packages together, initializes the menu, and provides the main interface for interacting with the POS system.

---

## Data Flow

```
Customer places order:
  OrderBuilder → MenuRegistry (validate item exists and is available)
               → OrderBuilder creates LineItem (captures price snapshot)
               → Order (adds LineItem)

Order is submitted:
  OrderManager → PricingEngine (calculate total)
               → DiscountRule (apply any discounts)
               → TaxCalculator (add tax)
               → InventoryManager (decrement ingredients)
               → OrderStore (persist the order)

Order is completed:
  OrderManager → ReceiptGenerator (customer receipt)
               → KitchenTicket (kitchen display)
               → OrderStore (update status)
```