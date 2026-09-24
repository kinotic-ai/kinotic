-- The state every watched record carries, on a job run: the SERVER_NODE_LEFT condition the cluster
-- membership watch sets on a run whose node left, and the deployment the run was made by.
ALTER TABLE kinotic_job_run ADD COLUMN state OBJECT (conditions OBJECT (type KEYWORD, message TEXT, since DATE), parent KEYWORD, dirty BOOLEAN, dirtyAt LONG) ;
