-- V2__add_performance_indexes.sql
-- Add additional indexes and performance optimizations

SET search_path TO banking, public;

-- Partial indexes for common queries
CREATE INDEX CONCURRENTLY idx_transactions_pending 
    ON banking.transactions(account_id, created_at DESC) 
    WHERE status = 'PENDING';

CREATE INDEX CONCURRENTLY idx_transactions_completed_recent 
    ON banking.transactions(account_id, processed_at DESC) 
    WHERE status = 'COMPLETED' AND processed_at > CURRENT_DATE - INTERVAL '90 days';

-- Index for large transactions requiring approval
CREATE INDEX CONCURRENTLY idx_transactions_pending_approval 
    ON banking.transactions(amount DESC, created_at) 
    WHERE status = 'PENDING_APPROVAL';

-- Composite indexes for reporting
CREATE INDEX CONCURRENTLY idx_transactions_daily_summary 
    ON banking.transactions(DATE(created_at), transaction_type, status);

-- Function-based index for case-insensitive searches
CREATE INDEX CONCURRENTLY idx_accounts_name_lower 
    ON banking.accounts(LOWER(account_name));

-- BRIN index for timestamp columns (efficient for large tables)
CREATE INDEX idx_audit_log_changed_at_brin 
    ON banking.audit_log USING BRIN(changed_at);

-- Add table partitioning preparation (for future use)
-- This creates a function to help with monthly partitioning of transactions
CREATE OR REPLACE FUNCTION banking.create_monthly_partition(
    table_name text,
    start_date date
)
RETURNS void AS $$
DECLARE
    partition_name text;
    end_date date;
BEGIN
    partition_name := table_name || '_' || to_char(start_date, 'YYYY_MM');
    end_date := start_date + INTERVAL '1 month';
    
    EXECUTE format('CREATE TABLE IF NOT EXISTS banking.%I PARTITION OF banking.%I 
        FOR VALUES FROM (%L) TO (%L)',
        partition_name, table_name, start_date, end_date);
    
    -- Create indexes on partition
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON banking.%I (account_id)',
        'idx_' || partition_name || '_account_id', partition_name);
    
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON banking.%I (created_at)',
        'idx_' || partition_name || '_created_at', partition_name);
END;
$$ LANGUAGE plpgsql;

-- Add statistics tracking
CREATE STATISTICS banking.transaction_stats (dependencies) 
    ON account_id, transaction_type, status 
    FROM banking.transactions;

-- Optimize common queries with materialized view
CREATE MATERIALIZED VIEW banking.account_summary AS
SELECT 
    a.account_id,
    a.account_name,
    a.balance,
    a.currency,
    COUNT(t.id) as total_transactions,
    SUM(CASE WHEN t.transaction_type = 'DEPOSIT' THEN t.amount ELSE 0 END) as total_deposits,
    SUM(CASE WHEN t.transaction_type = 'WITHDRAWAL' THEN t.amount ELSE 0 END) as total_withdrawals,
    MAX(t.created_at) as last_transaction_date
FROM banking.accounts a
LEFT JOIN banking.transactions t ON a.account_id = t.account_id
WHERE t.status = 'COMPLETED'
GROUP BY a.account_id, a.account_name, a.balance, a.currency;

-- Create index on materialized view
CREATE INDEX idx_account_summary_account_id ON banking.account_summary(account_id);

-- Function to refresh materialized view
CREATE OR REPLACE FUNCTION banking.refresh_account_summary()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY banking.account_summary;
END;
$$ LANGUAGE plpgsql;

-- Update table statistics
ANALYZE banking.accounts;
ANALYZE banking.transactions;
ANALYZE banking.audit_log;