-- The reconcile contract on a project's deployment: what it should be (the commit its last push asked
-- for), what it is (the phase it is in and the commit it serves), the counters that tie the two, and
-- the state every watched record carries. Declared whole: an OBJECT column cannot grow later. The
-- status and commitSha columns stay mapped and unused; failureMessage keeps the reason a deployment
-- failed.
ALTER TABLE kinotic_project_deployment ADD COLUMN state OBJECT (conditions OBJECT (type KEYWORD, message TEXT, since DATE), parent KEYWORD, dirty BOOLEAN, dirtyAt LONG, desired OBJECT (phase KEYWORD, commitSha KEYWORD), observed OBJECT (phase KEYWORD, commitSha KEYWORD), generation LONG, observedGeneration LONG, deletionRequested DATE, reconciled BOOLEAN) ;
ALTER TABLE kinotic_project_deployment ADD COLUMN failureMessage TEXT ;
