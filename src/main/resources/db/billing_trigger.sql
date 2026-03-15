SELECT id, next_billing_date FROM subscriptions WHERE tenant_id = 'moscow_digital';

UPDATE subscriptions
SET next_billing_date = CURRENT_DATE
WHERE id = 'bf1a771a-5916-432d-b4b8-efb35e43bdf4';

UPDATE subscriptions
SET trial_end = CURRENT_DATE
WHERE status = 'trialing';

UPDATE subscriptions
SET next_billing_date = CURRENT_DATE + INTERVAL '3 days'
WHERE status = 'active';

UPDATE subscriptions
SET trial_end = CURRENT_DATE + INTERVAL '3 days'
WHERE status = 'trialing';

SELECT telegram_chat_id FROM customers WHERE external_id = 'alivka';