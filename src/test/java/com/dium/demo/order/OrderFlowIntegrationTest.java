package com.dium.demo.order;

import com.dium.demo.dto.requests.ModifierGroupRequest;
import com.dium.demo.dto.requests.ModifierRequest;
import com.dium.demo.dto.requests.OrderItemRequest;
import com.dium.demo.dto.requests.OrderRequest;
import com.dium.demo.dto.requests.ProductRequest;
import com.dium.demo.dto.requests.VenueRequest;
import com.dium.demo.dto.responses.ModifierGroupResponse;
import com.dium.demo.dto.responses.OrderResponse;
import com.dium.demo.dto.responses.ProductResponse;
import com.dium.demo.dto.responses.VenueResponse;
import com.dium.demo.enums.OrderStatus;
import com.dium.demo.enums.ProductCategory;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.AccessDeniedException;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.models.User;
import com.dium.demo.repositories.OrderRepository;
import com.dium.demo.repositories.ProductRepository;
import com.dium.demo.repositories.VenueRepository;
import com.dium.demo.services.order.KitchenOrderService;
import com.dium.demo.services.order.OrderService;
import com.dium.demo.services.order.PaymentService;
import com.dium.demo.services.product.ModifierGroupService;
import com.dium.demo.services.product.ModifierService;
import com.dium.demo.services.product.ProductService;
import com.dium.demo.services.venue.VenueService;
import com.dium.demo.support.AbstractIntegrationTest;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** Сквозной сценарий: заведение → меню → заказ → кухня, на PostgreSQL со схемой из миграций. */
class OrderFlowIntegrationTest extends AbstractIntegrationTest {
    private static final String OWNER_PHONE = "77020000001";
    private static final String CLIENT_PHONE = "77020000002";

    @Autowired
    private VenueService venueService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ModifierGroupService modifierGroupService;
    @Autowired
    private ModifierService modifierService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private KitchenOrderService kitchenOrderService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private VenueRepository venueRepository;

    private User owner;
    private User client;
    private VenueResponse venue;
    private ProductResponse product;
    private ModifierGroupResponse modifierGroup;

    @BeforeEach
    void setUpMenu() throws Exception {
        owner = createUser(OWNER_PHONE, UserRole.VENUE_OWNER);
        client = createUser(CLIENT_PHONE, UserRole.CLIENT);

        authenticate(owner);
        venue = venueService.createVenue(
                new VenueRequest("Cafe", "Test cafe", "Main st. 1", null, true, BigDecimal.valueOf(500)), null);
        product = productService.addProduct(
                new ProductRequest("Burger", BigDecimal.valueOf(1000), null, "Tasty", true, ProductCategory.FOOD, true),
                null);
        productService.toggleStock(product.id());
        modifierGroup = modifierGroupService.addModifierGroup(product.id(), new ModifierGroupRequest(
                "Extras", false, 0, 2,
                List.of(new ModifierRequest("Cheese", BigDecimal.valueOf(200), true))));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        venueRepository.deleteAll();
    }

    @Test
    void orderGoesFromCreationToCompletion() {
        OrderResponse order = createOrderAsClient("Dorm 5");

        // 2 × (1000 + 200) + доставка 500
        assertThat(order.totalSum()).isEqualByComparingTo("2900");
        assertThat(order.deliveryFee()).isEqualByComparingTo("500");
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.pickupCode()).isBetween(1000, 9999);
        assertThat(order.venueOwnerPhone()).isEqualTo(OWNER_PHONE);
        assertThat(order.items()).hasSize(1);
        verify(senderService, timeout(3000))
                .trySendMessage(eq(OWNER_PHONE), contains("Новый заказ #" + order.id()));

        authenticate(owner);
        assertThat(kitchenOrderService.getKitchenOrders()).extracting(OrderResponse::id).containsExactly(order.id());
        assertThat(kitchenOrderService.getOrderHistory()).isEmpty();

        orderService.updateStatus(order.id());
        assertThat(statusOf(order)).isEqualTo(OrderStatus.PREPARING);
        verify(senderService).trySendMessage(CLIENT_PHONE, "Ваш заказ #" + order.id() + " готовится!");

        orderService.updateStatus(order.id());
        assertThat(statusOf(order)).isEqualTo(OrderStatus.READY);

        orderService.updateStatus(order.id());
        assertThat(statusOf(order)).isEqualTo(OrderStatus.COMPLETED);
        verify(senderService).trySendMessage(CLIENT_PHONE, "Ваш заказ #" + order.id() + " готов!");

        assertThatThrownBy(() -> orderService.updateStatus(order.id()))
                .isInstanceOf(BusinessLogicException.class);
        assertThat(kitchenOrderService.getKitchenOrders()).isEmpty();
        assertThat(kitchenOrderService.getOrderHistory()).hasSize(1);

