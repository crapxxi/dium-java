package com.dium.demo.models;

import com.dium.demo.enums.ProductCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ModifierGroup> modifierGroups = new ArrayList<>();

    @Column(nullable = false)
    private String name;
    private BigDecimal price;
    private ProductCategory category;

    private Boolean inStock;
    private String imageUrl;
    private String description;
    private Boolean deleted;
    private Boolean hasModifiers;
}
