package com.dium.demo.delivery;

import com.dium.demo.dto.requests.OrderItemRequest;
import com.dium.demo.dto.requests.OrderRequest;
import com.dium.demo.dto.requests.ProductRequest;
import com.dium.demo.dto.requests.VenueRequest;
import com.dium.demo.dto.responses.ProductResponse;
import com.dium.demo.dto.responses.VenueResponse;
import com.dium.demo.enums.DeliveryIssueStatus;
import com.dium.demo.enums.DeliveryIssueType;
import com.dium.demo.enums.DeliveryParticipant;
import com.dium.demo.enums.DeliveryStatus;
import com.dium.demo.enums.FulfillmentType;
import com.dium.demo.enums.OrderStatus;
import com.dium.demo.enums.PaymentMethod;
import com.dium.demo.enums.ProductCategory;
import com.dium.demo.enums.UserRole;
import com.dium.demo.models.Delivery;
import com.dium.demo.models.DeliveryIssue;
import com.dium.demo.models.User;
import com.dium.demo.repositories.DeliveryIssueRepository;
import com.dium.demo.repositories.DeliveryRepository;
import com.dium.demo.repositories.OrderRepository;
import com.dium.demo.repositories.ProductRepository;
import com.dium.demo.repositories.VenueRepository;
import com.dium.demo.services.delivery.CourierDeliveryService;
import com.dium.demo.services.delivery.CustomerDeliveryService;
import com.dium.demo.services.delivery.DeliveryTimeoutService;
import com.dium.demo.services.delivery.VenueDeliveryService;
import com.dium.demo.services.order.OrderService;
import com.dium.demo.services.product.ProductService;
import com.dium.demo.services.venue.VenueService;
import com.dium.demo.services.wallet.WalletService;
import com.dium.demo.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Сроки этапов доставки: когда начинается срок и что делает система, когда он истёк.
 * Время «двигается» записью срока в прошлое напрямую в БД.
 */
class DeliveryTimeoutIntegrationTest extends AbstractIntegrationTest {
    private static final String CUSTOMER_PHONE = "77050000002";
    private static final String ADDRESS = "Dorm 5, room 12";

    @Autowired
    private VenueService venueService;
    @Autowired
    private ProductService productService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private CourierDeliveryService courierService;
    @Autowired
    private CustomerDeliveryService customerService;
    @Autowired
    private VenueDeliveryService venueDeliveryService;
    @Autowired
    private DeliveryTimeoutService timeoutService;
    @Autowired
    private WalletService walletService;
    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private DeliveryIssueRepository issueRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private User customer;
    private User courier;
    private User admin;
    private VenueResponse venue;
    private ProductResponse product;

    @BeforeEach
    void setUp() throws Exception {
        owner = createUser("77050000001", UserRole.VENUE_OWNER);
        customer = createUser(CUSTOMER_PHONE, UserRole.CLIENT);
        courier = createUser("77050000003", UserRole.CLIENT);
        admin = createUser("77050000009", UserRole.ADMIN);

        authenticate(owner);
        venue = venueService.createVenue(
                new VenueRequest("Cafe", "Test cafe", "Main st. 1", null, true, BigDecimal.ZERO), null);
        product = productService.addProduct(
                new ProductRequest("Burger", BigDecimal.valueOf(1000), null, "Tasty", true, ProductCategory.FOOD, false),
                null);
        productService.toggleStock(product.id());
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        issueRepository.deleteAll();
        deliveryRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        venueRepository.deleteAll();
    }

    @Test
    void deadlineFollowsTheStages() {
        Long orderId = placeOrder(PaymentMethod.KASPI);
        Long deliveryId = openDelivery(orderId);
        assertThat(deadline(deliveryId)).as("найти курьера — 30 минут")
                .isCloseTo(Instant.now().plus(Duration.ofMinutes(30)), within(Duration.ofMinutes(1)));

        authenticate(courier);
        String pickupCode = courierService.accept(deliveryId).pickupCode();
        assertThat(deadline(deliveryId)).as("заказ ещё готовится — курьер не виноват").isNull();

        markReady(orderId);
        assertThat(deadline(deliveryId)).as("заказ готов — 20 минут, чтобы забрать")
                .isCloseTo(Instant.now().plus(Duration.ofMinutes(20)), within(Duration.ofMinutes(1)));
        authenticate(courier);
        assertThat(courierService.getMyDeliveries().get(0).deadlineAt()).isNotNull();

        authenticate(owner);
        venueDeliveryService.confirmPickup(deliveryId, pickupCode);
        assertThat(deadline(deliveryId)).as("забрал — час, чтобы отдать клиенту")
                .isCloseTo(Instant.now().plus(Duration.ofMinutes(60)), within(Duration.ofMinutes(1)));

        authenticate(customer);
        String code = customerService.getMyOrderDeliveries().get(0).deliveryCode();
        authenticate(courier);
        courierService.confirmDelivered(deliveryId, code);
        assertThat(deadline(deliveryId)).isNull();
    }

