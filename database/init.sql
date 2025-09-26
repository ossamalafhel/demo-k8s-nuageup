-- Initial database setup
-- This script is run when the database container is first created

-- Create database if not exists (handled by POSTGRES_DB env var)
-- CREATE DATABASE bankingdb;

-- Create schema
CREATE SCHEMA IF NOT EXISTS banking;

-- Set search path
SET search_path TO banking, public;

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Create user for application (if not using default postgres user)
-- CREATE USER banking_app WITH PASSWORD 'secure_password';
-- GRANT CONNECT ON DATABASE bankingdb TO banking_app;
-- GRANT USAGE ON SCHEMA banking TO banking_app;
-- GRANT CREATE ON SCHEMA banking TO banking_app;

-- Create audit function for tracking changes
CREATE OR REPLACE FUNCTION banking.audit_trigger_function()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.created_at = CURRENT_TIMESTAMP;
        NEW.updated_at = CURRENT_TIMESTAMP;
        NEW.created_by = COALESCE(NEW.created_by, CURRENT_USER);
        NEW.updated_by = COALESCE(NEW.updated_by, CURRENT_USER);
    ELSIF TG_OP = 'UPDATE' THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
        NEW.updated_by = COALESCE(NEW.updated_by, CURRENT_USER);
        NEW.created_at = OLD.created_at;
        NEW.created_by = OLD.created_by;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create performance monitoring
CREATE OR REPLACE FUNCTION banking.log_slow_queries()
RETURNS void AS $$
BEGIN
    -- Log queries taking more than 1 second
    PERFORM pg_stat_statements_reset();
END;
$$ LANGUAGE plpgsql;

-- Initial configuration
ALTER SYSTEM SET shared_preload_libraries = 'pg_stat_statements';
ALTER SYSTEM SET pg_stat_statements.track = 'all';
ALTER SYSTEM SET log_min_duration_statement = 1000;

-- Create tablespaces for better performance (optional)
-- CREATE TABLESPACE fast_ssd LOCATION '/mnt/fast_ssd/postgresql';
-- CREATE TABLESPACE archive_hdd LOCATION '/mnt/archive_hdd/postgresql';

COMMENT ON SCHEMA banking IS 'Banking demo application schema';

-- Grant permissions
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA banking TO banking_app;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA banking TO banking_app;
-- GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA banking TO banking_app;

-- Performance settings
ALTER DATABASE bankingdb SET random_page_cost = 1.1;
ALTER DATABASE bankingdb SET effective_io_concurrency = 200;
ALTER DATABASE bankingdb SET work_mem = '4MB';
ALTER DATABASE bankingdb SET maintenance_work_mem = '64MB';