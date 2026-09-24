-- The ledger: what happened to every watched record, from where and why, one entry per write. The
-- record says what it is now; its entries say how it got there. @timestamp is the write's time.
CREATE DATA STREAM kinotic_watch_event (type KEYWORD, id KEYWORD, parent KEYWORD, kind KEYWORD, source KEYWORD, serverNodeId KEYWORD, generation LONG, message TEXT, value JSON NOT INDEXED) WITH (DATA_RETENTION = '30d') ;
