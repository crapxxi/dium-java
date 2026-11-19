package com.dium.demo.delivery;

import com.dium.demo.dto.requests.DeliveryIssueRequest;
import com.dium.demo.dto.requests.OrderItemRequest;
import com.dium.demo.dto.requests.OrderRequest;
import com.dium.demo.dto.requests.ProductRequest;
import com.dium.demo.dto.requests.VenueRequest;
import com.dium.demo.dto.responses.DeliveryResponses;
import com.dium.demo.dto.responses.ProductResponse;
import com.dium.demo.dto.responses.VenueResponse;
import com.dium.demo.enums.DeliveryIssueStatus;
import com.dium.demo.enums.DeliveryIssueType;
import com.dium.demo.enums.DeliveryParticipant;
import com.dium.demo.enums.DeliveryStatus;
import com.dium.demo.enums.FulfillmentType;
import com.dium.demo.enums.OrderStatus;
import com.dium.demo.enums.ProductCategory;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.AccessDeniedException;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.exceptions.ConflictException;
import com.dium.demo.exceptions.InvalidHandoverCodeException;
import com.dium.demo.models.Delivery;
import com.dium.demo.models.Order;
import com.dium.demo.models.User;
import com.dium.demo.repositories.DeliveryIssueRepository;
import com.dium.demo.repositories.DeliveryRepository;
import com.dium.demo.repositories.OrderRepository;
import com.dium.demo.repositories.ProductRepository;
import com.dium.demo.repositories.VenueRepository;
import com.dium.demo.services.auth.JwtService;
import com.dium.demo.services.delivery.AdminDeliveryService;
import com.dium.demo.services.delivery.CourierDeliveryService;
import com.dium.demo.services.delivery.CustomerDeliveryService;
import com.dium.demo.services.delivery.DeliveryIssueService;
import com.dium.demo.services.delivery.DeliveryOrderStatusListener;
import com.dium.demo.services.delivery.VenueDeliveryService;
import com.dium.demo.services.order.OrderService;
import com.dium.demo.services.product.ProductService;
import com.dium.demo.services.venue.VenueService;
import com.dium.demo.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Доставка целиком на PostgreSQL со схемой из миграций: заказ → лента курьеров → код курьера
 * в заведении → код клиента у двери, плюс блокировка кодов, конфликты и действия администратора.
 */
class DeliveryFlowIntegrationTest extends AbstractIntegrationTest {
    private static final String OWNER_PHONE = "77030000001";
    private static final String CUSTOMER_PHONE = "77030000002";
    private static final String COURIER_PHONE = "77030000003";
    private static final String COURIER2_PHONE = "77030000004";
    private static final String ADMIN_PHONE = "77030000009";

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
    private AdminDeliveryService adminService;
    @Autowired
    private DeliveryIssueService issueService;
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
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private DeliveryOrderStatusListener deliveryOrderStatusListener;

    private User owner;
    private User customer;
    private User courier;
    private User courier2;
    private User admin;
    private VenueResponse venue;
    private ProductResponse product;

