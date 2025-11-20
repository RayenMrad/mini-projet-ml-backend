package com.rayen.miniprojet.repositories;

import com.rayen.miniprojet.entities.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Product findBySku(String sku);
}