-- Seed data for banking demo application
-- This creates sample data for testing and demonstration

SET search_path TO banking, public;

-- Insert sample transactions (if table exists)
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables 
               WHERE table_schema = 'banking' 
               AND table_name = 'transactions') THEN
        
        -- Clear existing data
        TRUNCATE TABLE banking.transactions RESTART IDENTITY CASCADE;
        
        -- Insert sample transactions
        INSERT INTO banking.transactions 
        (account_id, transaction_type, amount, currency, description, status, reference_number, created_at)
        VALUES 
        ('ACC0000000001', 'DEPOSIT', 10000.00, 'USD', 'Initial deposit', 'COMPLETED', 'TXN-2024-001', NOW() - INTERVAL '30 days'),
        ('ACC0000000001', 'WITHDRAWAL', 500.00, 'USD', 'ATM withdrawal', 'COMPLETED', 'TXN-2024-002', NOW() - INTERVAL '28 days'),
        ('ACC0000000001', 'TRANSFER', 2000.00, 'USD', 'Transfer to savings', 'COMPLETED', 'TXN-2024-003', NOW() - INTERVAL '25 days'),
        ('ACC0000000002', 'DEPOSIT', 15000.00, 'USD', 'Salary credit', 'COMPLETED', 'TXN-2024-004', NOW() - INTERVAL '20 days'),
        ('ACC0000000002', 'WITHDRAWAL', 1000.00, 'USD', 'Cash withdrawal', 'COMPLETED', 'TXN-2024-005', NOW() - INTERVAL '18 days'),
        ('ACC0000000003', 'DEPOSIT', 5000.00, 'USD', 'Check deposit', 'COMPLETED', 'TXN-2024-006', NOW() - INTERVAL '15 days'),
        ('ACC0000000003', 'TRANSFER', 3000.00, 'USD', 'Wire transfer', 'PENDING_APPROVAL', 'TXN-2024-007', NOW() - INTERVAL '10 days'),
        ('ACC0000000004', 'DEPOSIT', 25000.00, 'EUR', 'International transfer', 'COMPLETED', 'TXN-2024-008', NOW() - INTERVAL '7 days'),
        ('ACC0000000004', 'WITHDRAWAL', 5000.00, 'EUR', 'Large withdrawal', 'COMPLETED', 'TXN-2024-009', NOW() - INTERVAL '5 days'),
        ('ACC0000000005', 'DEPOSIT', 7500.00, 'GBP', 'Business deposit', 'COMPLETED', 'TXN-2024-010', NOW() - INTERVAL '3 days'),
        ('ACC0000000001', 'TRANSFER', 1500.00, 'USD', 'Bill payment', 'COMPLETED', 'TXN-2024-011', NOW() - INTERVAL '2 days'),
        ('ACC0000000002', 'DEPOSIT', 3000.00, 'USD', 'Bonus credit', 'COMPLETED', 'TXN-2024-012', NOW() - INTERVAL '1 day'),
        ('ACC0000000003', 'WITHDRAWAL', 200.00, 'USD', 'ATM withdrawal', 'COMPLETED', 'TXN-2024-013', NOW() - INTERVAL '12 hours'),
        ('ACC0000000004', 'TRANSFER', 10000.00, 'EUR', 'Investment transfer', 'PENDING', 'TXN-2024-014', NOW() - INTERVAL '6 hours'),
        ('ACC0000000005', 'DEPOSIT', 500.00, 'GBP', 'Mobile deposit', 'COMPLETED', 'TXN-2024-015', NOW() - INTERVAL '1 hour');
        
        RAISE NOTICE 'Sample transactions inserted successfully';
    ELSE
        RAISE NOTICE 'Transactions table does not exist yet - skipping seed data';
    END IF;
END $$;

-- Create sample accounts (if table exists)
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables 
               WHERE table_schema = 'banking' 
               AND table_name = 'accounts') THEN
        
        INSERT INTO banking.accounts (account_id, account_name, account_type, balance, currency, status)
        VALUES 
        ('ACC0000000001', 'John Doe', 'CHECKING', 6500.00, 'USD', 'ACTIVE'),
        ('ACC0000000002', 'Jane Smith', 'SAVINGS', 17000.00, 'USD', 'ACTIVE'),
        ('ACC0000000003', 'Bob Johnson', 'CHECKING', 4800.00, 'USD', 'ACTIVE'),
        ('ACC0000000004', 'Alice Brown', 'BUSINESS', 20000.00, 'EUR', 'ACTIVE'),
        ('ACC0000000005', 'Charlie Wilson', 'SAVINGS', 8000.00, 'GBP', 'ACTIVE')
        ON CONFLICT (account_id) DO NOTHING;
        
        RAISE NOTICE 'Sample accounts created successfully';
    END IF;
END $$;

-- Create indexes for better performance
DO $$
BEGIN
    -- Index on account_id for faster lookups
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_transactions_account_id') THEN
        CREATE INDEX idx_transactions_account_id ON banking.transactions(account_id);
    END IF;
    
    -- Index on created_at for time-based queries
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_transactions_created_at') THEN
        CREATE INDEX idx_transactions_created_at ON banking.transactions(created_at DESC);
    END IF;
    
    -- Index on status for filtering
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_transactions_status') THEN
        CREATE INDEX idx_transactions_status ON banking.transactions(status);
    END IF;
    
    -- Composite index for common queries
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_transactions_account_status') THEN
        CREATE INDEX idx_transactions_account_status ON banking.transactions(account_id, status);
    END IF;
END $$;

-- Update statistics
ANALYZE banking.transactions;