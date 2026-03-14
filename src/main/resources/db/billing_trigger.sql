SELECT id, next_billing_date FROM subscriptions WHERE tenant_id = 'moscow_digital';

UPDATE subscriptions
SET next_billing_date = CURRENT_DATE
WHERE id = 'ba1a64d1-43c1-4337-89fa-4acf7e0d2cfb';