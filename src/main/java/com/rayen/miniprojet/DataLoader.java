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
            repo.save(new Product("SCREEN-27-4K", "Écran 27\" 4K Pro", 10, 1500)); // Haute Valeur ( > 1000€)
            repo.save(new Product("HEADSET-G9", "Casque Gaming G9", 80, 120));     // Stock OK
            repo.save(new Product("CABLE-USB-C", "Câble USB-C 2m", 1, 10));          // Stock très faible
            repo.save(new Product("PROMO-JOY-01", "Joystick Promo", 15, 45));       // Article PROMO
            repo.save(new Product("HDD-8TB-NAS", "Disque Dur 8TB NAS", 2, 350));    // Stock critique
            repo.save(new Product("KIT-CLEAN", "Kit Nettoyage Écran", 120, 5));     // Stock très élevé
            System.out.println("✅ Données de stock chargées en mémoire !");

        };
    }
}