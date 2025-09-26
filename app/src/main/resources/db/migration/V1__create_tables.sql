-- V1__create_tables.sql
-- Initial schema creation for banking demo application

-- Create schema if not exists
CREATE SCHEMA IF NOT EXISTS banking;

-- Set search path
SET search_path TO banking, public;

-- Create accounts table
CREATE TABLE IF NOT EXISTS banking.accounts (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(20) NOT NULL UNIQUE,
    account_name VARCHAR(100) NOT NULL,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('CHECKING', 'SAVINGS', 'BUSINESS')),
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    version BIGINT DEFAULT 0
);

-- Create transactions table
CREATE TABLE IF NOT EXISTS banking.transactions (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(20) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    amount DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    description VARCHAR(255),
    reference_number VARCHAR(50) UNIQUE,
    target_account VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    CONSTRAINT fk_account 
        FOREIGN KEY(account_id) 
        REFERENCES banking.accounts(account_id)
);

-- Create audit log table
CREATE TABLE IF NOT EXISTS banking.audit_log (
    id BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(50) NOT NULL,
    operation VARCHAR(10) NOT NULL,
    record_id BIGINT NOT NULL,
    old_values JSONB,
    new_values JSONB,
    changed_by VARCHAR(50),
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_accounts_account_id ON banking.accounts(account_id);
CREATE INDEX idx_accounts_status ON banking.accounts(status);
CREATE INDEX idx_transactions_account_id ON banking.transactions(account_id);
CREATE INDEX idx_transactions_created_at ON banking.transactions(created_at DESC);
CREATE INDEX idx_transactions_status ON banking.transactions(status);
CREATE INDEX idx_transactions_reference ON banking.transactions(reference_number);
CREATE INDEX idx_audit_log_table_record ON banking.audit_log(table_name, record_id);
CREATE INDEX idx_audit_log_changed_at ON banking.audit_log(changed_at DESC);

-- Create update trigger for updated_at
CREATE OR REPLACE FUNCTION banking.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply trigger to accounts table
CREATE TRIGGER update_accounts_updated_at BEFORE UPDATE
    ON banking.accounts FOR EACH ROW
    EXECUTE FUNCTION banking.update_updated_at_column();

-- Apply trigger to transactions table
CREATE TRIGGER update_transactions_updated_at BEFORE UPDATE
    ON banking.transactions FOR EACH ROW
    EXECUTE FUNCTION banking.update_updated_at_column();

-- Create audit trigger function
CREATE OR REPLACE FUNCTION banking.audit_trigger_function()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO banking.audit_log(table_name, operation, record_id, new_values, changed_by)
        VALUES (TG_TABLE_NAME, TG_OP, NEW.id, to_jsonb(NEW), COALESCE(NEW.created_by, CURRENT_USER));
        RETURN NEW;
    ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO banking.audit_log(table_name, operation, record_id, old_values, new_values, changed_by)
        VALUES (TG_TABLE_NAME, TG_OP, NEW.id, to_jsonb(OLD), to_jsonb(NEW), COALESCE(NEW.updated_by, CURRENT_USER));
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO banking.audit_log(table_name, operation, record_id, old_values, changed_by)
        VALUES (TG_TABLE_NAME, TG_OP, OLD.id, to_jsonb(OLD), CURRENT_USER);
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Apply audit triggers
CREATE TRIGGER audit_accounts_changes
    AFTER INSERT OR UPDATE OR DELETE ON banking.accounts
    FOR EACH ROW EXECUTE FUNCTION banking.audit_trigger_function();

CREATE TRIGGER audit_transactions_changes
    AFTER INSERT OR UPDATE OR DELETE ON banking.transactions
    FOR EACH ROW EXECUTE FUNCTION banking.audit_trigger_function();

-- Add comments
COMMENT ON TABLE banking.accounts IS 'Customer bank accounts';
COMMENT ON TABLE banking.transactions IS 'Financial transactions';
COMMENT ON TABLE banking.audit_log IS 'Audit trail for all changes';
COMMENT ON COLUMN banking.transactions.status IS 'PENDING, COMPLETED, FAILED, CANCELLED, PENDING_APPROVAL';
COMMENT ON COLUMN banking.accounts.status IS 'ACTIVE, SUSPENDED, CLOSED';