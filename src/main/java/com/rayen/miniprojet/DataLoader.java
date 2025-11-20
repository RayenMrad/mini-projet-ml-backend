package com.rayen.miniprojet;

import com.rayen.miniprojet.entities.Product;
import com.rayen.miniprojet.repositories.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataLoader {
    @Bean
    CommandLineRunner initDatabase(ProductRepository repo) {
        return args -> {
            repo.save(new Product("IPHONE-15", "iPhone 15 Pro", 50, 10)); // OK
            repo.save(new Product("PS5-SLIM", "PlayStation 5", 2, 10));   // RUPTURE (2 < 10)
            repo.save(new Product("PC-DELL", "Dell XPS 13", 0, 5));       // RUPTURE TOTALE
            System.out.println("✅ Données de stock chargées en mémoire !");
        };
    }
}