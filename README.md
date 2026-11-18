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
| **Kafka & Stream Processing** | Asynchronous event-driven communication via Kafka topics (`flashsale-order-events`, `flashsale.audit.events`), high-throughput concurrent listener containers, and modern non-deprecated `RecordMessageConverter`. |
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
# Deploy product service container using dev or product profile:
docker compose --profile dev up -d --build
# or:
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
````
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
*Response: Returns the created product with `id: 1` and `stock: 50`.*

---

### Step 2: Query Product Detail (Cache-Aside Pattern)
```bash
curl -X GET http://localhost:8081/api/products/1
```
*Note: This automatically warms up the Redis cache key `product:stock:1` with value `50` on read-through.*

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
3. **Kafka Publish:** Emits `OrderPlacedEvent` to Kafka topic `flashsale-order-events`.
4. **Product Service Consume:** `ProductService`'s `OrderEventConsumer` consumes the message and executes the native SQL atomic update:
   ```sql
   UPDATE products SET stock = stock - 2 WHERE id = 1 AND stock >= 2;
   ```
5. **Redis In-Memory Decrement:** Atomically decrements the Redis counter `product:stock:1` to `48`.
6. **Fan-Out Sync:** `OrderEventConsumer` indexes the document into Elasticsearch (`localhost:9200`) and records telemetry in MongoDB (`localhost:27017`).

---

### Step 4: Verify Product Stock Decrement
```bash
curl -X GET http://localhost:8081/api/products/1
```
*Response shows `stock: 48`.*

---

### Step 5: Test Advanced Native SQL Analytics (Order Service)

#### 1. Top Spending Customers (`DENSE_RANK() OVER`)
```bash
curl -X GET http://localhost:8082/api/orders/analytics/top-customers?limit=5
```

#### 2. Hourly Sales Trend (`Cumulative Window SUM() OVER`)
```bash
curl -X GET http://localhost:8082/api/orders/analytics/hourly-trend
```

#### 3. Top Products per Category (Common Table Expression `WITH ... ROW_NUMBER() OVER`)
```bash
curl -X GET http://localhost:8082/api/orders/analytics/top-products-by-category
```

---

### Step 6: Test Audit & Activity Logs (Audit Service)
Query telemetry logs and real-time streaming analytics computed across `product-service` and `order-service`:

#### 1. Query All Audit Logs
```bash
curl -X GET http://localhost:8083/api/audit/logs
```

#### 2. Query Activities Filtered by Service
```bash
# Product Service Activities (PRODUCT_CREATED, STOCK_DEDUCTED, INSUFFICIENT_STOCK)
curl -X GET "http://localhost:8083/api/audit/logs?serviceName=product-service"

# Order Service Activities (ORDER_PLACED, ORDER_FAILED)
curl -X GET "http://localhost:8083/api/audit/logs?serviceName=order-service"
```

#### 3. Trace Timeline by Order / Trace ID
```bash
curl -X GET http://localhost:8083/api/audit/logs/trace/ORD-20261118-0001
```

#### 4. Analytics Summary & Latency (Java Stream API)
```bash
curl -X GET http://localhost:8083/api/audit/analytics/summary
curl -X GET http://localhost:8083/api/audit/analytics/latency
```

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
All unit tests mock external dependencies using Mockito and run in under 5 seconds.
