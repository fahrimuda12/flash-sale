# FlashSale Engine 🚀

An event-driven, high-concurrency microservices platform engineered to handle flash sale traffic spikes (gadget launches, payday promos, 12.12 e-commerce campaigns) without database deadlocks, race conditions, or inventory overselling.

---

## 🏛️ System Architecture & Data Flow

```text
                                        [ Client / API Gateway ]
                                                   │
                        ┌──────────────────────────┴──────────────────────────┐
                        │ 1. GET /api/products/{id}                           │ 2. POST /api/orders/checkout
                        ▼                                                     ▼
             ┌─────────────────────┐                               ┌─────────────────────┐
             │   Product Service   │                               │    Order Service    │
             │     (Port 8081)     │                               │     (Port 8082)     │
             │ - Cache-Aside Read  │                               │ - Spring IoC Policy │
             │ - Atomic Decrement  │                               │ - Java Stream Cart  │
             └──────────┬──────────┘                               └──────────┬──────────┘
                        │                                                     │
        ┌───────────────┴──────────────┐                      ┌───────────────┴──────────────┐
        │                              │                      │ 3. Atomic Decrement          │ 4. Publish Event
        │                              ▼                      ▼                              ▼
        │                    ┌───────────────────┐  ┌───────────────────┐  ┌───────────────────┐
        │                    │    PostgreSQL     │  │  Redis Data Grid  │  │   Apache Kafka    │
        │                    │   (Port 5432)     │  │   (Port 6379)     │  │   (Port 9092)     │
        │                    │ - Native SQL DDL  │  │ - Atomic Counter  │  │ Topic:            │
        │                    │ - Atomic Decrement│  │ - Cache-Aside TTL │  │ flashsale-order-  │
        │                    └───────────────────┘  └───────────────────┘  │ events            │
        │                                                                  └─────────┬─────────┘
        │                                                                            │
        │ 5. Consume OrderPlacedEvent to Deduct Stock                                │ 6. Consume Event
        └────────────────────────────────────────────────────────────────────────────┤
                                                                                     ▼
                                                                           ┌───────────────────┐
                                                                           │   Audit Service   │
                                                                           │    (Port 8083)    │
                                                                           │ - Java Stream Log │
                                                                           │ - SLA Telemetry   │
                                                                           └─────────┬─────────┘
                                                                                     │
                                           ┌─────────────────────────────────────────┴─────────────────────────────────────────┐
                                           ▼                                                                                   ▼
                                ┌─────────────────────┐                                                             ┌─────────────────────┐
                                │    Elasticsearch    │                                                             │       MongoDB       │
                                │     (Port 9200)     │                                                             │    (Port 27017)     │
                                │ - Full-Text Search  │                                                             │ - Flexible NoSQL    │
                                │ - Fast Indexing     │                                                             │ - Device Audit Log  │
                                └─────────────────────┘                                                             └─────────────────────┘
```

---

## 🌟 Technical Highlights & Core Competencies

| Competency | Implementation Details |
| :--- | :--- |
| **Spring IoC** | Constructor-based dependency injection (`@RequiredArgsConstructor`), Bean lifecycle & configuration management, and Strategy Pattern (`DiscountStrategy` with `FixedAmountDiscountStrategy`, `PercentageDiscountStrategy`, `TieredVipDiscountStrategy`). |
| **Java Stream API** | Cart aggregation, subtotal computing, item grouping & quantity summing for stock decrements (`groupingBy` & `summingInt`), and multi-dimensional analytics calculations in Audit Service (`partitioningBy`, `LongSummaryStatistics`). |
| **Advance Native SQL** | Complex PostgreSQL queries utilizing Window functions (`DENSE_RANK() OVER`, `SUM() OVER (ORDER BY ... ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)`), Common Table Expressions (`WITH ProductSalesRank AS (...)`), and atomic conditional decrements (`UPDATE products SET stock = stock - :qty WHERE id = :id AND stock >= :qty`). |
| **Containerization & Microservices** | Multi-service architecture (`product`, `order`, `audit`) orchestrated with Docker Compose alongside PostgreSQL (5432 hosting separate databases `product` & `order`), Redis (6379), Kafka KRaft (9092), Kafka UI (8085), Elasticsearch (9200), and MongoDB (27017). |
| **Kafka & Stream Processing** | Asynchronous event-driven communication via Kafka topics (`flashsale-order-events`, `flashsale.audit.events`), high-throughput concurrent listener containers, and resilient error handlers with retry backoff. |
| **Redis & Caching Strategy** | Real-time in-memory inventory counters, atomic `decrement()` operations, Read-Through Cache-Aside pattern (`mapAndSyncWithRedis`), and cache eviction on inventory updates. |
| **Elasticsearch & NoSQL (MongoDB)** | Elasticsearch cluster for full-text search and faceted category queries; MongoDB document store for flexible, schema-less telemetry and audit logging. |

