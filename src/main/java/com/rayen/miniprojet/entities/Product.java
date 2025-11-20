package com.rayen.miniprojet.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data                 // Génère Getters, Setters, toString, equals, hashCode
@NoArgsConstructor    // Génère le constructeur vide (obligatoire pour JPA)
@AllArgsConstructor   // Génère le constructeur avec tous les arguments
public class Product {

    @Id @GeneratedValue
    private Long id;
    
    private String sku;
    private String name;
    private int quantity;
    private int minThreshold;
    
    // Constructeur personnalisé sans ID (optionnel, mais pratique pour le DataLoader)
    public Product(String sku, String name, int quantity, int minThreshold) {
        this.sku = sku;
        this.name = name;
        this.quantity = quantity;
        this.minThreshold = minThreshold;
    }
}