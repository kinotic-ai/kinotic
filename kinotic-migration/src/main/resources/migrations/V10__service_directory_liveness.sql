-- A liveness write to a directory entry is an observation made at a time, and two writers observe
-- the same entry: the node whose services stop and the node whose services start, a verify and a
-- reconcile. The entry keeps the time of the latest observation applied, and a write observed
-- earlier that lands later is declined, so the last write to land is never the earlier word.
ALTER TABLE kinotic_service_directory ADD COLUMN livenessVerifiedAt LONG ;
