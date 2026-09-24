-- The reconcile contract on a node: what it should be (taking workloads), what it reports it is
-- (taking workloads, or draining), the counters that tie the two, and the state every watched
-- record carries, with the NODE_UNREACHABLE condition set by silence or an undelivered call.
-- Declared whole: an OBJECT column cannot grow later. The status column stays mapped and unused;
-- healthMessage keeps the reason a node gives for not taking workloads.
ALTER TABLE kinotic_vm_node ADD COLUMN state OBJECT (conditions OBJECT (type KEYWORD, message TEXT, since DATE), parent KEYWORD, dirty BOOLEAN, dirtyAt LONG, desired OBJECT (phase KEYWORD), observed OBJECT (phase KEYWORD), generation LONG, observedGeneration LONG, deletionRequested DATE, reconciled BOOLEAN) ;
ALTER TABLE kinotic_vm_node ADD COLUMN healthMessage TEXT ;