---

## 📦 Infrastructure (Docker Compose)

All external infrastructure services are managed via `docker-compose.yaml`:

| Container | Image | Port (Host:Container) | Purpose |
| :--- | :--- | :--- | :--- |
| **flashsale-postgres** | `postgres:16-alpine` | `5432:5432` | Single RDBMS instance hosting both `product` and `order` databases |
| **flashsale-redis** | `redis:7-alpine` | `6379:6379` | In-memory stock counter & Cache-Aside store |
| **flashsale-kafka** | `apache/kafka:3.7.0` | `9092:9092` | KRaft-mode event broker (no Zookeeper needed) |
| **flashsale-kafka-ui** | `provectuslabs/kafka-ui` | `8085:8080` | Web UI at `http://localhost:8085` |
| **flashsale-elasticsearch** | `elasticsearch:8.11.3` | `9200:9200` | Full-text search engine for orders & products |
| **flashsale-mongodb** | `mongo:7.0` | `27017:27017` | NoSQL document store for raw telemetry & audit logs |

---

## 🚀 Step-by-Step Guide to Run the Services

### 1. Prerequisites
- **Java 21 or Java 25** (OpenJDK / Eclipse Temurin)
- **Docker Desktop** (with Docker Compose v2)
- **Maven** or IDE with Maven support (IntelliJ IDEA recommended)

---

### 2. Start Infrastructure Containers
In the root directory, launch all infrastructure services:

```bash
docker compose up -d
```

Verify that all containers are healthy:
```bash
docker compose ps
```

---

### 3. Run the Microservices

You can run each service directly from your IDE by launching their main classes, or run them from the terminal:

#### Service 1: Product Service (Port 8081)
- **Main Class:** `com.fahri.product.ProductApplication`
- **Port:** `8081`
- **Database:** PostgreSQL (`localhost:5432/product`), Redis (`localhost:6379`), Kafka (`localhost:9092`)
```bash
# Run locally with development profile (default):
cd product
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# or simply:
./mvnw spring-boot:run

# Or run with production profile:
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

##### 🐳 Deploy Product Service via Docker Compose Profiles:
Deploy `product-service` as an isolated container alongside required dependencies (PostgreSQL, Redis, Kafka):
```bash
# Deploy product service container using product profile:
docker compose --profile product up -d --build

# View logs:
docker compose logs -f product-service
```

#### Service 2: Order Service (Port 8082)
- **Main Class:** `com.fahri.order.OrderApplication`
- **Port:** `8082`
- **Database:** PostgreSQL (`localhost:5432/order`), Redis (`localhost:6379`), Kafka (`localhost:9092`), Elasticsearch (`localhost:9200`), MongoDB (`localhost:27017`)
```bash
# Run locally with development profile (default):
cd order
./mvnw spring-boot:run

# Or run with production profile:
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

##### 🐳 Deploy Order Service via Docker Compose Profiles:
```bash
# Deploy order service container:
docker compose --profile order up -d --build
```

