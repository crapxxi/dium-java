package com.dium.demo.services.order;

import com.dium.demo.dto.requests.OrderItemRequest;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.models.Modifier;
import com.dium.demo.models.Order;
import com.dium.demo.models.OrderItem;
import com.dium.demo.models.Product;
import com.dium.demo.repositories.ModifierRepository;
import com.dium.demo.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Собирает позицию заказа: проверяет товар и модификаторы, фиксирует цену на момент покупки. */
@Component
@RequiredArgsConstructor
public class OrderItemFactory {
    private final ProductRepository productRepository;
    private final ModifierRepository modifierRepository;

    public OrderItem create(Order order, OrderItemRequest request) {
        if (request.count() == null || request.count() <= 0) {
            throw new BusinessLogicException("Count must be greater than 0");
        }

        Product product = findAvailableProduct(order, request.productId());
        List<Modifier> modifiers = findModifiers(product, request.modifierIds());

        return OrderItem.builder()
                .order(order)
                .product(product)
                .count(request.count())
                .modifiers(modifiers)
                .priceAtPurchase(unitPrice(product, modifiers))
                .build();
    }

    private Product findAvailableProduct(Order order, Long productId) {
        Product product = productRepository.findByIdOrThrow(productId);

        if (!Boolean.TRUE.equals(product.getInStock())) {
            throw new BusinessLogicException("Product " + product.getName() + " is not in stock");
        }
        if (!product.getVenue().getId().equals(order.getVenue().getId())) {
            throw new BusinessLogicException("Access denied: product is not from this venue");
        }
        return product;
    }

    private List<Modifier> findModifiers(Product product, List<Long> modifierIds) {
        if (modifierIds == null || modifierIds.isEmpty()) {
            return List.of();
        }

        List<Modifier> modifiers = modifierRepository.findAllById(modifierIds);
        for (Modifier modifier : modifiers) {
            if (!modifier.getModifierGroup().getProduct().getId().equals(product.getId())) {
                throw new BusinessLogicException("Access denied: invalid modifier for product: " + product.getName());
            }
        }
        return modifiers;
    }

    private BigDecimal unitPrice(Product product, List<Modifier> modifiers) {
        BigDecimal price = modifiers.stream()
                .map(Modifier::getPriceDelta)
                .reduce(product.getPrice(), BigDecimal::add);

        return price.max(BigDecimal.ZERO);
    }
}
