-- Retention support for relay_records.
--
-- relay_records previously grew without bound: phones upload captured SMS /
-- notifications and nothing ever trimmed the table, leaving plaintext bodies
-- and OTP codes accumulating indefinitely. Retention is enforced in the
-- application after each ingest batch (per record_type counts that follow the
-- device's history-limit config, plus optional global count / age caps).
--
-- These indexes make the "keep the latest N" and "delete older than D days"
-- pruning queries efficient instead of forcing sequential scans.

-- Supports per-(user, record_type) pruning ordered by recency.
CREATE INDEX IF NOT EXISTS relay_records_user_type_recent_idx
    ON relay_records (user_id, record_type, occurred_at DESC, id DESC);

-- Supports global per-user count pruning and age-based pruning.
CREATE INDEX IF NOT EXISTS relay_records_user_recent_idx
    ON relay_records (user_id, occurred_at DESC, id DESC);
