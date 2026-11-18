-- Automatically create the order database if it does not already exist
SELECT 'CREATE DATABASE "order"'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'order')\gexec
