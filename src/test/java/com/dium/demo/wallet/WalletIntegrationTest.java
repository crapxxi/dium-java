package com.dium.demo.wallet;

import com.dium.demo.dto.requests.OrderItemRequest;
import com.dium.demo.dto.requests.OrderRequest;
import com.dium.demo.dto.requests.ProductRequest;
import com.dium.demo.dto.requests.VenueRequest;
import com.dium.demo.dto.responses.OrderResponse;
import com.dium.demo.dto.responses.ProductResponse;
import com.dium.demo.dto.responses.VenueResponse;
import com.dium.demo.dto.responses.WalletResponses;
import com.dium.demo.enums.FulfillmentType;
import com.dium.demo.enums.OrderStatus;
import com.dium.demo.enums.PaymentMethod;
import com.dium.demo.enums.ProductCategory;
import com.dium.demo.enums.UserRole;
import com.dium.demo.enums.WalletTransactionType;
import com.dium.demo.exceptions.AccessDeniedException;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.models.User;
import com.dium.demo.repositories.DeliveryIssueRepository;
import com.dium.demo.repositories.DeliveryRepository;
import com.dium.demo.repositories.OrderRepository;
import com.dium.demo.repositories.ProductRepository;
import com.dium.demo.repositories.VenueRepository;
import com.dium.demo.repositories.WalletTransactionRepository;
import com.dium.demo.services.auth.JwtService;
import com.dium.demo.services.delivery.AdminDeliveryService;
import com.dium.demo.services.delivery.CourierDeliveryService;
import com.dium.demo.services.delivery.CustomerDeliveryService;
import com.dium.demo.services.delivery.VenueDeliveryService;
import com.dium.demo.services.order.OrderExpirationService;
import com.dium.demo.services.order.OrderService;
import com.dium.demo.services.order.PaymentService;
import com.dium.demo.services.product.ProductService;
import com.dium.demo.services.venue.VenueService;
import com.dium.demo.services.wallet.WalletOperations;
import com.dium.demo.services.wallet.WalletService;
import com.dium.demo.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Кошелёк баллов на PostgreSQL со схемой из миграций: начисление курьеру, оплата заказа
 * с наценкой, возвраты, защита от двойных операций и перерасхода, журнал и сверка.
 */
class WalletIntegrationTest extends AbstractIntegrationTest {
    private static final String ADDRESS = "Dorm 5, room 12";

    @Autowired
    private VenueService venueService;
    @Autowired
    private ProductService productService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderExpirationService expirationService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private CourierDeliveryService courierService;
    @Autowired
    private CustomerDeliveryService customerService;
    @Autowired
    private VenueDeliveryService venueDeliveryService;
    @Autowired
    private AdminDeliveryService adminDeliveryService;
    @Autowired
    private WalletService walletService;
    @Autowired
    private WalletOperations walletOperations;
    @Autowired
    private WalletTransactionRepository transactionRepository;
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
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JwtService jwtService;

    private User owner;
    private User customer;
    private User courier;
    private User admin;
    private User admin2;
    private VenueResponse venue;
    private ProductResponse product;

