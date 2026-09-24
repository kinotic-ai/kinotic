-- What the orchestrator infers about a workload without the node confirming it. A workload whose
-- node fell silent keeps the last status the node reported and carries NODE_UNREACHABLE until the
-- node's next report clears it.
ALTER TABLE kinotic_workload ADD COLUMN conditions NESTED (type KEYWORD, message TEXT, since DATE) ;
