package com.dium.demo.security.access;

import com.dium.demo.repositories.ModifierGroupRepository;
import com.dium.demo.repositories.ModifierRepository;
import com.dium.demo.repositories.OrderRepository;
import com.dium.demo.repositories.ProductRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OwnershipConfig {

    @Bean
    OwnerLookup productOwnerLookup(ProductRepository repository) {
        return OwnerLookup.of(OwnedResource.PRODUCT, repository);
    }

    @Bean
    OwnerLookup modifierGroupOwnerLookup(ModifierGroupRepository repository) {
        return OwnerLookup.of(OwnedResource.MODIFIER_GROUP, repository);
    }

    @Bean
    OwnerLookup modifierOwnerLookup(ModifierRepository repository) {
        return OwnerLookup.of(OwnedResource.MODIFIER, repository);
    }

    @Bean
    OwnerLookup orderOwnerLookup(OrderRepository repository) {
        return OwnerLookup.of(OwnedResource.ORDER, repository);
    }
}
