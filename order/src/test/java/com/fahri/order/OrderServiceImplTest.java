package com.fahri.order;

import com.fahri.order.dto.CheckoutRequest;
import com.fahri.order.dto.OrderItemRequest;
import com.fahri.order.dto.OrderResponse;
import com.fahri.order.dto.TopCustomerResponse;
import com.fahri.order.dto.HourlySalesTrendResponse;
import com.fahri.order.entity.Order;
import com.fahri.order.entity.OrderStatus;
import com.fahri.order.exception.OrderNotFoundException;
import com.fahri.order.queue.dto.OrderPlacedEvent;
import com.fahri.order.exception.OutOfStockException;
import com.fahri.order.queue.OrderEventProducer;
import com.fahri.order.repository.OrderItemRepository;
import com.fahri.order.repository.OrderRepository;
import com.fahri.order.repository.OrderSearchRepository;
import com.fahri.order.repository.projection.CustomerSpendingProjection;
import com.fahri.order.repository.projection.HourlySalesTrendProjection;
import com.fahri.order.service.StockService;
import com.fahri.order.service.impl.OrderServiceImpl;
import com.fahri.order.strategy.DiscountStrategy;
import com.fahri.order.strategy.DiscountStrategyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Order Service Implementation Unit Tests")
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private StockService stockService;

    @Mock
    private DiscountStrategyProvider discountStrategyProvider;

    @Mock
    private OrderEventProducer orderEventProducer;

    @Mock
    private OrderSearchRepository orderSearchRepository;

    @Mock
    private DiscountStrategy mockDiscountStrategy;

    @Mock
    private com.fahri.order.client.ProductServiceClient productServiceClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private CheckoutRequest validRequest;

    @BeforeEach
    void setUp() {
        OrderItemRequest item1 = OrderItemRequest.builder()
                .productId(1L)
                .productName("Flagship Smartphone Pro 256GB")
                .category("Electronics")
                .price(new BigDecimal("15000000.00"))
                .quantity(1)
                .build();

        OrderItemRequest item2 = OrderItemRequest.builder()
                .productId(2L)
                .productName("Wireless Earbuds")
                .category("Accessories")
                .price(new BigDecimal("2500000.00"))
                .quantity(2)
                .build();

        validRequest = CheckoutRequest.builder()
                .customerId(999L)
                .customerEmail("shopper@example.com")
                .paymentMethod("VIRTUAL_ACCOUNT")
                .voucherCode("FLASHSALE10")
                .idempotencyKey("IDEM-TEST-1234")
                .items(List.of(item1, item2))
                .clientIp("127.0.0.1")
                .userAgent("JUnit Test Agent")
                .build();
    }

    @Test
    @DisplayName("Should successfully checkout with Java Stream calculations, Redis Data Grid reservation, and Kafka event publishing")
    void testSuccessfulCheckout() {
        // Total subtotal: (15M * 1) + (2.5M * 2) = 20M
        BigDecimal expectedSubtotal = new BigDecimal("20000000.00");
        BigDecimal expectedDiscount = new BigDecimal("2000000.00"); // 10%

        when(discountStrategyProvider.resolveStrategy("FLASHSALE10")).thenReturn(mockDiscountStrategy);
        when(mockDiscountStrategy.getStrategyName()).thenReturn("10% Discount");
        when(mockDiscountStrategy.calculateDiscount(expectedSubtotal)).thenReturn(expectedDiscount);
        when(stockService.reserveVoucherQuota("FLASHSALE10")).thenReturn(true);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(100L);
            return order;
        });

        OrderResponse response = orderService.checkout(validRequest);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("shopper@example.com", response.getCustomerEmail());
        assertEquals(expectedSubtotal, response.getTotalAmount());
        assertEquals(expectedDiscount, response.getDiscountAmount());
        assertEquals(new BigDecimal("18000000.00"), response.getFinalAmount());
        assertEquals(OrderStatus.CONFIRMED.name(), response.getStatus());
        assertEquals(2, response.getItems().size());

        // Verify Redis Data Grid atomic stock reservations
        verify(stockService, times(1)).reserveProductStock(1L, 1);
        verify(stockService, times(1)).reserveProductStock(2L, 2);
        verify(stockService, times(1)).acquireIdempotencyLock(eq("IDEM-TEST-1234"), any());

        // Verify Kafka event published
        verify(orderEventProducer, times(1)).publishOrderPlaced(any(OrderPlacedEvent.class));
    }

    @Test
    @DisplayName("Should trigger compensating transaction in Redis when stock reservation fails")
    void testCheckoutOutOfStockRollback() {
        when(discountStrategyProvider.resolveStrategy("FLASHSALE10")).thenReturn(mockDiscountStrategy);
        when(mockDiscountStrategy.getStrategyName()).thenReturn("10% Discount");
        when(stockService.reserveVoucherQuota("FLASHSALE10")).thenReturn(true);

        // First item succeeds, second item throws OutOfStockException
        doNothing().when(stockService).reserveProductStock(1L, 1);
        doThrow(new OutOfStockException("Out of stock")).when(stockService).reserveProductStock(2L, 2);

        assertThrows(OutOfStockException.class, () -> orderService.checkout(validRequest));

        // Verify compensation: first item was rolled back
        verify(stockService, times(1)).releaseProductStock(1L, 1);
        // Verify no order was saved to database
        verify(orderRepository, never()).save(any());
        // Verify no Kafka event was published
        verify(orderEventProducer, never()).publishOrderPlaced(any());
    }

    @Test
    @DisplayName("Should successfully checkout with simple payload containing only productId and quantity")
    void testCheckout_SimplePayload_OnlyProductIdAndQuantity() {
        OrderItemRequest simpleItem = OrderItemRequest.builder()
                .productId(2L)
                .quantity(1)
                .build();

        CheckoutRequest simpleRequest = CheckoutRequest.builder()
                .customerId(101L)
                .customerEmail("customer1@gmail.com")
                .paymentMethod("VIRTUAL_ACCOUNT")
                .items(List.of(simpleItem))
                .build();

        com.fahri.order.client.dto.ProductDto productDto = com.fahri.order.client.dto.ProductDto.builder()
                .id(2L)
                .name("Apple iPhone 15 Pro 128GB - Natural Titanium")
                .category("Smartphone")
                .price(new BigDecimal("18499000.00"))
                .stock(16)
                .build();

        when(productServiceClient.getProductById(2L)).thenReturn(productDto);
        when(discountStrategyProvider.resolveStrategy(null)).thenReturn(new com.fahri.order.strategy.NoDiscountStrategy());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(101L);
            return order;
        });

        OrderResponse response = orderService.checkout(simpleRequest);

        assertNotNull(response);
        assertEquals(101L, response.getId());
        assertEquals("customer1@gmail.com", response.getCustomerEmail());
        assertEquals(new BigDecimal("18499000.00"), response.getTotalAmount());
        assertEquals(new BigDecimal("18499000.00"), response.getFinalAmount());
        assertEquals(1, response.getItems().size());
        assertEquals("Apple iPhone 15 Pro 128GB - Natural Titanium", response.getItems().getFirst().getProductName());

        verify(productServiceClient, times(1)).getProductById(2L);
        verify(stockService, times(1)).reserveProductStock(2L, 1);
        verify(orderEventProducer, times(1)).publishOrderPlaced(any(OrderPlacedEvent.class));
    }

    @Test
    @DisplayName("getOrderById should return OrderResponse when order exists")
    void testGetOrderById_Success() {
        Order order = Order.builder()
                .id(1L)
                .orderNumber("ORD-20261118-0001")
                .customerId(101L)
                .customerEmail("alex.tan@example.com")
                .totalAmount(new BigDecimal("15000000.00"))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("15000000.00"))
                .status(OrderStatus.COMPLETED)
                .paymentMethod("VIRTUAL_ACCOUNT")
                .build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("ORD-20261118-0001", response.getOrderNumber());
        assertEquals("alex.tan@example.com", response.getCustomerEmail());
        verify(orderRepository).findById(1L);
    }

    @Test
    @DisplayName("getOrderById should throw OrderNotFoundException when order does not exist")
    void testGetOrderById_NotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.getOrderById(999L));
        verify(orderRepository).findById(999L);
    }

    @Test
    @DisplayName("getOrderByOrderNumber should return OrderResponse when found")
    void testGetOrderByOrderNumber_Success() {
        Order order = Order.builder()
                .id(2L)
                .orderNumber("ORD-20261118-0002")
                .customerId(102L)
                .customerEmail("sarah.connor@example.com")
                .totalAmount(new BigDecimal("5000000.00"))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("5000000.00"))
                .status(OrderStatus.CONFIRMED)
                .paymentMethod("CREDIT_CARD")
                .build();

        when(orderRepository.findByOrderNumber("ORD-20261118-0002")).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderByOrderNumber("ORD-20261118-0002");

        assertNotNull(response);
        assertEquals(2L, response.getId());
        assertEquals("ORD-20261118-0002", response.getOrderNumber());
        verify(orderRepository).findByOrderNumber("ORD-20261118-0002");
    }

    @Test
    @DisplayName("getAllOrders should return all orders from repository")
    void testGetAllOrders() {
        Order order = Order.builder()
                .id(1L)
                .orderNumber("ORD-20261118-0001")
                .customerId(101L)
                .customerEmail("alex.tan@example.com")
                .totalAmount(new BigDecimal("15000000.00"))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("15000000.00"))
                .status(OrderStatus.COMPLETED)
                .paymentMethod("VIRTUAL_ACCOUNT")
                .build();

        when(orderRepository.findAll()).thenReturn(List.of(order));

        List<OrderResponse> result = orderService.getAllOrders();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ORD-20261118-0001", result.get(0).getOrderNumber());
        verify(orderRepository).findAll();
    }

    @Test
    @DisplayName("getTopSpenders should invoke findTopSpendersNative and map projections")
    void testGetTopSpenders() {
        CustomerSpendingProjection projection = mock(CustomerSpendingProjection.class);
        when(projection.getCustomerId()).thenReturn(101L);
        when(projection.getCustomerEmail()).thenReturn("alex.tan@example.com");
        when(projection.getTotalSpent()).thenReturn(new BigDecimal("25000000.00"));
        when(projection.getOrderCount()).thenReturn(2L);
        when(projection.getCustomerRank()).thenReturn(1);

        when(orderRepository.findTopSpendersNative(5)).thenReturn(List.of(projection));

        List<TopCustomerResponse> result = orderService.getTopSpenders(5);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(101L, result.get(0).getCustomerId());
        assertEquals(1, result.get(0).getCustomerRank());
        verify(orderRepository).findTopSpendersNative(5);
    }


    @Test
    @DisplayName("getHourlySalesTrends should invoke findHourlySalesTrendsNative and map projections")
    void testGetHourlySalesTrends() {
        HourlySalesTrendProjection projection = mock(HourlySalesTrendProjection.class);
        when(projection.getHourBucket()).thenReturn("2026-11-18 10:00");
        when(projection.getOrderCount()).thenReturn(15L);
        when(projection.getHourlySales()).thenReturn(new BigDecimal("50000000.00"));
        when(projection.getCumulativeSales()).thenReturn(new BigDecimal("120000000.00"));

        when(orderRepository.findHourlySalesTrendsNative()).thenReturn(List.of(projection));

        List<HourlySalesTrendResponse> result = orderService.getHourlySalesTrends();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("2026-11-18 10:00", result.get(0).getHourBucket());
        assertEquals(new BigDecimal("120000000.00"), result.get(0).getCumulativeSales());
        verify(orderRepository).findHourlySalesTrendsNative();
    }
}
