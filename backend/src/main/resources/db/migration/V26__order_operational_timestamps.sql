-- Kasa operational KPIs need durable transition timestamps. last_activity_at cannot
-- represent these independently because every later state transition overwrites it.
ALTER TABLE customer_order ADD COLUMN preparation_started_at TIMESTAMPTZ;
ALTER TABLE customer_order ADD COLUMN ready_at TIMESTAMPTZ;
ALTER TABLE customer_order ADD COLUMN completed_at TIMESTAMPTZ;

-- ACCEPTED_BY_STORE is already an immutable audit event, so it is the trustworthy
-- preparation start for existing accepted orders.
UPDATE customer_order customer_order
SET preparation_started_at = accepted.created_at
FROM (
    SELECT entity_id, MIN(created_at) AS created_at
    FROM audit_log_entry
    WHERE entity_type = 'Order'
      AND action = 'ACCEPTED_BY_STORE'
    GROUP BY entity_id
) accepted
WHERE customer_order.id = accepted.entity_id
  AND customer_order.status IN ('IN_KITCHEN', 'READY', 'COMPLETED');

-- READY is still the current state, therefore last_activity_at is exactly its ready
-- transition. For historical COMPLETED rows the ready transition is no longer
-- recoverable; leave ready_at NULL so they are not fabricated into the average.
UPDATE customer_order
SET ready_at = last_activity_at
WHERE status = 'READY';

-- COMPLETED is terminal, so last_activity_at is the real completion timestamp.
UPDATE customer_order
SET completed_at = last_activity_at
WHERE status = 'COMPLETED';

CREATE INDEX idx_customer_order_branch_ready_at
    ON customer_order (branch_id, ready_at)
    WHERE ready_at IS NOT NULL;

CREATE INDEX idx_customer_order_branch_completed_at
    ON customer_order (branch_id, completed_at)
    WHERE completed_at IS NOT NULL;
