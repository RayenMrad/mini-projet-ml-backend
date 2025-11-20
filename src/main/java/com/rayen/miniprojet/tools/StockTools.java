package com.rayen.miniprojet.tools;

import com.rayen.miniprojet.entities.Product;
import com.rayen.miniprojet.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor // Lombok injecte le repo automatiquement
public class StockTools {

    private final ProductRepository repo;

    @Tool(description = "Donne la quantité et le statut d'un produit via son code SKU.")
    public StockInfo getProductInfo(@ToolParam(description = "Le SKU du produit (ex: PS5-SLIM)") String sku) {
        Product p = repo.findBySku(sku);
        if (p == null) return new StockInfo(sku, "Inconnu", 0, "Non trouvé");
        
        String status = (p.getQuantity() <= p.getMinThreshold()) ? "ALERTE RUPTURE" : "STOCK OK";
        return new StockInfo(p.getSku(), p.getName(), p.getQuantity(), status);
    }

    @Tool(description = "Retourne la liste des produits qui sont en dessous de leur seuil minimum.")
    public List<StockInfo> getLowStockProducts() {
        return repo.findAll().stream()
                .filter(p -> p.getQuantity() <= p.getMinThreshold())
                .map(p -> new StockInfo(p.getSku(), p.getName(), p.getQuantity(), "ALERTE RUPTURE"))
                .collect(Collectors.toList());
    }

    public record StockInfo(String sku, String name, int quantity, String status) {}
}