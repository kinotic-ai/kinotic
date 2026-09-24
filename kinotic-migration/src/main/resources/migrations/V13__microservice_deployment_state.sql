-- The reconcile contract on a microservice's deployment: what it should be (running the commit its
-- project's last deployment asked for, or left as the commit dropped it), what it is, the counters
-- that tie the two, and the state every watched record carries. Declared whole: an OBJECT column
-- cannot grow later. The status and commitSha columns stay mapped and unused; failureMessage keeps
-- why the microservice is not running as it should.
ALTER TABLE kinotic_microservice_deployment ADD COLUMN state OBJECT (conditions OBJECT (type KEYWORD, message TEXT, since DATE), parent KEYWORD, dirty BOOLEAN, dirtyAt LONG, desired OBJECT (phase KEYWORD, commitSha KEYWORD), observed OBJECT (phase KEYWORD, commitSha KEYWORD), generation LONG, observedGeneration LONG, deletionRequested DATE, reconciled BOOLEAN) ;
ALTER TABLE kinotic_microservice_deployment ADD COLUMN failureMessage TEXT ;
