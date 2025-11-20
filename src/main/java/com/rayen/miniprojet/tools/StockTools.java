package com.rayen.miniprojet.tools;

import com.rayen.miniprojet.entities.Product;
import com.rayen.miniprojet.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
@RequiredArgsConstructor
public class StockTools {

    private final ProductRepository repo;

    @Bean
    @Description("Donne la quantité et le statut d'un produit via son code SKU")
    public Function<StockRequest, StockInfo> getProductInfo() {
        return request -> {
            Product p = repo.findBySku(request.sku());
            if (p == null) {
                return new StockInfo(request.sku(), "Inconnu", 0, "Non trouvé");
            }
            
            String status = (p.getQuantity() <= p.getMinThreshold()) ? "ALERTE RUPTURE" : "STOCK OK";
            return new StockInfo(p.getSku(), p.getName(), p.getQuantity(), status);
        };
    }

    @Bean
    @Description("Retourne la liste des produits qui sont en dessous de leur seuil minimum")
    public Function<Void, List<StockInfo>> getLowStockProducts() {
        return unused -> repo.findAll().stream()
                .filter(p -> p.getQuantity() <= p.getMinThreshold())
                .map(p -> new StockInfo(p.getSku(), p.getName(), p.getQuantity(), "ALERTE RUPTURE"))
                .collect(Collectors.toList());
    }

    // Records pour les paramètres et réponses
    public record StockRequest(String sku) {}
    public record StockInfo(String sku, String name, int quantity, String status) {}
}