    @Test
    void nobodyTookTheOrderSoDeliveryIsCancelledAndPointsReturned() {
        give(customer, 2000);
        Long orderId = placeOrder(PaymentMethod.POINTS); // 1200 за бургер + 120 за доставку
        Long deliveryId = openDelivery(orderId);
        assertThat(balance(customer)).isEqualTo(680);

        expire(deliveryId);
        timeoutService.processOverdue();

        Delivery delivery = deliveryRepository.findByIdOrThrow(deliveryId);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        assertThat(delivery.getAdminNote()).contains("никто из курьеров");
        assertThat(orderRepository.findByIdOrThrow(orderId).getStatus())
                .as("заказ остаётся — клиент заберёт сам").isEqualTo(OrderStatus.PREPARING);
        assertThat(balance(customer)).as("баллы за доставку вернулись").isEqualTo(800);
        verify(senderService).trySendMessage(eq(CUSTOMER_PHONE), contains("никто из курьеров не взял заказ за 30 мин"));
    }

    @Test
    void courierWhoDidNotComeForReadyOrderIsReleased() {
        Long orderId = placeOrder(PaymentMethod.KASPI);
        Long deliveryId = openDelivery(orderId);
        authenticate(courier);
        courierService.accept(deliveryId);
        markReady(orderId);

        expire(deliveryId);
        timeoutService.processOverdue();

        Delivery delivery = deliveryRepository.findByIdOrThrow(deliveryId);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SEARCHING_COURIER);
        assertThat(delivery.getCourier()).isNull();
        assertThat(delivery.getDeadlineAt()).as("снова ищем курьера — новый срок").isAfter(Instant.now());
        DeliveryIssue issue = singleOpenIssue(deliveryId);
        assertThat(issue.getType()).isEqualTo(DeliveryIssueType.COURIER_NOT_ARRIVED);
        assertThat(issue.getReportedBy()).isEqualTo(DeliveryParticipant.SYSTEM);
        verify(senderService).trySendMessage(eq(CUSTOMER_PHONE), contains("Ищем другого курьера"));

        // Заказ снова доступен курьерам
        authenticate(createUser("77050000004", UserRole.CLIENT));
        assertThat(courierService.getAvailable()).extracting(d -> d.id()).contains(deliveryId);
    }

    @Test
    void courierIsNotBlamedWhileTheOrderIsStillCooking() {
        Long orderId = placeOrder(PaymentMethod.KASPI);
        Long deliveryId = openDelivery(orderId);
        authenticate(courier);
        courierService.accept(deliveryId);

        expire(deliveryId); // срок истёк, но заказ ещё PREPARING
        timeoutService.processOverdue();

        Delivery delivery = deliveryRepository.findByIdOrThrow(deliveryId);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(delivery.getCourier().getId()).isEqualTo(courier.getId());
        assertThat(delivery.getDeadlineAt()).isNull();
        assertThat(issueRepository.findAll()).isEmpty();
    }

    @Test
    void lateDeliveryIsReportedToAdminOnce() {
        Long orderId = placeOrder(PaymentMethod.KASPI);
        Long deliveryId = openDelivery(orderId);
        authenticate(courier);
        String pickupCode = courierService.accept(deliveryId).pickupCode();
        markReady(orderId);
        authenticate(owner);
        venueDeliveryService.confirmPickup(deliveryId, pickupCode);

        expire(deliveryId);
        timeoutService.processOverdue();
        timeoutService.processOverdue();

        Delivery delivery = deliveryRepository.findByIdOrThrow(deliveryId);
        assertThat(delivery.getStatus()).as("заказ у курьера — его не снимаем").isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(delivery.getDeadlineAt()).isNull();
        assertThat(singleOpenIssue(deliveryId).getType()).isEqualTo(DeliveryIssueType.NOT_DELIVERED);
    }

    @Test
    void deliveriesWithinTheirDeadlineAreLeftAlone() {
        Long deliveryId = openDelivery(placeOrder(PaymentMethod.KASPI));

        timeoutService.processOverdue();

        assertThat(deliveryRepository.findByIdOrThrow(deliveryId).getStatus()).isEqualTo(DeliveryStatus.SEARCHING_COURIER);
    }

    // ---------------------------------------------------------------- помощники

    private Long placeOrder(PaymentMethod method) {
        authenticate(customer);
        return orderService.createOrder(new OrderRequest(
                venue.id(),
                List.of(new OrderItemRequest(product.id(), 1, List.of())),
                ADDRESS,
                null,
                null,
                method == PaymentMethod.KASPI ? "Aidar A." : null,
                FulfillmentType.DELIVERY,
                method)).id();
    }

    private Long openDelivery(Long orderId) {
        authenticate(owner);
        orderService.updateStatus(orderId); // PREPARING
        return deliveryRepository.findByOrderId(orderId).orElseThrow().getId();
    }

    private void markReady(Long orderId) {
        authenticate(owner);
        orderService.updateStatus(orderId); // READY
    }

    private void expire(Long deliveryId) {
        jdbcTemplate.update("UPDATE deliveries SET deadline_at = now() - interval '1 minute' WHERE id = ?", deliveryId);
    }

    private Instant deadline(Long deliveryId) {
        return deliveryRepository.findByIdOrThrow(deliveryId).getDeadlineAt();
    }

    private DeliveryIssue singleOpenIssue(Long deliveryId) {
        List<DeliveryIssue> issues = issueRepository.findAll().stream()
                .filter(i -> i.getDelivery().getId().equals(deliveryId) && i.getStatus() == DeliveryIssueStatus.OPEN)
                .toList();
        assertThat(issues).hasSize(1);
        return issues.get(0);
    }

    private void give(User user, long points) {
        authenticate(admin);
        walletService.adjust(user.getId(), points, "Тестовое начисление");
    }

    private long balance(User user) {
        authenticate(user);
        return walletService.getMyWallet().balance();
    }

    private static void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
