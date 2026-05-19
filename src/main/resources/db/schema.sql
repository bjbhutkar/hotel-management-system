-- =============================================================
-- Hotel Management System — Reference Schema
-- (Hibernate auto-creates these via ddl-auto=update)
-- =============================================================

CREATE TABLE IF NOT EXISTS USERS (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    username    TEXT    NOT NULL UNIQUE,
    password    TEXT    NOT NULL,
    full_name   TEXT    NOT NULL,
    role        TEXT    NOT NULL CHECK(role IN ('ADMIN','STAFF')),
    active      INTEGER NOT NULL DEFAULT 1,
    created_at  TEXT,
    last_login  TEXT
);

CREATE TABLE IF NOT EXISTS MENU_ITEMS (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,
    description TEXT,
    category    TEXT    NOT NULL,
    price       REAL    NOT NULL,
    available   INTEGER NOT NULL DEFAULT 1,
    created_at  TEXT,
    updated_at  TEXT
);

CREATE TABLE IF NOT EXISTS ORDERS (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    order_number        TEXT    NOT NULL UNIQUE,
    table_number        INTEGER NOT NULL,
    customer_name       TEXT,
    status              TEXT    NOT NULL DEFAULT 'PENDING'
                            CHECK(status IN ('PENDING','IN_PROGRESS','COMPLETED','CANCELLED','BILLED')),
    subtotal            REAL    NOT NULL DEFAULT 0,
    tax_rate            REAL    NOT NULL DEFAULT 5.00,
    tax_amount          REAL    NOT NULL DEFAULT 0,
    total_amount        REAL    NOT NULL DEFAULT 0,
    cancellation_reason TEXT,
    created_by_id       INTEGER REFERENCES USERS(id),
    created_at          TEXT    NOT NULL,
    updated_at          TEXT,
    completed_at        TEXT
);

CREATE TABLE IF NOT EXISTS ORDER_ITEMS (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id     INTEGER NOT NULL REFERENCES ORDERS(id) ON DELETE CASCADE,
    menu_item_id INTEGER NOT NULL REFERENCES MENU_ITEMS(id),
    quantity     INTEGER NOT NULL,
    unit_price   REAL    NOT NULL,
    total_price  REAL    NOT NULL,
    notes        TEXT
);

CREATE TABLE IF NOT EXISTS AUDIT_LOGS (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    action       TEXT NOT NULL,
    entity_type  TEXT NOT NULL,
    entity_id    INTEGER,
    description  TEXT,
    performed_by TEXT,
    timestamp    TEXT NOT NULL
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_orders_status       ON ORDERS(status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at   ON ORDERS(created_at);
CREATE INDEX IF NOT EXISTS idx_order_items_order   ON ORDER_ITEMS(order_id);
CREATE INDEX IF NOT EXISTS idx_menu_items_category ON MENU_ITEMS(category);
