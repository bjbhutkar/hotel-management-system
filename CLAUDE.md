# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run in development mode (hot reload via JavaFX Maven plugin)
mvn clean javafx:run

# Build a fat JAR (all dependencies bundled)
mvn clean package -DskipTests

# Run the built JAR
java -jar target/hotel-management-system-1.0.0.jar

# Run tests
mvn test

# Windows convenience launcher (auto-detects dev vs. production JAR)
scripts\run.bat
```

## Architecture

This is a **JavaFX 17 desktop application** (offline, no web server) using Spring Boot 3.2 purely for dependency injection, JPA, and business logic — not as a web server. Compiled at Java 21 source/target level.

**Stack:** Java 21 (compiler), JavaFX 17.0.10 + FXML, Spring Boot 3.2, Spring Data JPA, Hibernate, SQLite, BCrypt, JasperReports 6.20.6, Apache POI 5.2.5, OpenPDF 1.3.30, Jackson, Maven

### Layers

```
JavaFX Controllers (com.hotel.ui)
        ↓
Service Layer (com.hotel.service  +  com.hotel.delivery.service)
        ↓
Spring Data Repositories (com.hotel.repository  +  com.hotel.delivery.repository)
        ↓
JPA Entities (com.hotel.entity  +  com.hotel.delivery.entity)
        ↓
