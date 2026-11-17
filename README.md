# FlashSale Engine 🚀

During high-traffic flash sale campaigns (such as digital banking payday promos, gadget launches, or e-commerce 12.12 events), thousands of concurrent shoppers attempt to purchase limited-stock products and apply high-value discount vouchers at the exact same second.

Traditional database transactions fail under this load due to row-locking contention, deadlocks, and cascading latency.

---

##  System Architecture & Data Flow

```
                                        [ Client  / Postman ]
                                                │
                                                │ 1. POST /api/orders/checkout
                                                │    (Items + Voucher Code)
                                                ▼
                                   ┌──────────────────────────┐
                                   │       Order Service      │
                                   │  - Java Streams (Cart)   │
                                   │  - Spring IoC (Strategy) │
                                   └─────────┬───────┬────────┘
                                             │       │
              ┌──────────────────────────────┘       └──────────────────────────────┐
              │ 2. Atomic Decrement:                                                │ 3. Publish Event:
              │    - product:stock:{id}                                             │    OrderPlacedEvent
              │    - voucher:stock:{code}                                           │
              ▼                                                                     ▼
    ┌───────────────────┐                                                ┌───────────────────┐
    │  Redis Data Grid  │                                                │   Apache Kafka    │
    │  (Stock & Cache)  │                                                │  (order-events)   │
    └───────────────────┘                                                └─────────┬─────────┘
                                                                                   │
                                                                                   │ 4. Consume Asynchronously
                                                                                   ▼
                                                                     ┌───────────────────────────┐
                                                                     │     Analytics & Audit     │
                                                                     │   (Downstream Consumer)   │
                                                                     └─────────────┬─────────────┘
                                                                                   │
                                     ┌─────────────────────────────────────────────┼─────────────────────────────────────────────┐
                                     │ 5. Relational Ledger                        │ 6. Full-Text Search                         │ 7. Raw Telemetry
                                     ▼                                             ▼                                             ▼
                          ┌─────────────────────┐                       ┌─────────────────────┐                       ┌─────────────────────┐
                          │     PostgreSQL      │                       │    Elasticsearch    │                       │       MongoDB       │
                          │ (Orders + Window Q) │                       │ (Order/Product Docs)│                       │ (Device Audit Logs) │
                          └─────────────────────┘                       └─────────────────────┘                       └─────────────────────┘
```
---

## Infrastructure & Services (Docker Compose)

All infrastructure dependencies are managed via `compose.yaml`:

| Service | Image | Internal Port | Host Port | Credentials / Purpose                                           |
| :--- | :--- | :--- | :--- |:----------------------------------------------------------------|
| **PostgreSQL** | `postgres:16-alpine` | `5432` | `5432` | User: `changeYourUser`, Pass: `changeYourPass`, DB: `flashsale` |
| **Redis** | `redis:7-alpine` | `6379` | `6379` | In-memory product/voucher stock counter & Cache-Aside store     |
| **Apache Kafka** | `apache/kafka:3.7.0` | `9092` | `9092` | KRaft mode event broker (no Zookeeper required)                 |
| **Kafka UI** | `provectuslabs/kafka-ui` | `8080` | `8085` | Web dashboard at `http://localhost:8085`                        |
| **Elasticsearch**| `elasticsearch:8.11.3` | `9200` | `9200` | Full-text search engine for orders & products                   |
| **MongoDB** | `mongo:7.0` | `27017` | `27017` | Unstructured audit telemetry collection (`audit`)               |

---

##  Getting Started

### 1. Start Infrastructure
```powershell
docker compose up -d
```

Verify that all containers are running:
```powershell
docker compose ps
```

### 2. Build & Run Application
```powershell
./mvnw clean install
./mvnw spring-boot:run
```
