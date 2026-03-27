package local.dev.persistence;

import local.dev.menu.Customization;
import local.dev.order.LineItem;
import local.dev.order.Order;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderStore {
    private final Path storageDirectory;

    public OrderStore(String directoryPath) {
        this.storageDirectory = Paths.get(directoryPath);
        try {
            Files.createDirectories(storageDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create order storage directory", e);
        }
    }

    public void save(Order order) {
        Path file = storageDirectory.resolve(order.getId() + ".txt");
        List<String> lines = new ArrayList<>();
        lines.add("ID=" + order.getId());
        lines.add("STATUS=" + order.getStatus());
        lines.add("CREATED=" + order.getOrderCreation());
        if (order.getOrderCompleted() != null)
            lines.add("COMPLETED=" + order.getOrderCompleted());
        lines.add("SUBTOTAL=" + order.getSubTotalAmount());
        lines.add("TAX=" + order.getTaxAmount());
        lines.add("TOTAL=" + order.getTotalAmount());

        for (LineItem item : order.getLineItems()) {
            StringBuilder sb = new StringBuilder("ITEM=")
                .append(item.getId()).append("|")
                .append(item.getMenuItem().getId()).append("|")
                .append(item.getMenuItem().getName()).append("|")
                .append(item.getQuantity()).append("|")
                .append(item.getUnitPrice());
            for (Customization c : item.getCustomizations()) {
                sb.append("|CUST:").append(c.getName())
                  .append(":").append(c.getModification())
                  .append(":").append(c.getPriceChange());
            }
            lines.add(sb.toString());
        }

        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save order " + order.getId(), e);
        }
    }

    public boolean delete(UUID orderId) {
        try {
            return Files.deleteIfExists(storageDirectory.resolve(orderId + ".txt"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete order " + orderId, e);
        }
    }

    public List<List<String>> loadAllRaw() {
        List<List<String>> results = new ArrayList<>();
        try (var stream = Files.list(storageDirectory)) {
            stream.filter(p -> p.toString().endsWith(".txt"))
                  .forEach(p -> {
                      try {
                          results.add(Files.readAllLines(p, StandardCharsets.UTF_8));
                      } catch (IOException e) {
                          throw new RuntimeException("Failed to read order file: " + p, e);
                      }
                  });
        } catch (IOException e) {
            throw new RuntimeException("Failed to list order files", e);
        }
        return results;
    }
}