SQLite: ~/hotel_management/hotel_management.db
```

**Entry point:** `com.hotel.Main` → `HotelManagementApp extends Application`. Spring Boot context is initialized in `init()`, JavaFX stage starts in `start(Stage)`. All scene transitions go through `StageManager`.

**Controller injection:** `StageManager.createLoader()` sets `loader.setControllerFactory(appContext::getBean)`, so all JavaFX controllers are Spring `@Component` beans injected by the container — no manual `context.getBean()` calls in controllers.

**App branding:** The application is named "Rasoi" in all window titles and UI.

### Scene / Controller Map

| FXML Scene | Controller | Access |
|---|---|---|
| `login.fxml` | `LoginController` | All users |
| `dashboard.fxml` | `DashboardController` | All users |
| `menu-management.fxml` | `MenuManagementController` | Admin only |
| `order-management.fxml` | `OrderManagementController` | All users |
| `billing.fxml` | `BillingController` | All users (modal) |
| `reports.fxml` | `ReportController` | Admin only |
| `online-orders-dashboard.fxml` | `OnlineOrdersDashboardController` | All users |
| `platform-config.fxml` | `PlatformConfigController` | Admin only |
| `menu-sync.fxml` | `MenuSyncController` | Admin only |
| `settings.fxml` | `SettingsController` | Admin only |
| `import-data.fxml` | `ImportController` | Admin only |

### Key Design Points

- **Spring context lifecycle:** The Spring `ApplicationContext` is stored in `HotelManagementApp` and accessed by `StageManager` to inject beans into controllers via `controllerFactory`.
- **DDL:** `spring.jpa.hibernate.ddl-auto=update` — schema is auto-managed. The file at `src/main/resources/db/schema.sql` is informational only and is not executed automatically.
- **Seeding:** `DataInitializer` (`ApplicationRunner`, in `com.hotel.config`) seeds default users and 12 menu items on every startup if they don't exist. Default credentials: `admin/admin123` (ADMIN), `staff/staff123` (STAFF).
- **Tax:** GST rate is stored per-order on `Order.taxRate` (BigDecimal). The default is 18% from `RestaurantConfig.defaultTaxPercent`, which is configurable via the Settings screen.
- **Roles:** `UserRole` enum — `ADMIN` (full access) and `STAFF` (order/billing only). Role gates are enforced in controllers, not at the service layer.
- **Audit logging:** `AuditLogRepository` / `AuditLog` entity records user actions throughout the app.
- **Reporting:** `ReportService` generates daily/weekly/monthly sales summaries, top/least-selling items, and cancelled order reports. `ReportController` also integrates JasperReports for PDF export.
- **Backup/restore:** `BackupService` copies the SQLite file to a user-chosen directory and restores it from a chosen file.
- **Window state:** `WindowStateManager` (no JavaFX dependency — unit-testable) encapsulates the logic for sizing/positioning the primary stage across scene swaps. Login screen centers at fixed size; all other screens maximize on first open and restore their prior state on subsequent navigation.

### Restaurant Configuration

`RestaurantConfig` is a singleton entity (fixed `id=1`) in the `RESTAURANT_CONFIG` table. It stores:
- Business identity: name, address, phone, email, GST number, FSSAI number, website
- Branding: logo path (local file), footer message, thank-you message
- Invoice settings: invoice prefix, currency symbol (default `₹`)
- Tax: `defaultTaxPercent` (default 18%)

`RestaurantConfigService` provides `getConfig()` / `saveConfig()` with upsert logic.

### Printing

`PrintService` handles customer invoices and Kitchen Order Tickets (KOT) via the JavaFX `PrinterJob` API:
- First print: shows a printer-picker dialog; persists the chosen printer via `java.util.prefs.Preferences`.
- Subsequent prints: sends directly to the remembered printer without a dialog.
- `changePrinter(Window)`: clears the saved choice and re-opens the picker (called from Settings screen).
- Invoices pull branding from `RestaurantConfig` (logo, GST number, FSSAI, thank-you message).
- KOT layout includes per-item `notes` (stored on `OrderItem.notes`).

### Excel Import

`ExcelImportService` reads `.xlsx` files via Apache POI and bulk-imports:

| Import type | FXML screen | Key columns |
|---|---|---|
| Menu items | `import-data.fxml` | Name, Category, Price, Description, Available |
| Order history | `import-data.fxml` | OrderNumber, TableNo, CustomerName, Date, ItemName, Qty, UnitPrice, TaxRate, PaymentMode |

Both importers are `@Transactional`, skip duplicates with warnings, and return an `ImportResult` DTO with per-row error details. The service also generates downloadable sample template `.xlsx` files.

---

## Delivery Platform Integration (`com.hotel.delivery`)

A standalone sub-module for Zomato/Swiggy online order ingestion. All classes live under `com.hotel.delivery.*`.

### Package Layout

```
com.hotel.delivery
├── adapter/          DeliveryPlatformAdapter (interface), ZomatoAdapter, SwiggyAdapter, MockPlatformAdapter
├── config/           DeliveryConfig (@EnableScheduling, platform seeding, ObjectMapper bean)
├── dto/              IncomingOrderDto, IncomingOrderItemDto, OrderStatusUpdateDto, MenuSyncItemDto, PlatformAuthDto
├── entity/           DeliveryPlatform, PlatformCredential, OnlineOrder, OnlineOrderItem,
│                     MenuPlatformMapping, OnlineOrderStatusHistory, WebhookLog, RetryQueueItem
├── enums/            PlatformType (ZOMATO, SWIGGY, MOCK), OnlineOrderStatus, RetryStatus
├── event/            NewOnlineOrderEvent, OrderStatusChangedEvent
├── repository/       (one Spring Data repo per entity)
├── service/          DeliveryIntegrationService, OnlineOrderProcessingService,
│                     MenuSyncService, RetryService, CredentialService
└── webhook/          WebhookServer
```

### Adapter Pattern

`DeliveryPlatformAdapter` is the pluggable interface. Each platform (plus a `MockPlatformAdapter` for testing) implements:
- `testConnection()` — verifies stored credentials
- `pollNewOrders()` — idempotent poll for new orders
- `acceptOrder()` / `rejectOrder()` — acknowledge incoming orders
- `updateOrderStatus()` — push state transitions (PREPARING → READY → PICKED_UP)
- `syncFullMenu()` / `syncMenuItem()` — push menu changes to the platform

`DeliveryIntegrationService` auto-discovers all adapters via Spring's list-injection and builds a `Map<PlatformType, DeliveryPlatformAdapter>`.

### Order Polling

`DeliveryIntegrationService.pollAllPlatforms()` runs on `@Scheduled(fixedDelayString = "${delivery.poll.interval-ms:30000}")`. Only platforms marked `active=true` in the `DELIVERY_PLATFORMS` table are polled.

### Webhook Server (Optional)

`WebhookServer` embeds a lightweight `com.sun.net.httpserver.HttpServer` (4 threads) to receive real-time push notifications:
- Enabled via `delivery.webhook.enabled=true` (default: `false` — polling-only mode)
- Port: `delivery.webhook.port` (default: `9090`)
- Endpoints: `POST /webhook/zomato`, `POST /webhook/swiggy`, `GET /health`
- Replay protection: SHA-256 of payload body deduplicates against `WebhookLog.payloadHash`
- Logs all raw payloads to `WEBHOOK_LOGS` table before processing

### Credential Encryption

`CredentialService` encrypts platform API keys stored in SQLite using AES-256-CBC with PBKDF2WithHmacSHA256 key derivation (65 536 iterations). Configure the master passphrase via `delivery.credential.secret` in `application.properties` — **change before first production use**.

### Platform Seeding

`DeliveryConfig` seeds `DELIVERY_PLATFORMS` rows for ZOMATO, SWIGGY, and MOCK (all `active=false`) on first startup. Activation and credential entry is done via the Platform Config screen.

---

### Database Location

| OS | Path |
|---|---|
| Windows | `%USERPROFILE%\hotel_management\hotel_management.db` |
| Unix | `~/hotel_management/hotel_management.db` |

The directory is created automatically by `AppConfig` on first run.

### Application Properties Reference

| Key | Default | Purpose |
|---|---|---|
| `delivery.poll.interval-ms` | `30000` | Platform polling interval (ms) |
| `delivery.webhook.enabled` | `false` | Enable embedded webhook HTTP server |
| `delivery.webhook.port` | `9090` | Webhook listener port |
| `delivery.credential.secret` | *(change me)* | AES-256 master passphrase for stored API keys |

### Packaging

- Fat JAR via Spring Boot Maven plugin (main class: `com.hotel.Main`)
- `installer/hotel-management.iss` — Inno Setup script for building a Windows `.exe` installer