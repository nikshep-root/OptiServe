ALTER TABLE auth_users RENAME TO users;
ALTER INDEX uq_auth_users_email_lower RENAME TO uq_users_email_lower;

ALTER TABLE users
    ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'USER',
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE customers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(32),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_customers_user_id UNIQUE (user_id),
    CONSTRAINT fk_customers_user FOREIGN KEY (user_id) REFERENCES users (id)
);

INSERT INTO users (id, email, password_hash, role, enabled, created_at, updated_at)
SELECT legacy.customer_id,
       'legacy-' || legacy.customer_id || '@migration.invalid',
       'DISABLED_NO_PASSWORD',
       'USER',
       FALSE,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM (SELECT DISTINCT customer_id FROM vehicles) AS legacy;

INSERT INTO customers (id, user_id, name, phone, created_at, updated_at)
SELECT legacy.customer_id,
       legacy.customer_id,
       'Legacy customer',
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM (SELECT DISTINCT customer_id FROM vehicles) AS legacy;

ALTER TABLE vehicles
    ADD CONSTRAINT fk_vehicles_customer
    FOREIGN KEY (customer_id) REFERENCES customers (id);
