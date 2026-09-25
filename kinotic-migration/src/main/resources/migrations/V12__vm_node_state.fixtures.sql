-- The console fixture nodes in their desired state, as a registered node that heartbeats is;
-- dev-worker-3 reports a problem, so it drains
UPDATE kinotic_vm_node SET state = { desired: { phase: 'ONLINE' }, observed: { phase: 'ONLINE' }, generation: 1, observedGeneration: 1, dirty: false, dirtyAt: 0, reconciled: true } WHERE id == 'dev-worker-1' WITH REFRESH ;
UPDATE kinotic_vm_node SET state = { desired: { phase: 'ONLINE' }, observed: { phase: 'ONLINE' }, generation: 1, observedGeneration: 1, dirty: false, dirtyAt: 0, reconciled: true } WHERE id == 'dev-worker-2' WITH REFRESH ;
UPDATE kinotic_vm_node SET state = { desired: { phase: 'ONLINE' }, observed: { phase: 'DRAINING' }, generation: 1, observedGeneration: 1, dirty: false, dirtyAt: 0, reconciled: false }, healthMessage = 'data root /var/lib/kinotic/workloads no longer enforces project quotas' WHERE id == 'dev-worker-3' WITH REFRESH ;
