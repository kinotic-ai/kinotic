-- The system console fixtures of V5, sized in the columns V6 added: the nodes' free capacity is
-- what V5 seeded as available, and each workload's cpus is what it seeded as vcpus.
UPDATE kinotic_vm_node SET freeCpus = 7, freeMemoryMb = 14336, freeDiskMb = 389120 WHERE id == 'dev-worker-1' WITH REFRESH ;
UPDATE kinotic_vm_node SET freeCpus = 6, freeMemoryMb = 12288, freeDiskMb = 235520 WHERE id == 'dev-worker-2' WITH REFRESH ;
UPDATE kinotic_vm_node SET freeCpus = 8, freeMemoryMb = 16384, freeDiskMb = 128000 WHERE id == 'dev-worker-3' WITH REFRESH ;
UPDATE kinotic_workload SET cpus = 2 WHERE id == '00000000-0000-0000-0000-0000000000c1' WITH REFRESH ;
UPDATE kinotic_workload SET cpus = 4 WHERE id == '00000000-0000-0000-0000-0000000000c2' WITH REFRESH ;
UPDATE kinotic_workload SET cpus = 2 WHERE id == '00000000-0000-0000-0000-0000000000c3' WITH REFRESH ;
UPDATE kinotic_workload SET cpus = 2 WHERE id == '00000000-0000-0000-0000-0000000000c4' WITH REFRESH ;
UPDATE kinotic_workload SET cpus = 2 WHERE id == '00000000-0000-0000-0000-0000000000c5' WITH REFRESH ;
UPDATE kinotic_workload SET cpus = 1 WHERE id == '00000000-0000-0000-0000-0000000000c6' WITH REFRESH ;
UPDATE kinotic_workload SET cpus = 1 WHERE id == '00000000-0000-0000-0000-0000000000c7' WITH REFRESH ;
UPDATE kinotic_workload SET cpus = 1 WHERE id == '00000000-0000-0000-0000-0000000000c8' WITH REFRESH ;
