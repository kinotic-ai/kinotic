-- What the platform keeps on a workload beside the node's own report: what it inferred (a workload
-- whose node fell silent keeps the last status the node reported and carries NODE_UNREACHABLE until
-- the node's next report clears it), the deployment the workload belongs to, and whether its last
-- write has been seen by the reconcile master. Declared whole: an OBJECT column cannot grow later.
ALTER TABLE kinotic_workload ADD COLUMN state OBJECT (conditions OBJECT (type KEYWORD, message TEXT, since DATE), parent KEYWORD, dirty BOOLEAN, dirtyAt LONG) ;