#### Service 3: Audit Service (Port 8083)
- **Main Class:** `com.fahri.audit.AuditApplication`
- **Port:** `8083`
- **Database:** MongoDB (`localhost:27017/audit`), Kafka (`localhost:9092`)
```bash
# Run locally with development profile (default):
cd audit
./mvnw spring-boot:run

# Or run with production profile:
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

##### 🐳 Deploy Audit Service via Docker Compose Profiles:
```bash
# Deploy audit service container:
docker compose --profile audit up -d --build
```

##### 🚀 Deploy All Services Together:
```bash
docker compose --profile dev up -d --build
# or:
docker compose --profile deploy up -d --build
```

---

## 🧪 End-to-End Testing & Verification

### Step 1: Create a Product (Product Service)
```bash
curl -X POST http://localhost:8081/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Rog Alloy Gaming Pad",
    "description": "Next-gen gaming console",
    "price": 10899000.00,
    "stock": 20,
    "category": "Gaming"
  }'
```
*Response: Returns the created product with `id: 1` and `stock: 20`.*

---

### Step 2: Query Product Detail (Cache-Aside Pattern)
```bash
curl -X GET http://localhost:8081/api/products/1
```
*Note: This automatically warms up the Redis cache key `product:stock:1` with value `20` on read-through.*

---

### Step 3: Checkout an Order (Order Service)
Execute checkout with multiple items and apply a voucher code (client only needs to send `productId` and `quantity`; product name, category, and price are securely enriched from `product-service`):

```bash
curl -X POST http://localhost:8082/api/orders/checkout \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 101,
    "customerEmail": "customer1@gmail.com",
    "voucherCode": "FLASHSALE10",
    "paymentMethod": "VIRTUAL_ACCOUNT",
    "items": [
      {
        "productId": 1,
        "quantity": 2
      }
    ]
  }'
```

**What happens behind the scenes:**
1. **Catalog Auto-Enrichment:** `order-service` securely resolves real-time catalog price, name, and category from `product-service` to prevent client-side price tampering.
2. **Spring IoC Strategy:** Evaluates voucher `FLASHSALE10` using `PercentageDiscountStrategy` (10% off).
3. **PostgreSQL Write:** Saves the order and line items to PostgreSQL (`localhost:5432/order`).
4. **Kafka Publish:** Emits `OrderPlacedEvent` to Kafka topic `flashsale-order-events`.
5. **Product Service Consume:** `ProductService`'s `OrderEventConsumer` consumes the message and executes the native SQL atomic update:
   ```sql
   UPDATE products SET stock = stock - 2 WHERE id = 1 AND stock >= 2;
   ```
6. **Redis In-Memory Decrement:** Atomically decrements the Redis counter `product:stock:1` to `18`.
7. **Fan-Out Sync:** `OrderEventConsumer` indexes the document into Elasticsearch (`localhost:9200`) and records telemetry in MongoDB (`localhost:27017`).

---

### Step 4: Verify Product Stock Decrement
```bash
curl -X GET http://localhost:8081/api/products/1
```
*Response shows `stock: 18`.*

---

### Step 5: Test Advanced Native SQL Analytics (Order Service)

Execute and verify the advanced analytical aggregations computed in PostgreSQL:

#### 1. Top Spending Customers (`DENSE_RANK() OVER`)
Ranks high-value customers by cumulative order expenditure without sequence gaps:
```bash
curl -X GET "http://localhost:8082/api/orders/analytics/top-customers?limit=5"
````

#### 2. Hourly Sales Trend (`Cumulative Window SUM() OVER`)
Calculates order count, bucket sales, and continuous running total per hourly time window:
```bash
curl -X GET "http://localhost:8082/api/orders/analytics/hourly-trend"
```
---

### Step 6: Test Audit & Activity Logs (Audit Service)
Query telemetry logs and real-time streaming analytics computed across `product-service` and `order-service`:

#### 1. Query All Audit Logs (Paginated & Sorted)
```bash
curl -X GET "http://localhost:8083/api/audit/logs?page=0&size=10&sortBy=timestamp&sortDirection=desc"
```

