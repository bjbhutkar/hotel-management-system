# 🏨 Hotel Management System

A fully **offline, desktop-based** Hotel & Restaurant Management System built with **Java 21, JavaFX, Spring Boot 3, and SQLite**.

---

## ✨ Features

| Module | Details |
|--------|---------|
| **User Authentication** | Admin & Staff roles, BCrypt passwords, session management |
| **Menu Management** | Add / edit / delete items, categories, availability toggle |
| **Order Management** | Create orders, add/remove items, table tracking |
| **Billing & Invoice** | Formatted text invoice, GST calculation, print & save |
| **Reports** | Daily / Weekly / Monthly sales, most/least sold items |
| **Cancelled Orders** | Full audit trail with cancellation reasons |
| **Backup & Restore** | One-click SQLite backup and restore |

---

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | JavaFX 21 + FXML |
| Backend | Spring Boot 3.2 (no web server) |
| Database | SQLite (Hibernate / Spring Data JPA) |
| Password Hashing | BCrypt (Spring Security Crypto) |
| Reports | Text-based + JasperReports 6.20 |
| Build | Maven 3.9 |
| Packaging | Inno Setup (Windows installer) |

---

## 📁 Project Structure

```
src/main/java/com/hotel/
├── Main.java                        # Fat-JAR launcher
├── HotelManagementApp.java          # Spring Boot + JavaFX bootstrap
├── config/
│   ├── AppConfig.java               # BCrypt bean, DB directory setup
│   └── DataInitializer.java         # Seeds default users & menu items
├── entity/                          # JPA entities
│   ├── User.java
│   ├── MenuItem.java
│   ├── Order.java
│   ├── OrderItem.java
│   └── AuditLog.java
├── repository/                      # Spring Data JPA repositories
├── service/                         # Business logic
│   ├── UserService.java
│   ├── MenuService.java
│   ├── OrderService.java
│   ├── BillingService.java
│   ├── ReportService.java
│   └── BackupService.java
└── ui/                              # JavaFX controllers
    ├── StageManager.java
    ├── LoginController.java
    ├── DashboardController.java
    ├── MenuManagementController.java
    ├── OrderManagementController.java
    ├── BillingController.java
    └── ReportController.java

src/main/resources/
├── fxml/          # login, dashboard, menu-management, order-management, billing, reports
├── css/styles.css
├── db/schema.sql  # Reference DDL (Hibernate auto-creates tables)
└── application.properties

installer/hotel-management.iss   # Inno Setup script → one-click .exe installer
scripts/run.bat                  # Windows launch script
scripts/backup.bat               # Manual backup utility
```

---

## 🚀 Getting Started

### Prerequisites
- **Java 21** JDK (e.g., [Temurin](https://adoptium.net/))
- **Maven 3.9+**

### Run in Development
```bash
mvn clean javafx:run
```

### Build Fat JAR
```bash
mvn clean package -DskipTests
java -jar target/hotel-management-system-1.0.0.jar
```

### Windows – Double-click Launch
```
scripts\run.bat
```

---

## 🔐 Default Credentials

| Role  | Username | Password  |
|-------|----------|-----------|
| Admin | `admin`  | `admin123`|
| Staff | `staff`  | `staff123`|

> ⚠️ **Change these immediately after first login.**

---

## 🗄 Database

- Location: `%USERPROFILE%\hotel_management\hotel_management.db` (Windows)
- SQLite file created automatically on first run
- Hibernate manages schema (`ddl-auto=update`)

---

## 🖨 Invoice Format

```
================================================
         HOTEL MANAGEMENT SYSTEM
                  INVOICE
================================================
Invoice No  : ORD-20240514-0001
Date & Time : 14-05-2024 19:30
Table No    : 5
Customer    : John Doe
Served by   : Administrator
------------------------------------------------
Item                        Qty     Amount
------------------------------------------------
Butter Chicken                2     560.00
  @ ₹280.00 each
Naan                          4     160.00
  @ ₹40.00 each
------------------------------------------------
Subtotal:                            720.00
GST @ 5%:                             36.00
================================================
TOTAL AMOUNT:                        756.00
================================================
      Thank you for dining with us!
```

---

## 📦 Windows Installer

1. Build the fat JAR: `mvn clean package -DskipTests`
2. Open `installer/hotel-management.iss` in [Inno Setup 6](https://jrsoftware.org/isinfo.php)
3. Compile → produces `HotelManagementSetup.exe`

---

## 📊 Reports Available

- **Daily Sales** — orders, revenue, GST for a selected day
- **Weekly Sales** — any 7-day window
- **Monthly Sales** — full month summary
- **Top Selling Items** — most ordered (by quantity)
- **Least Selling Items** — slowest movers
- **Cancelled Orders** — with reasons and amounts

---

## 📄 License

MIT License — free for commercial and personal use.
● All three files are in place. Here's how to use it:                                                                                                                                                                            

  ---
  Developer (you) — to produce the installer:

  1. Install Inno Setup 6 from https://jrsoftware.org/isdl.php (one-time)
  2. Double-click installer\build-installer.bat                                                                                                                                                                

  That script will:
  - Run mvn clean package to build the JAR
  - Download Eclipse Temurin 21 JRE (~130 MB) into installer\jre\ on first run (cached for future builds)
  - Convert rasoi.png → rasoi.ico                                                                                                                                                                                                
  - Compile everything into dist\RasoiSetup.exe                                                                                                                                                                                  

  ---
  Customer — to install:

  1. Double-click RasoiSetup.exe                                                                                                                                                                                                 
  2. Click Next / Next / Finish
  3. Launch from desktop shortcut

  No Java installation, no PATH setup, no errors. The app runs from the JRE bundled inside C:\Program Files\Rasoi\jre\. The database is stored separately at %USERPROFILE%\hotel_management\ so it survives reinstalls/updates.  

  ---
  Installer size estimate:

  1. Install Inno Setup 6 from https://jrsoftware.org/isdl.php (one-time)
  2. Double-click installer\build-installer.bat                                                                                                                                                                                  

  That script will:
  - Run mvn clean package to build the JAR
  - Download Eclipse Temurin 21 JRE (~130 MB) into installer\jre\ on first run (cached for future builds)
  - Convert rasoi.png → rasoi.ico                                                                                                                                                                                                
  - Compile everything into dist\RasoiSetup.exe                                                                                                                                                                                  

  ---
  Customer — to install:

  1. Double-click RasoiSetup.exe
  2. Click Next / Next / Finish
  3. Launch from desktop shortcut

  No Java installation, no PATH setup, no errors. The app runs from the JRE bundled inside C:\Program Files\Rasoi\jre\. The database is stored separately at %USERPROFILE%\hotel_management\ so it survives reinstalls/updates.  

  ---
  Installer size estimate:
  - Fat JAR: ~80–120 MB
  - JRE: ~130 MB uncompressed → ~60 MB compressed with LZMA
  - Total installer: ~150–180 MB