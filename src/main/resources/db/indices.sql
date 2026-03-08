CREATE INDEX IF NOT EXISTS idx_subscriptions_next_billing_active
    ON subscriptions (next_billing_date)
    WHERE status = 'active' AND next_billing_date IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_subscriptions_tenant_customer
    ON subscriptions (tenant_id, customer_id);

CREATE INDEX IF NOT EXISTS idx_subscriptions_tenant_status
    ON subscriptions (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_invoices_payment_id
    ON invoices (payment_id);

-- CREATE UNIQUE INDEX idx_plans_tenant_code ON plans (tenant_id, code);
CREATE INDEX IF NOT EXISTS idx_customers_tenant ON customers (tenant_id);
-- CREATE INDEX IF NOT EXISTS idx_plans_tenant ON plans (tenant_id);
CREATE INDEX IF NOT EXISTS idx_invoices_tenant ON invoices (tenant_id);
CREATE INDEX IF NOT EXISTS idx_notifications_tenant ON notifications (tenant_id);