    @BeforeEach
    void setUp() throws Exception {
        owner = createUser("77040000001", UserRole.VENUE_OWNER);
        customer = createUser("77040000002", UserRole.CLIENT);
        courier = createUser("77040000003", UserRole.CLIENT);
        admin = createUser("77040000009", UserRole.ADMIN);
        admin2 = createUser("77040000008", UserRole.ADMIN);

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

    // ---------------------------------------------------------------- курьер зарабатывает

    @Test
    void courierEarnsPointsOnlyAfterCustomerCodeAndOnlyOnce() {
        Long orderId = placeOrder(PaymentMethod.KASPI, ADDRESS);
        Long deliveryId = pickedUp(orderId);
        assertThat(balance(courier)).as("забрал, но ещё не довёз").isZero();

        authenticate(customer);
        String code = customerService.getMyOrderDeliveries().get(0).deliveryCode();
        authenticate(courier);
        courierService.confirmDelivered(deliveryId, code);

        assertThat(balance(courier)).isEqualTo(100);
        // Повторная попытка начислить за ту же доставку ничего не меняет
        transactionTemplate.executeWithoutResult(tx ->
                walletOperations.payCourier(deliveryRepository.findByIdOrThrow(deliveryId)));
        assertThat(balance(courier)).isEqualTo(100);
        assertThat(transactions(courier)).extracting(WalletResponses.Transaction::type)
                .containsExactly(WalletTransactionType.DELIVERY_EARNING);
        assertLedgerMatchesBalance(courier);
    }

    @Test
    void adminCompletingDeliveryAlsoPaysCourier() {
        Long deliveryId = pickedUp(placeOrder(PaymentMethod.KASPI, ADDRESS));
        authenticate(admin);
        adminDeliveryService.complete(deliveryId, "Клиент подтвердил по телефону");
        assertThat(balance(courier)).isEqualTo(100);
    }

    // ---------------------------------------------------------------- оплата баллами

    @Test
    void payingWithPointsCostsTwentyPercentMoreAndNeedsNoKaspi() {
        give(customer, 3000);

        OrderResponse order = createOrder(PaymentMethod.POINTS, ADDRESS, null);

        // 2 × 1000 ₸ = 2000 ₸ → 2400 баллов, доставка 100 ₸ → 120 баллов
        assertThat(order.totalSum()).isEqualByComparingTo("2100");
        assertThat(order.paymentMethod()).isEqualTo(PaymentMethod.POINTS);
        assertThat(order.pointsSpent()).isEqualTo(2520);
        assertThat(order.paymentFrom()).isNull();
        assertThat(balance(customer)).isEqualTo(480);
        assertThatThrownBy(() -> paymentService.getPaymentUrl(order.id()))
                .isInstanceOf(BusinessLogicException.class)
                .hasMessageContaining("баллами");
        assertLedgerMatchesBalance(customer);
    }

    @Test
    void notEnoughPointsMeansNoOrder() {
        give(customer, 2519);
        long ordersBefore = orderRepository.count();

        assertThatThrownBy(() -> createOrder(PaymentMethod.POINTS, ADDRESS, null))
                .isInstanceOf(BusinessLogicException.class)
                .hasMessageContaining("Недостаточно баллов");

        assertThat(orderRepository.count()).as("заказ откатился вместе со списанием").isEqualTo(ordersBefore);
        assertThat(balance(customer)).isEqualTo(2519);
    }

    @Test
    void kaspiOrderStillNeedsPayer() {
        assertThatThrownBy(() -> createOrder(PaymentMethod.KASPI, null, null))
                .isInstanceOf(BusinessLogicException.class);
    }

    @Test
    void twoSimultaneousOrdersCannotSpendTheSamePoints() throws Exception {
        give(customer, 2520); // ровно на один заказ

        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            authenticate(customer);
            start.await();
            try {
                createOrder(PaymentMethod.POINTS, ADDRESS, null);
                return true;
            } catch (BusinessLogicException e) {
                return false;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            results.add(pool.submit(attempt));
            results.add(pool.submit(attempt));
            start.countDown();
            int succeeded = 0;
            for (Future<Boolean> result : results) {
                succeeded += result.get(30, TimeUnit.SECONDS) ? 1 : 0;
            }
            assertThat(succeeded).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(balance(customer)).isZero();
        assertLedgerMatchesBalance(customer);
    }

    // ---------------------------------------------------------------- возвраты

    @Test
    void venueCancellingPointsOrderRefundsEverything() {
        give(customer, 3000);
        Long orderId = placeOrder(PaymentMethod.POINTS, ADDRESS);

        authenticate(owner);
        orderService.cancelOrder(orderId);

        assertThat(balance(customer)).isEqualTo(3000);
        assertLedgerMatchesBalance(customer);
    }

    @Test
    void expiredPointsOrderRefundsEverything() {
        give(customer, 3000);
        Long orderId = placeOrder(PaymentMethod.POINTS, null);
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(10)), orderId);

        expirationService.cancelExpiredOrders();

        assertThat(orderRepository.findByIdOrThrow(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(balance(customer)).isEqualTo(3000);
        // Повторный прогон планировщика не возвращает второй раз
        expirationService.cancelExpiredOrders();
        assertThat(balance(customer)).isEqualTo(3000);
    }

    @Test
    void cancelledDeliveryReturnsOnlyDeliveryPoints() {
        give(customer, 3000);
        Long orderId = placeOrder(PaymentMethod.POINTS, ADDRESS);
        acceptByVenue(orderId);
        Long deliveryId = deliveryRepository.findByOrderId(orderId).orElseThrow().getId();

        authenticate(admin);
        adminDeliveryService.cancel(deliveryId, "Никто не взял");

        assertThat(balance(customer)).isEqualTo(480 + 120);
        assertLedgerMatchesBalance(customer);
    }

    // ---------------------------------------------------------------- журнал и администратор

    @Test
    void ledgerCannotBeEditedOrDeleted() {
        give(customer, 500);
        Long id = transactionRepository.findTop100ByUserIdOrderByIdDesc(customer.getId()).get(0).getId();

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE wallet_transactions SET amount = 50000 WHERE id = ?", id))
                .hasMessageContaining("только дописывается");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM wallet_transactions WHERE id = ?", id))
                .hasMessageContaining("только дописывается");
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE wallets SET balance = -1 WHERE user_id = ?", customer.getId()))
                .hasMessageContaining("ck_wallets_balance");
    }

    @Test
    void adminAdjustmentRules() {
        authenticate(customer);
        assertThatThrownBy(() -> walletService.adjust(customer.getId(), 1000, "Хочу"))
                .isInstanceOf(AccessDeniedException.class);

        authenticate(admin);
        assertThatThrownBy(() -> walletService.adjust(admin.getId(), 1000, "Себе"))
                .isInstanceOf(BusinessLogicException.class)
                .hasMessageContaining("Свой баланс");

        WalletResponses.AdminWallet wallet = walletService.adjust(customer.getId(), 300, "Компенсация за опоздание");
        assertThat(wallet.balance()).isEqualTo(300);
        assertThat(wallet.ledgerSum()).isEqualTo(300);
        assertThat(wallet.transactions().get(0).note()).contains("Компенсация за опоздание");

        assertThatThrownBy(() -> walletService.adjust(customer.getId(), -301, "Ошибка"))
                .isInstanceOf(BusinessLogicException.class)
                .hasMessageContaining("Недостаточно баллов");
        assertThat(walletService.findByPhone("77040000002").balance()).isEqualTo(300);
    }

    @Test
    void settlementShowsWhoOwesWhom() {
        // Kaspi + доставка: заведение получило 100 ₸ за доставку, курьер — 100 баллов
        Long kaspiOrder = placeOrder(PaymentMethod.KASPI, ADDRESS);
        Long deliveryId = pickedUp(kaspiOrder);
        authenticate(admin);
        adminDeliveryService.complete(deliveryId, "Проверка сверки");

        // Самовывоз за баллы: заведение отдало еды на 2000 ₸ и денег не получило
        give(customer, 3000);
        Long pointsOrder = placeOrder(PaymentMethod.POINTS, null);
        acceptByVenue(pointsOrder);
        orderService.updateStatus(pointsOrder); // READY
        orderService.updateStatus(pointsOrder); // COMPLETED

        authenticate(admin);
        WalletResponses.SettlementReport report = walletService.getSettlements(LocalDate.now(), LocalDate.now());
        assertThat(report.venues()).hasSize(1);
        WalletResponses.Settlement settlement = report.venues().get(0);
        assertThat(settlement.deliveryFeesOwed()).isEqualByComparingTo("100");
        assertThat(settlement.pointsOrdersCount()).isEqualTo(1);
        assertThat(settlement.pointsOrdersValue()).isEqualByComparingTo("2000");
        assertThat(settlement.netToVenue()).isEqualByComparingTo("1900");
    }

    @Test
    void walletApi() throws Exception {
        give(customer, 250);
        TestSecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/v1/wallet").header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(250))
                .andExpect(jsonPath("$.markupPercent").value(20))
                .andExpect(jsonPath("$.pointsPerDelivery").value(100))
                .andExpect(jsonPath("$.transactions[0].type").value("ADMIN_ADJUSTMENT"));

        TestSecurityContextHolder.clearContext();
        mockMvc.perform(post("/api/v1/admin/wallets/" + customer.getId() + "/adjustments")
                        .header("Authorization", bearer(customer))
                        .contentType("application/json").content("{\"amount\":100,\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());

        TestSecurityContextHolder.clearContext();
        mockMvc.perform(post("/api/v1/admin/wallets/" + customer.getId() + "/adjustments")
                        .header("Authorization", bearer(admin))
                        .contentType("application/json").content("{\"amount\":100,\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- помощники

    private OrderResponse createOrder(PaymentMethod method, String address, String paymentFrom) {
        authenticate(customer);
        return orderService.createOrder(new OrderRequest(
                venue.id(),
                List.of(new OrderItemRequest(product.id(), 2, List.of())),
                address,
                null,
                null,
                paymentFrom,
                address == null ? FulfillmentType.PICKUP : FulfillmentType.DELIVERY,
                method));
    }

    private Long placeOrder(PaymentMethod method, String address) {
        return createOrder(method, address, method == PaymentMethod.KASPI ? "Aidar A." : null).id();
    }

    private void acceptByVenue(Long orderId) {
        authenticate(owner);
        orderService.updateStatus(orderId);
    }

    private Long pickedUp(Long orderId) {
        acceptByVenue(orderId);
        Long deliveryId = deliveryRepository.findByOrderId(orderId).orElseThrow().getId();
        authenticate(courier);
        String pickupCode = courierService.accept(deliveryId).pickupCode();
        authenticate(owner);
        orderService.updateStatus(orderId); // READY
        venueDeliveryService.confirmPickup(deliveryId, pickupCode);
        return deliveryId;
    }

    private void give(User user, long points) {
        authenticate(admin2);
        walletService.adjust(user.getId(), points, "Тестовое начисление");
        SecurityContextHolder.clearContext();
    }

    private long balance(User user) {
        authenticate(user);
        return walletService.getMyWallet().balance();
    }

    private List<WalletResponses.Transaction> transactions(User user) {
        authenticate(user);
        return walletService.getMyWallet().transactions();
    }

    private void assertLedgerMatchesBalance(User user) {
        assertThat(transactionRepository.sumByUser(user.getId())).isEqualTo(balance(user));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    private static void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
