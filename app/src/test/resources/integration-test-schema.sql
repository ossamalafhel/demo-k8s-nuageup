-- Integration Test Schema for Testcontainers
-- Creates banking schema and tables for integration testing

-- Create banking schema
CREATE SCHEMA IF NOT EXISTS banking;

-- Create transactions table
CREATE TABLE IF NOT EXISTS banking.transactions (
    id                  BIGSERIAL PRIMARY KEY,
    account_id          VARCHAR(20) NOT NULL,
    transaction_type    VARCHAR(20) NOT NULL CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    amount              DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    currency            VARCHAR(3) NOT NULL CHECK (currency IN ('USD', 'EUR', 'GBP')),
    description         VARCHAR(255),
    reference_number    VARCHAR(50) UNIQUE,
    target_account      VARCHAR(20),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    message             TEXT,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at        TIMESTAMP,
    created_by          VARCHAR(50),
    last_modified_by    VARCHAR(50),
    idempotency_key     VARCHAR(36) UNIQUE,
    client_ip           VARCHAR(45),
    user_agent          VARCHAR(500),
    version             BIGINT DEFAULT 0
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_transactions_account_id ON banking.transactions(account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON banking.transactions(created_at);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON banking.transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_created_by ON banking.transactions(created_by);
CREATE INDEX IF NOT EXISTS idx_transactions_idempotency_key ON banking.transactions(idempotency_key);

-- Create audit_log table for compliance
CREATE TABLE IF NOT EXISTS banking.audit_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(50),
    action          VARCHAR(50) NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       VARCHAR(50),
    old_value       TEXT,
    new_value       TEXT,
    client_ip       VARCHAR(45),
    user_agent      VARCHAR(500),
    session_id      VARCHAR(100),
    request_id      VARCHAR(100),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create security_events table for monitoring
CREATE TABLE IF NOT EXISTS banking.security_events (
    id              BIGSERIAL PRIMARY KEY,
    event_type      VARCHAR(50) NOT NULL,
    severity        VARCHAR(20) NOT NULL DEFAULT 'INFO',
    user_id         VARCHAR(50),
    client_ip       VARCHAR(45),
    user_agent      VARCHAR(500),
    details         TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert sample test data
INSERT INTO banking.transactions (
    account_id, transaction_type, amount, currency, description, 
    status, created_by, idempotency_key
) VALUES 
    ('ACC0000000001', 'DEPOSIT', 1000.00, 'USD', 'Initial test deposit', 'COMPLETED', 'system', gen_random_uuid()::text),
    ('ACC0000000002', 'WITHDRAWAL', 250.00, 'USD', 'Test withdrawal', 'COMPLETED', 'system', gen_random_uuid()::text),
    ('ACC0000000003', 'TRANSFER', 500.00, 'EUR', 'Test transfer', 'PENDING', 'system', gen_random_uuid()::text)
ON CONFLICT (idempotency_key) DO NOTHING;

-- Grant permissions (if using specific test user)
-- GRANT ALL PRIVILEGES ON SCHEMA banking TO test_user;
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA banking TO test_user;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA banking TO test_user;