#### 2. Query Activities Filtered by Service & Status
```bash
# Product Service Activities (PRODUCT_CREATED, STOCK_DEDUCTED, INSUFFICIENT_STOCK)
curl -X GET "http://localhost:8083/api/audit/logs?serviceName=product-service"

# Order Service Activities (ORDER_PLACED, ORDER_FAILED)
curl -X GET "http://localhost:8083/api/audit/logs?serviceName=order-service&status=SUCCESS"

# Slow Operations Exceeding Latency SLA (e.g. >= 200ms)
curl -X GET "http://localhost:8083/api/audit/logs?minExecutionTimeMs=200"
```

#### 3. Query Audit Log by ID
```bash
curl -X GET http://localhost:8083/api/audit/logs/{id}
```

#### 4. Trace Distributed Transaction Timeline by Trace ID
Tracks the complete cross-service lifecycle for an order:
```bash
curl -X GET http://localhost:8083/api/audit/logs/trace/{traceId}
```

#### 5. Analytics Summary & Latency Metrics (Java Stream API)
```bash
# Real-time telemetry summary (partitioning, grouping, success rates, slow operations)
curl -X GET http://localhost:8083/api/audit/analytics/summary

# Average execution latency per microservice
curl -X GET http://localhost:8083/api/audit/analytics/latency

# High-priority security and operational failure alerts
curl -X GET http://localhost:8083/api/audit/analytics/alerts
```

---

## 📋 Endpoint Reference

### 1. Product Endpoints

The **Product Service** manages the master product catalog, real-time inventory tracking, Read-Through Cache-Aside synchronization with Redis, full-text search indexing via Elasticsearch, and asynchronous inventory deductions consumed from Kafka order events.

**Base URL:** `http://localhost:8081/api/products`

| Method | Endpoint | Description | Request Type | Response Type |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/products` | Retrieve paginated products with dynamic sorting | Query Params (`page`, `size`, `sortBy`, `sortDirection`) | `Page<ProductResponse>` (HTTP 200) |
| `GET` | `/api/products/all` | Retrieve all products in catalog as a flat list | None | `List<ProductResponse>` (HTTP 200) |
| `GET` | `/api/products/{id}` | Get product detail by ID (warms up Redis `product:stock:{id}` on read-through) | Path Variable (`id`) | `ProductResponse` (HTTP 200) |
| `POST` | `/api/products` | Create a new flash sale product and index to Elasticsearch | JSON Body (`CreateProductRequest`) | `ProductResponse` (HTTP 201) |
| `GET` | `/api/products/search` | Full-text & faceted search across name, category, and price range | Query Params (`keyword`, `category`, `minPrice`, `maxPrice`, `page`, `size`, `sortBy`, `sortDirection`) | `Page<ProductSearchDocument>` (HTTP 200) |
| `POST` | `/api/products/search/sync` | Re-index and synchronize all PostgreSQL products into Elasticsearch | None | `Map<String, Object>` (HTTP 200) |
| `POST` | `/api/products/seed` | Dynamically seed products (1–500) into PostgreSQL and warm Redis stock cache | Query Params (`count`, `force`) | `Map<String, Object>` (HTTP 200) |
| `POST` | `/api/products/seed/performance` | Dedicated alias endpoint for performance testing dynamic product seeder | Query Params (`count`, `force`) | `Map<String, Object>` (HTTP 200) |

---

### 2. Order Endpoints

The **Order Service** coordinates high-concurrency flash sale checkouts, catalog price auto-enrichment from Product Service, Spring IoC discount strategies (`DiscountStrategyProvider`), atomic inventory pre-decrement in Redis, order ledger persistence in PostgreSQL, Kafka event publishing (`flashsale-order-events`), and advanced native SQL analytical aggregations.

**Base URL:** `http://localhost:8082/api/orders`