    @BeforeEach
    void setUpVenue() throws Exception {
        owner = createUser(OWNER_PHONE, UserRole.VENUE_OWNER);
        customer = createUser(CUSTOMER_PHONE, UserRole.CLIENT);
        courier = createUser(COURIER_PHONE, UserRole.CLIENT);
        courier2 = createUser(COURIER2_PHONE, UserRole.CLIENT);
        admin = createUser(ADMIN_PHONE, UserRole.ADMIN);

        authenticate(owner);
        venue = venueService.createVenue(
                new VenueRequest("Cafe", "Test cafe", "Main st. 1", null, true, BigDecimal.valueOf(500)), null);
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

    // ---------------------------------------------------------------- основной сценарий

    @Test
    void orderIsDeliveredByStudentAndConfirmedWithBothCodes() {
        Long orderId = placeDeliveryOrder(customer);

        // Пока заведение не приняло заказ, курьерам его не показываем
        authenticate(courier);
        assertThat(courierService.getAvailable()).isEmpty();

        acceptByVenue(orderId);

        authenticate(customer);
        DeliveryResponses.Customer waiting = single(customerService.getMyOrderDeliveries());
        assertThat(waiting.status()).isEqualTo(DeliveryStatus.SEARCHING_COURIER);
        assertThat(waiting.deliveryCode()).matches("\\d{4}");
        // Свой заказ студент в ленте не видит
        assertThat(courierService.getAvailable()).isEmpty();

        authenticate(courier);
        DeliveryResponses.Available offer = single(courierService.getAvailable());
        assertThat(offer.orderId()).isEqualTo(orderId);
        assertThat(offer.courierFee()).isEqualByComparingTo("100");
        assertThat(offer.deliveryAddress()).isEqualTo("Dorm 5, room 12");
        assertThat(offer.itemsCount()).isEqualTo(2);

        DeliveryResponses.Courier taken = courierService.accept(offer.id());
        assertThat(taken.status()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(taken.pickupCode()).matches("\\d{4}");
        assertThat(taken.customerPhone()).isEqualTo(CUSTOMER_PHONE);
        assertThat(taken.items()).hasSize(1);

        authenticate(customer);
        DeliveryResponses.Customer assigned = single(customerService.getMyOrderDeliveries());
        assertThat(assigned.courierName()).isEqualTo(courier.getName());
        assertThat(assigned.courierPhone()).isEqualTo(COURIER_PHONE);
        assertThat(assigned.deliveryCode()).isEqualTo(waiting.deliveryCode());

        // Заведение не может отдать заказ, пока он не готов, и не может само отметить его выданным
        authenticate(owner);
        assertThatThrownBy(() -> venueDeliveryService.confirmPickup(offer.id(), taken.pickupCode()))
                .isInstanceOf(BusinessLogicException.class)
                .hasMessageContaining("готов");
        orderService.updateStatus(orderId); // READY
        assertThatThrownBy(() -> orderService.updateStatus(orderId))
                .isInstanceOf(BusinessLogicException.class)
                .hasMessageContaining("курьер");

        DeliveryResponses.Venue handedOver = venueDeliveryService.confirmPickup(offer.id(), taken.pickupCode());
        assertThat(handedOver.status()).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(handedOver.courierName()).isEqualTo(courier.getName());

        authenticate(courier);
        DeliveryResponses.Courier onTheWay = single(courierService.getMyDeliveries());
        assertThat(onTheWay.pickupCode()).as("код курьера больше не нужен").isNull();
        assertThat(onTheWay.deliveryAttemptsLeft()).isEqualTo(5);

        DeliveryResponses.Courier delivered = courierService.confirmDelivered(offer.id(), waiting.deliveryCode());
        assertThat(delivered.status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivered.customerPhone()).as("после доставки телефон клиента скрыт").isNull();
        assertThat(orderRepository.findByIdOrThrow(orderId).getStatus()).isEqualTo(OrderStatus.COMPLETED);

        DeliveryResponses.CourierSummary summary = courierService.getSummary();
        assertThat(summary.deliveredCount()).isEqualTo(1);
        assertThat(summary.totalEarned()).isEqualByComparingTo("100");
        assertThat(summary.activeCount()).isZero();

        authenticate(customer);
        DeliveryResponses.Customer received = single(customerService.getMyOrderDeliveries());
        assertThat(received.status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(received.deliveryCode()).as("код после доставки не показывается").isNull();
        assertThat(received.courierPhone()).isNull();

        authenticate(owner);
        DeliveryResponses.Venue venueView = single(venueDeliveryService.getVenueDeliveries());
        assertThat(venueView.status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(venueView.deliveredAt()).isNotNull();

        verify(senderService).trySendMessage(eq(CUSTOMER_PHONE), contains("Курьер " + courier.getName() + " взял"));
        verify(senderService).trySendMessage(eq(CUSTOMER_PHONE), contains(waiting.deliveryCode()));
        verify(senderService).trySendMessage(eq(CUSTOMER_PHONE), contains("доставлен"));

        assertThat(statusHistory(offer.id()))
                .containsExactly("SEARCHING_COURIER", "ASSIGNED", "PICKED_UP", "DELIVERED");
    }

    @Test
    void pickupChosenByCustomerNeverReachesCouriersEvenWithAddress() {
        Long pickupOrder = placeOrder(customer, "Dorm 5, room 12", FulfillmentType.PICKUP);
        Long deliveryOrder = placeOrder(customer, "Dorm 5, room 12", FulfillmentType.DELIVERY);
        acceptByVenue(pickupOrder);
        acceptByVenue(deliveryOrder);

        assertThat(deliveryRepository.findByOrderId(pickupOrder)).isEmpty();
        authenticate(courier);
        assertThat(courierService.getAvailable())
                .extracting(DeliveryResponses.Available::orderId)
                .containsExactly(deliveryOrder);
    }

    @Test
    void pickupOrderHasNoDeliveryAndVenueCompletesItItself() {
        Long orderId = placeOrder(customer, null);
        acceptByVenue(orderId);

        assertThat(deliveryRepository.findByOrderId(orderId)).isEmpty();
        orderService.updateStatus(orderId);
        orderService.updateStatus(orderId);
        assertThat(orderRepository.findByIdOrThrow(orderId).getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    // ---------------------------------------------------------------- правила для курьера

    @Test
    void courierRules() {
        Long deliveryId = openDelivery(placeDeliveryOrder(customer));

        authenticate(customer);
        assertThatThrownBy(() -> courierService.accept(deliveryId))
                .isInstanceOf(BusinessLogicException.class)
                .hasMessageContaining("собственный");

        authenticate(owner);
        assertThatThrownBy(() -> courierService.accept(deliveryId))
                .as("курьеры — только студенты")
                .isInstanceOf(AccessDeniedException.class);

        authenticate(courier);
        String firstPickupCode = courierService.accept(deliveryId).pickupCode();

        authenticate(courier2);
        assertThatThrownBy(() -> courierService.accept(deliveryId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("другой курьер");
        assertThatThrownBy(() -> courierService.release(deliveryId))
                .isInstanceOf(BusinessLogicException.class)
                .hasMessageContaining("не закреплена");

        // Отказ возвращает заказ в ленту, старый код курьера больше не действует
        authenticate(courier);
        assertThat(courierService.release(deliveryId).status()).isEqualTo(DeliveryStatus.SEARCHING_COURIER);
        authenticate(courier2);
        assertThat(courierService.getAvailable()).extracting(DeliveryResponses.Available::id).containsExactly(deliveryId);
        String secondPickupCode = courierService.accept(deliveryId).pickupCode();

        Delivery stored = deliveryRepository.findByIdOrThrow(deliveryId);
        assertThat(stored.getCourier().getId()).isEqualTo(courier2.getId());
        assertThat(stored.getPickupCode()).as("в БД код зашифрован")
                .startsWith("v1:").doesNotContain(secondPickupCode);
        if (!firstPickupCode.equals(secondPickupCode)) {
            authenticate(owner);
            orderService.updateStatus(stored.getOrder().getId()); // READY
            assertThatThrownBy(() -> venueDeliveryService.confirmPickup(deliveryId, firstPickupCode))
                    .isInstanceOf(InvalidHandoverCodeException.class);
        }
    }

    @Test
    void courierCannotTakeMoreThanTheLimit() {
        List<Long> deliveries = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            deliveries.add(openDelivery(placeDeliveryOrder(customer)));
        }

        authenticate(courier);
        courierService.accept(deliveries.get(0));
        courierService.accept(deliveries.get(1));
        assertThatThrownBy(() -> courierService.accept(deliveries.get(2)))
                .isInstanceOf(BusinessLogicException.class)
                .hasMessageContaining("не больше 2");
        assertThat(courierService.getSummary().activeCount()).isEqualTo(2);
    }

    @Test
    void twoCouriersRacingForOneOrderOnlyOneWins() throws Exception {
        Long deliveryId = openDelivery(placeDeliveryOrder(customer));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (User racer : List.of(courier, courier2)) {
                Callable<String> attempt = () -> {
                    authenticate(racer);
                    start.await();
                    try {
                        courierService.accept(deliveryId);
                        return "won";
                    } catch (ConflictException e) {
                        return "conflict";
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                };
                results.add(pool.submit(attempt));
            }
            start.countDown();

            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(outcomes).containsExactlyInAnyOrder("won", "conflict");
        } finally {
            pool.shutdownNow();
        }
        assertThat(deliveryRepository.findByIdOrThrow(deliveryId).getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
    }

    // ---------------------------------------------------------------- коды и их блокировка

    @Test
    void wrongCustomerCodeIsLockedAfterFiveAttemptsAndAdminIssuesANewOne() {
        Long orderId = placeDeliveryOrder(customer);
        Long deliveryId = pickedUpBy(courier, orderId);
        String code = customerCode();
        String wrong = code.equals("0000") ? "1111" : "0000";

        authenticate(courier);
        for (int left = 4; left >= 1; left--) {
            assertThatThrownBy(() -> courierService.confirmDelivered(deliveryId, wrong))
                    .isInstanceOf(InvalidHandoverCodeException.class)
                    .hasMessageContaining("Осталось попыток: " + left);
        }
        assertThat(deliveryRepository.findByIdOrThrow(deliveryId).getDeliveryCodeAttempts())
                .as("неверные попытки сохраняются, хотя запрос завершился ошибкой")
                .isEqualTo(4);

        assertThatThrownBy(() -> courierService.confirmDelivered(deliveryId, wrong))
                .isInstanceOf(InvalidHandoverCodeException.class)
                .hasMessageContaining("заблокирован");
        assertThatThrownBy(() -> courierService.confirmDelivered(deliveryId, code))
                .as("после блокировки не принимается даже верный код")
                .isInstanceOf(InvalidHandoverCodeException.class);
        assertThat(single(courierService.getMyDeliveries()).deliveryCodeLocked()).isTrue();

        authenticate(admin);
        DeliveryResponses.Issue issue = single(adminService.getIssues(DeliveryIssueStatus.OPEN));
        assertThat(issue.type()).isEqualTo(DeliveryIssueType.CODE_LOCKED);
        assertThat(issue.reportedBy()).isEqualTo(DeliveryParticipant.SYSTEM);
        assertThat(single(adminService.getDeliveries(null)).hasOpenIssue()).isTrue();

        DeliveryResponses.Admin unlocked = adminService.unlockCodes(deliveryId, "Клиент подтвердил по телефону");
        assertThat(unlocked.codeLocked()).isFalse();
        assertThat(unlocked.adminNote()).contains("Клиент подтвердил по телефону");
        adminService.resolveIssue(issue.id(), "Код перевыпущен");

        String newCode = customerCode();
        authenticate(courier);
        courierService.confirmDelivered(deliveryId, newCode);
        assertThat(orderRepository.findByIdOrThrow(orderId).getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void onlyOwnerOfTheVenueConfirmsPickup() {
        Long orderId = placeDeliveryOrder(customer);
        Long deliveryId = openDelivery(orderId);
        authenticate(courier);
        String pickupCode = courierService.accept(deliveryId).pickupCode();
        authenticate(owner);
        orderService.updateStatus(orderId); // READY

        authenticate(createUser("77030000005", UserRole.VENUE_OWNER));
        assertThatThrownBy(() -> venueDeliveryService.confirmPickup(deliveryId, pickupCode))
                .isInstanceOf(AccessDeniedException.class);

        authenticate(owner);
        assertThat(venueDeliveryService.confirmPickup(deliveryId, pickupCode).status())
                .isEqualTo(DeliveryStatus.PICKED_UP);
    }

    // ---------------------------------------------------------------- администратор и проблемы

    @Test
    void adminReassignsCancelsAndCompletesDeliveries() {
        // Снять курьера: заказ снова в ленте, бывший курьер больше не может им управлять
        Long first = openDelivery(placeDeliveryOrder(customer));
        authenticate(courier);
        courierService.accept(first);
        authenticate(admin);
        DeliveryResponses.Admin reassigned = adminService.reassign(first, "Курьер не отвечает");
        assertThat(reassigned.status()).isEqualTo(DeliveryStatus.SEARCHING_COURIER);
        assertThat(reassigned.courierName()).isNull();
        assertThat(reassigned.adminNote()).contains("Курьер не отвечает");
        authenticate(courier);
        assertThatThrownBy(() -> courierService.release(first)).isInstanceOf(BusinessLogicException.class);

        // Отменить доставку: заведение снова может выдать заказ само
        Long secondOrder = placeDeliveryOrder(customer);
        Long second = openDelivery(secondOrder);
        authenticate(owner);
        orderService.updateStatus(secondOrder); // READY
        authenticate(admin);
        assertThat(adminService.cancel(second, "Клиент заберёт сам").status()).isEqualTo(DeliveryStatus.CANCELLED);
        authenticate(owner);
        orderService.updateStatus(secondOrder);
        assertThat(orderRepository.findByIdOrThrow(secondOrder).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(senderService).trySendMessage(eq(CUSTOMER_PHONE), contains("Клиент заберёт сам"));

        // Завершить без кода: передача записывается отдельным шагом, курьер получает оплату
        Long thirdOrder = placeDeliveryOrder(customer);
        Long third = openDelivery(thirdOrder);
        authenticate(courier2);
        courierService.accept(third);
        authenticate(owner);
        orderService.updateStatus(thirdOrder); // READY
        authenticate(admin);
        assertThat(adminService.complete(third, "Клиент подтвердил получение").status())
                .isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(orderRepository.findByIdOrThrow(thirdOrder).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(statusHistory(third)).containsExactly("SEARCHING_COURIER", "ASSIGNED", "PICKED_UP", "DELIVERED");
        authenticate(courier2);
        assertThat(courierService.getSummary().totalEarned()).isEqualByComparingTo("100");

        assertThatThrownBy(() -> adminService.getDeliveries(null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void participantsReportIssuesAndAdminResolvesThem() {
        Long orderId = placeDeliveryOrder(customer);
        Long deliveryId = openDelivery(orderId);

        authenticate(customer);
        DeliveryResponses.Issue issue = issueService.report(deliveryId,
                new DeliveryIssueRequest(DeliveryIssueType.NOT_DELIVERED, "  Жду уже час  "));
        assertThat(issue.reportedBy()).isEqualTo(DeliveryParticipant.CUSTOMER);
        assertThat(issue.description()).isEqualTo("Жду уже час");

        authenticate(courier);
        assertThatThrownBy(() -> issueService.report(deliveryId,
                new DeliveryIssueRequest(DeliveryIssueType.OTHER, null)))
                .as("курьер, который не брал заказ, не участник")
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> issueService.report(deliveryId,
                new DeliveryIssueRequest(DeliveryIssueType.CODE_LOCKED, null)))
                .isInstanceOf(BusinessLogicException.class);

        authenticate(owner);
        assertThat(issueService.report(deliveryId,
                new DeliveryIssueRequest(DeliveryIssueType.COURIER_NOT_ARRIVED, null)).reportedBy())
                .isEqualTo(DeliveryParticipant.VENUE);

        authenticate(admin);
        assertThat(adminService.getIssues(DeliveryIssueStatus.OPEN)).hasSize(2);
        DeliveryResponses.Issue resolved = adminService.resolveIssue(issue.id(), "Связались с клиентом");
        assertThat(resolved.status()).isEqualTo(DeliveryIssueStatus.RESOLVED);
        assertThat(resolved.resolvedByName()).isEqualTo(admin.getName());
        assertThatThrownBy(() -> adminService.resolveIssue(issue.id(), "ещё раз"))
                .isInstanceOf(BusinessLogicException.class);
        assertThat(adminService.getIssues(DeliveryIssueStatus.OPEN)).hasSize(1);
    }

    @Test
    void cancelledOrderTakesItsDeliveryOutOfTheFeed() {
        Long orderId = placeDeliveryOrder(customer);
        Long deliveryId = openDelivery(orderId);

        // Сейчас заведение отменяет только PENDING-заказы, у которых доставки ещё нет, поэтому
        // слушатель вызывается напрямую: если отмену разрешат позже, доставка не останется в ленте
        transactionTemplate.executeWithoutResult(tx -> deliveryOrderStatusListener.afterStatusChange(
                Order.builder().id(orderId).status(OrderStatus.CANCELLED).build(), OrderStatus.PREPARING));

        assertThat(deliveryRepository.findByIdOrThrow(deliveryId).getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        authenticate(courier);
        assertThat(courierService.getAvailable()).isEmpty();
    }

    // ---------------------------------------------------------------- HTTP

    @Test
    void httpApiHidesCodesFromVenueAndMapsErrors() throws Exception {
        Long orderId = placeDeliveryOrder(customer);
        Long deliveryId = openDelivery(orderId);
        authenticate(courier);
        courierService.accept(deliveryId);
        SecurityContextHolder.clearContext();

        perform(get("/api/v1/deliveries/venue").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courierName").value(courier.getName()))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Code\""))));

        perform(post("/api/v1/deliveries/" + deliveryId + "/accept").header("Authorization", bearer(courier2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Этот заказ уже взял другой курьер"));

        perform(post("/api/v1/deliveries/" + deliveryId + "/pickup")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"12\"}"))
                .andExpect(status().isBadRequest());

        perform(get("/api/v1/admin/deliveries").header("Authorization", bearer(courier)))
                .andExpect(status().isForbidden());
        perform(get("/api/v1/admin/deliveries").param("status", "ASSIGNED").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courierPhone").value(COURIER_PHONE));
    }

    // ---------------------------------------------------------------- helpers

    private Long placeDeliveryOrder(User who) {
        return placeOrder(who, "Dorm 5, room 12");
    }

    private Long placeOrder(User who, String address) {
        return placeOrder(who, address, address == null ? FulfillmentType.PICKUP : FulfillmentType.DELIVERY);
    }

    private Long placeOrder(User who, String address, FulfillmentType fulfillment) {
        authenticate(who);
        return orderService.createOrder(new OrderRequest(
                venue.id(),
                List.of(new OrderItemRequest(product.id(), 2, List.of())),
                address,
                "Позвонить у входа",
                null,
                "Aidar A.",
                fulfillment)).id();
    }

    /** Заведение принимает заказ — доставка появляется в ленте. */
    private void acceptByVenue(Long orderId) {
        authenticate(owner);
        orderService.updateStatus(orderId);
    }

    private Long openDelivery(Long orderId) {
        acceptByVenue(orderId);
        return deliveryRepository.findByOrderId(orderId).orElseThrow().getId();
    }

    /** Довести доставку до «курьер забрал заказ». */
    private Long pickedUpBy(User who, Long orderId) {
        Long deliveryId = openDelivery(orderId);
        authenticate(who);
        String pickupCode = courierService.accept(deliveryId).pickupCode();
        authenticate(owner);
        orderService.updateStatus(orderId); // READY
        venueDeliveryService.confirmPickup(deliveryId, pickupCode);
        return deliveryId;
    }

    private String customerCode() {
        authenticate(customer);
        return single(customerService.getMyOrderDeliveries()).deliveryCode();
    }

    private List<String> statusHistory(Long deliveryId) {
        return jdbcTemplate.queryForList(
                "SELECT new_status FROM delivery_status_history WHERE delivery_id = ? ORDER BY id", String.class, deliveryId);
    }

    /**
     * Запрос «как будто новый»: MockMvc с spring-security-test переносит контекст безопасности
     * предыдущего запроса в тестовом потоке, и JWT-фильтр тогда не подставит нового пользователя.
     */
    private org.springframework.test.web.servlet.ResultActions perform(
            org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        TestSecurityContextHolder.clearContext();
        return mockMvc.perform(request);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    private static <T> T single(List<T> values) {
        assertThat(values).hasSize(1);
        return values.get(0);
    }

    private static void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
