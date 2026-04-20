SELECT id, next_billing_date FROM subscriptions WHERE tenant_id = 'moscow_digital';

UPDATE subscriptions
SET next_billing_date = CURRENT_DATE
WHERE id = 'ba80aba6-1e75-43a2-a5fc-268d31af3619';

UPDATE subscriptions
SET trial_end = CURRENT_DATE
WHERE status = 'trialing';

UPDATE subscriptions
SET next_billing_date = CURRENT_DATE + INTERVAL '3 days'
WHERE status = 'active';

UPDATE subscriptions
SET trial_end = CURRENT_DATE + INTERVAL '1 days'
WHERE status = 'trialing';

SELECT telegram_chat_id FROM customers WHERE external_id = 'alivka';