| Method | Endpoint | Description | Request Type | Response Type |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/orders/checkout` | Process flash sale order checkout with voucher validation & stock verification | JSON Body (`CheckoutRequest`) | `OrderResponse` (HTTP 201) |
| `GET` | `/api/orders` | Retrieve list of all orders | None | `List<OrderResponse>` (HTTP 200) |
| `GET` | `/api/orders/{id}` | Retrieve order details by numeric ID | Path Variable (`id`) | `OrderResponse` (HTTP 200) |
| `GET` | `/api/orders/number/{orderNumber}` | Retrieve order details by unique order number | Path Variable (`orderNumber`) | `OrderResponse` (HTTP 200) |
| `GET` | `/api/orders/analytics/top-customers` | Top spending customers ranked via native SQL `DENSE_RANK() OVER` | Query Param (`limit`, default: 5) | `List<TopCustomerResponse>` (HTTP 200) |
| `GET` | `/api/orders/analytics/hourly-trend` | Hourly cumulative sales trend calculated via window `SUM(...) OVER` | None (alias: `/hourly-trends`) | `List<HourlySalesTrendResponse>` (HTTP 200) |
| `GET` | `/api/orders/search` | Full-text & multi-field search across order number, customer email, product names, and status | Query Params (`keyword`, `customerEmail`, `status`, `category`, `page`, `size`, `sortBy`, `sortDirection`) | `Page<OrderSearchDocument>` (HTTP 200) |
| `GET` | `/api/orders/search/{orderNumber}` | Retrieve indexed order search document by order number from Elasticsearch | Path Variable (`orderNumber`) | `OrderSearchDocument` (HTTP 200) |
| `POST` | `/api/orders/search/sync` | Re-index and synchronize all PostgreSQL orders into Elasticsearch `orders_search` | None | `Map<String, Object>` (HTTP 200) |
| `POST` | `/api/orders/seed` | Dynamically seed flash sale orders (1–500) into PostgreSQL and Elasticsearch | Query Params (`count`, `force`) | `Map<String, Object>` (HTTP 200) |
| `POST` | `/api/orders/seed/performance` | Dedicated alias endpoint for performance testing dynamic order seeder | Query Params (`count`, `force`) | `Map<String, Object>` (HTTP 200) |

---

### 3. Audit Endpoints

The **Audit Service** provides centralized telemetry, distributed tracing, SLA monitoring, dynamic test data seeding, and real-time streaming analytics backed by **MongoDB** and **Apache Kafka**.

**Base URL:** `http://localhost:8083/api/audit`

| Method | Endpoint | Description | Request Type | Response Type |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/audit/events` | Ingest and persist an audit/telemetry event into MongoDB | JSON Body (`AuditEventDto`) | `AuditResponse` (HTTP 201) |
| `GET` | `/api/audit/logs` | Search and paginate audit logs with multi-field filters | Query Params (`serviceName`, `eventType`, `status`, `userId`, `traceId`, `fromDate`, `toDate`, `minExecutionTimeMs`, `page`, `size`, `sortBy`, `sortDirection`) | `Page<AuditResponse>` (HTTP 200) |
| `GET` | `/api/audit/logs/{id}` | Retrieve a specific audit log document by MongoDB ID | Path Variable (`id`) | `AuditResponse` (HTTP 200) |
| `GET` | `/api/audit/logs/trace/{traceId}` | Chronological distributed trace timeline across all services | Path Variable (`traceId`) | `List<AuditResponse>` (HTTP 200) |
| `GET` | `/api/audit/analytics/summary` | Real-time aggregate analytics calculated via Java Stream API | Query Params (`fromDate`, `toDate`, optional) | `AuditAnalyticsSummary` (HTTP 200) |
| `GET` | `/api/audit/analytics/latency` | Dynamic average execution latency per microservice | None | `Map<String, Double>` (HTTP 200) |
| `GET` | `/api/audit/analytics/alerts` | High-priority security and operational failure alerts | None | `List<AuditResponse>` (HTTP 200) |
| `POST` | `/api/audit/seed` | Dynamically seed procedural audit logs (1–500) into MongoDB | Query Params (`count`, `force`) | `Map<String, Object>` (HTTP 200) |

---

## 🚦 Running Unit Tests

Run the test suite in each microservice directory:

```bash
# Product Service Tests
cd product
./mvnw test

# Order Service Tests
cd ../order
./mvnw test

# Audit Service Tests
cd ../audit
./mvnw test
```