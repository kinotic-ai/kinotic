-- The reconcile contract, declared whole on every record the reconcile master watches: an OBJECT
-- column cannot grow later. Every watched record carries conditions (what the platform inferred
-- beside what the record's owner reported), parent (the record it belongs to) and dirty/dirtyAt
-- (whether its last write has been seen by the master). A reconcilable record also carries what it
-- should be (desired), what it is (observed), the generations that tie the two, deletionRequested
-- and reconciled. The status and commitSha columns that state replaces stay mapped and unused, since
-- Elasticsearch cannot retype a mapped field.

-- A workload keeps the last status its node reported and carries NODE_UNREACHABLE until the node's
-- next report clears it.
ALTER TABLE kinotic_workload ADD COLUMN state OBJECT (conditions OBJECT (type KEYWORD, message TEXT, since DATE), parent KEYWORD, dirty BOOLEAN, dirtyAt LONG) ;

-- A job run carries SERVER_NODE_LEFT once the cluster membership watch sees its node leave, and the
-- deployment the run was made by.
ALTER TABLE kinotic_job_run ADD COLUMN state OBJECT (conditions OBJECT (type KEYWORD, message TEXT, since DATE), parent KEYWORD, dirty BOOLEAN, dirtyAt LONG) ;

-- A project's deployment: desired is the commit its last push asked for; observed is the phase it is
-- in and the commit it serves. failureMessage keeps the reason a deployment failed.
ALTER TABLE kinotic_project_deployment ADD COLUMN state OBJECT (conditions OBJECT (type KEYWORD, message TEXT, since DATE), parent KEYWORD, dirty BOOLEAN, dirtyAt LONG, desired OBJECT (phase KEYWORD, commitSha KEYWORD), observed OBJECT (phase KEYWORD, commitSha KEYWORD), generation LONG, observedGeneration LONG, desiredAt LONG, deletionRequested DATE, reconciled BOOLEAN) ;
ALTER TABLE kinotic_project_deployment ADD COLUMN failureMessage TEXT ;

-- A microservice's deployment: desired is running the commit its project's last deployment asked
-- for, or left as the commit dropped it. failureMessage keeps why it is not running as it should;
-- restartAt is when a VM that exited is started again.
ALTER TABLE kinotic_microservice_deployment ADD COLUMN state OBJECT (conditions OBJECT (type KEYWORD, message TEXT, since DATE), parent KEYWORD, dirty BOOLEAN, dirtyAt LONG, desired OBJECT (phase KEYWORD, commitSha KEYWORD), observed OBJECT (phase KEYWORD, commitSha KEYWORD), generation LONG, observedGeneration LONG, desiredAt LONG, deletionRequested DATE, reconciled BOOLEAN) ;
ALTER TABLE kinotic_microservice_deployment ADD COLUMN failureMessage TEXT ;
ALTER TABLE kinotic_microservice_deployment ADD COLUMN restartAt DATE ;

-- A UI's deployment: desired is serving the commit its project's last deployment published, or left
-- as the commit dropped it. observation keeps what the site answered when last checked.
ALTER TABLE kinotic_ui_deployment ADD COLUMN state OBJECT (conditions OBJECT (type KEYWORD, message TEXT, since DATE), parent KEYWORD, dirty BOOLEAN, dirtyAt LONG, desired OBJECT (phase KEYWORD, commitSha KEYWORD), observed OBJECT (phase KEYWORD, commitSha KEYWORD), generation LONG, observedGeneration LONG, desiredAt LONG, deletionRequested DATE, reconciled BOOLEAN) ;
ALTER TABLE kinotic_ui_deployment ADD COLUMN observation TEXT ;

-- A node: desired is taking workloads; observed is taking workloads or draining, with
-- NODE_UNREACHABLE set by silence or an undelivered call. healthMessage keeps the reason a node
-- gives for not taking workloads.
ALTER TABLE kinotic_vm_node ADD COLUMN state OBJECT (conditions OBJECT (type KEYWORD, message TEXT, since DATE), parent KEYWORD, dirty BOOLEAN, dirtyAt LONG, desired OBJECT (phase KEYWORD), observed OBJECT (phase KEYWORD), generation LONG, observedGeneration LONG, desiredAt LONG, deletionRequested DATE, reconciled BOOLEAN) ;
ALTER TABLE kinotic_vm_node ADD COLUMN healthMessage TEXT ;

-- The ledger: one entry per write to a watched record, saying what happened, from where and why.
-- The record says what it is now; its entries say how it got there. @timestamp is the write's time.
CREATE DATA STREAM kinotic_watch_event (type KEYWORD, id KEYWORD, parent KEYWORD, kind KEYWORD, source KEYWORD, serverNodeId KEYWORD, generation LONG, message TEXT, value JSON NOT INDEXED) WITH (DATA_RETENTION = '30d') ;
