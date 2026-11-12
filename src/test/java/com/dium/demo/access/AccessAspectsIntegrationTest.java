package com.dium.demo.access;

import com.dium.demo.dto.requests.VenueOwnerRequest;
import com.dium.demo.dto.responses.UserResponse;
import com.dium.demo.enums.ProductCategory;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.AccessDeniedException;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.models.Product;
import com.dium.demo.models.User;
import com.dium.demo.models.Venue;
import com.dium.demo.repositories.ProductRepository;
import com.dium.demo.repositories.VenueRepository;
import com.dium.demo.services.auth.AuthService;
import com.dium.demo.services.product.ProductService;
import com.dium.demo.services.venue.VenueService;
import com.dium.demo.support.AbstractIntegrationTest;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Аспекты {@code @RequireRole} и {@code @CheckOwnership} на реальных сервисах и PostgreSQL. */
class AccessAspectsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductService productService;
    @Autowired
    private VenueService venueService;
    @Autowired
    private AuthService authService;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private ProductRepository productRepository;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        productRepository.deleteAll();
        venueRepository.deleteAll();
    }

    @Test
    void ownerCanChangeOwnProduct() {
        User owner = createUser("77010000001", UserRole.VENUE_OWNER);
        Product product = createProduct(createVenue(owner));

        authenticate(owner);
        productService.toggleStock(product.getId());

        assertThat(productRepository.findByIdOrThrow(product.getId()).getInStock()).isTrue();
    }

    @Test
    void ownerCannotChangeProductOfAnotherVenue() {
        User owner = createUser("77010000001", UserRole.VENUE_OWNER);
        User stranger = createUser("77010000002", UserRole.VENUE_OWNER);
        Product product = createProduct(createVenue(owner));

        authenticate(stranger);

        assertThatThrownBy(() -> productService.toggleStock(product.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("belongs to another venue");
        assertThat(productRepository.findByIdOrThrow(product.getId()).getInStock()).isFalse();
    }

    @Test
    void clientIsRejectedByRoleCheckBeforeOwnership() {
        User owner = createUser("77010000001", UserRole.VENUE_OWNER);
        User client = createUser("77010000003", UserRole.CLIENT);
        Product product = createProduct(createVenue(owner));

        authenticate(client);

        assertThatThrownBy(() -> productService.toggleStock(product.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("required role");
        assertThatThrownBy(() -> venueService.getOwnerVenue())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingResourceIsReportedAsNotFound() {
        authenticate(createUser("77010000001", UserRole.VENUE_OWNER));

        assertThatThrownBy(() -> productService.toggleStock(999_999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Product not found");
    }

    @Test
    void anonymousCallIsRejected() {
        assertThatThrownBy(() -> venueService.toggleWork())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminAssignsExistingUserAsVenueOwner() {
        User admin = createUser("77010000009", UserRole.ADMIN);
        createUser("77010000003", UserRole.CLIENT);

        authenticate(admin);
        UserResponse response = authService.assignVenueOwner(new VenueOwnerRequest("8 701 000 00 03"));

        assertThat(response.role()).isEqualTo(UserRole.VENUE_OWNER);
        assertThat(userRepository.findByPhone("77010000003").orElseThrow().getRole())
                .isEqualTo(UserRole.VENUE_OWNER);

        assertThatThrownBy(() -> authService.assignVenueOwner(new VenueOwnerRequest("77010000003")))
                .isInstanceOf(BusinessLogicException.class);
        assertThatThrownBy(() -> authService.assignVenueOwner(new VenueOwnerRequest("77019999999")))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private Venue createVenue(User owner) {
        return venueRepository.save(Venue.builder()
                .name("Test venue")
                .address("Test address")
                .canDeliver(false)
                .deliveryPrice(BigDecimal.ZERO)
                .owner(owner)
                .build());
    }

    private Product createProduct(Venue venue) {
        return productRepository.save(Product.builder()
                .venue(venue)
                .name("Test product")
                .price(BigDecimal.valueOf(1000))
                .category(ProductCategory.values()[0])
                .inStock(false)
                .deleted(false)
                .hasModifiers(false)
                .build());
    }
}