        authenticate(client);
        assertThat(orderService.getOrdersByUserId()).hasSize(1);
    }

    @Test
    void pickupOrderHasNoDeliveryFee() {
        OrderResponse order = createOrderAsClient(null);

        assertThat(order.address()).isNull();
        assertThat(order.deliveryFee()).isEqualByComparingTo("0");
        assertThat(order.totalSum()).isEqualByComparingTo("2400");
    }

    @Test
    void onlyOwnerOfTheVenueCanCancelPendingOrder() {
        OrderResponse order = createOrderAsClient(null);

        authenticate(client);
        assertThatThrownBy(() -> orderService.cancelOrder(order.id()))
                .isInstanceOf(AccessDeniedException.class);

        authenticate(createUser("77020000003", UserRole.VENUE_OWNER));
        assertThatThrownBy(() -> orderService.cancelOrder(order.id()))
                .isInstanceOf(AccessDeniedException.class);

        authenticate(owner);
        orderService.cancelOrder(order.id());
        assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCELLED);
        verify(senderService).trySendMessage(CLIENT_PHONE, "Ваш заказ #" + order.id() + " отменен");

        assertThatThrownBy(() -> orderService.cancelOrder(order.id()))
                .isInstanceOf(BusinessLogicException.class);
    }

    @Test
    void orderIsRejectedWhenVenueIsClosedOrProductOutOfStock() {
        authenticate(owner);
        productService.toggleStock(product.id());
        assertThatThrownBy(() -> createOrderAsClient(null))
                .isInstanceOf(BusinessLogicException.class)
                .hasMessageContaining("not in stock");

        authenticate(owner);
        productService.toggleStock(product.id());
        venueService.toggleWork();
        assertThatThrownBy(() -> createOrderAsClient(null))
                .isInstanceOf(BusinessLogicException.class)
                .hasMessage("Venue is not working");
    }

    @Test
    void ownerManagesVenueMenuAndModifiers() throws Exception {
        Long modifierId = modifierGroup.modifiers().get(0).id();

        assertThat(venueService.getOwnerVenue().id()).isEqualTo(venue.id());
        assertThat(venueService.updateVenue(
                new VenueRequest("Cafe 2", null, "Main st. 2", null, false, BigDecimal.ZERO), null).name())
                .isEqualTo("Cafe 2");
        assertThatThrownBy(() -> venueService.createVenue(
                new VenueRequest("Second", null, "Other st.", null, false, null), null))
                .isInstanceOf(BusinessLogicException.class);

        assertThat(productService.updateProduct(product.id(),
                new ProductRequest("Big burger", BigDecimal.valueOf(1500), null, null, true, ProductCategory.FOOD, true),
                null).price()).isEqualByComparingTo("1500");
        assertThatThrownBy(() -> productService.updateProduct(product.id(),
                new ProductRequest("Bad", BigDecimal.valueOf(-1), null, null, true, ProductCategory.FOOD, true), null))
                .isInstanceOf(BusinessLogicException.class);

        assertThat(modifierService.editModifier(modifierId,
                new ModifierRequest("Double cheese", BigDecimal.valueOf(300), true)).name())
                .isEqualTo("Double cheese");
        modifierService.toggleStock(modifierId);

        ModifierGroupResponse edited = modifierGroupService.editModifierGroup(modifierGroup.id(),
                new ModifierGroupRequest("Toppings", true, 1, 3,
                        List.of(new ModifierRequest("Bacon", BigDecimal.valueOf(400), true))));
        assertThat(edited.name()).isEqualTo("Toppings");
        assertThat(edited.modifiers()).extracting("name").containsExactly("Bacon");

        modifierGroupService.toggleRequired(modifierGroup.id());
        assertThat(modifierGroupService.getModifierGroups(product.id()).get(0).required()).isFalse();

        modifierService.delete(edited.modifiers().get(0).id());
        modifierGroupService.deleteModifierGroup(modifierGroup.id());
        assertThat(modifierGroupService.getModifierGroups(product.id())).isEmpty();

        productService.deleteProduct(product.id());
        assertThat(productService.getMenuByVenueId(venue.id())).isEmpty();
    }

    @Test
    void paymentUrlComesFromVenue() {
        OrderResponse order = createOrderAsClient(null);

        assertThatThrownBy(() -> paymentService.getPaymentUrl(order.id()))
                .isInstanceOf(EntityNotFoundException.class);

        var storedVenue = venueRepository.findByIdOrThrow(venue.id());
        storedVenue.setKaspiUrl("https://pay.kaspi.kz/pay/test");
        venueRepository.save(storedVenue);

        assertThat(paymentService.getPaymentUrl(order.id())).isEqualTo("https://pay.kaspi.kz/pay/test");
    }

    private OrderResponse createOrderAsClient(String address) {
        Long modifierId = modifierGroup.modifiers().get(0).id();

        authenticate(client);
        return orderService.createOrder(new OrderRequest(
                venue.id(),
                List.of(new OrderItemRequest(product.id(), 2, List.of(modifierId))),
                address,
                "No onions",
                null,
                "Aidar A."));
    }

    private OrderStatus statusOf(OrderResponse order) {
        return orderRepository.findByIdOrThrow(order.id()).getStatus();
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
