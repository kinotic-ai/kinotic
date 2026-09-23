-- The node capacity ledger and fractional CPU. Elasticsearch cannot change a mapped field's
-- type, so the INTEGER available* columns of kinotic_vm_node stay mapped and unused: the free*
-- columns replace them, filled when the node registers. kinotic_workload.vcpus and autoRemove
-- stay mapped and unused the same way; cpus replaces vcpus.
ALTER TABLE kinotic_vm_node ADD COLUMN freeCpus DOUBLE ;
ALTER TABLE kinotic_vm_node ADD COLUMN freeMemoryMb INTEGER ;
ALTER TABLE kinotic_vm_node ADD COLUMN freeDiskMb INTEGER ;
ALTER TABLE kinotic_vm_node ADD COLUMN reservations NESTED (workloadId KEYWORD, cpus DOUBLE, memoryMb INTEGER, diskMb INTEGER) ;
ALTER TABLE kinotic_workload ADD COLUMN cpus DOUBLE ;
