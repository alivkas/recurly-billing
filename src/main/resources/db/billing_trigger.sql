SELECT id, next_billing_date FROM subscriptions WHERE tenant_id = 'moscow_digital';

UPDATE subscriptions
SET next_billing_date = CURRENT_DATE
WHERE id = '6255878c-d579-48ac-8bac-beacacf286ba';

-- UPDATE subscriptions
-- SET trial_end = CURRENT_DATE
-- WHERE status = 'trialing';