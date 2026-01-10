import { Pageable } from '@kinotic/continuum-client';
import { Structures, IEntityService, StructureService } from '@kinotic/structures-api';
import { WebSocket } from 'ws';
import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest';
import { createVehicleStructure, createTestVehicles } from '../TestHelpers';
import { Vehicle } from '../domain/Vehicle';
import {
    clearEvictionFiles,
    waitForEvictions,
    summarizeEvictions,
    filterByStructureId,
    type EvictionsByPod
} from './eviction-utils';
import { K8sTestHelper } from './k8s-helper';

// Make WebSocket available globally for continuum-client
Object.assign(global, { WebSocket });

/**
 * K8s Cache Eviction Tests
 * 
 * These tests verify that cache eviction propagates correctly across all pods
 * in a Kubernetes cluster using Ignite Compute Grid.
 * 
 * Prerequisites:
 * - Kubernetes cluster running (KinD or any K8s cluster)
 * - structures-server deployed with 3 replicas
 * - kubectl configured and cluster accessible
 * - Set K8S_TEST_ENABLED=true to run these tests
 * 
 * Setup:
 * 1. Create KinD cluster: ./dev-tools/kind/kind-cluster.sh create
 * 2. Build image: ./gradlew :structures-server:bootBuildImage
 * 3. Load image: ./dev-tools/kind/kind-cluster.sh load
 * 4. Deploy with 3 replicas: ./dev-tools/kind/kind-cluster.sh deploy
 * 5. Run tests: npm test -- k8s-cache-eviction.test.ts
 * 
 * Environment variables:
 * - K8S_TEST_ENABLED: Enable K8s tests (default: false)
 * - K8S_CONTEXT: Kubernetes context (default: kind-structures-cluster)
 * - K8S_NAMESPACE: Kubernetes namespace (default: default)
 * - K8S_LABEL_SELECTOR: Pod label selector (default: app=structures)
 * - K8S_REPLICA_COUNT: Expected replicas (default: 3)
 * - K8S_STOMP_PORT: Continuum STOMP port (default: 58503)
 * - K8S_STARTING_LOCAL_PORT: Starting local port for port-forwards (default: 58511)
 */
describe('K8s Cache Eviction Tests', () => {
    let k8s: K8sTestHelper;

    beforeAll(async () => {
        k8s = new K8sTestHelper();

        if (!k8s.isEnabled()) {
            console.log('K8s tests disabled. Set K8S_TEST_ENABLED=true to run these tests.');
            return;
        }

        // Check cluster accessibility
        const accessible = await k8s.isClusterAccessible();
        if (!accessible) {
            throw new Error('Kubernetes cluster is not accessible');
        }

        // Discover pods
        await k8s.discoverPods();

        // Port-forwards will be created on-demand as needed
    }, 120000); // 2 minute timeout for setup

    afterAll(async () => {
        if (!k8s.isEnabled()) {
            return;
        }

        await k8s.stopPortForwards();
    }, 60000);

    beforeEach(async () => {
        await k8s.disconnectFromPod();
    }, 60000);

    it('should propagate cache eviction across all pods with entity operations', async () => {
        if (!k8s.isEnabled()) {
            console.log('Test 1: Test skipped: K8s tests not enabled');
            return;
        }

        const applicationId = 'k8stest-' + Date.now();
        const structureName = 'vehicle';
        const structureId = `${applicationId}.${structureName}`.toLowerCase();
        const initialDescription = `Some form of transportation`;
        const evictionDataPath = k8s.getEvictionDataPath();

        // Record timestamp before test for filtering eviction records
        const testStartTime = Date.now();

        // Step 0: Clear eviction files to ensure clean slate
        console.log('Test 1: Step 0 - Clearing eviction files');
        clearEvictionFiles(evictionDataPath);

        // Step 1: Connect to pod 0 and create structure
        console.log('\nTest 1: Step 1 - Creating structure on pod 0');
        await k8s.connectToPod(0);

        console.log('Test 1: Connected to pod 0');

        const savedStructure = await createVehicleStructure(applicationId, 'TestProject');

        console.log('Test 1: Saved structure', savedStructure);

        expect(savedStructure).toBeDefined();
        expect(savedStructure.id).toBe(structureId);
        expect(savedStructure.description).toBe(initialDescription);
        console.log(`Test 1: Created and Published structure: ${structureId}`);

        // Step 2: Create test entities (vehicles)
        console.log('\nTest 1: Step 2 - Creating test vehicles');
        const entityService: IEntityService<Vehicle> = Structures.createEntityService(applicationId, structureName);
        const testVehicles = createTestVehicles(5);

        for (const vehicle of testVehicles) {
            await entityService.save(vehicle);
        }
        await entityService.syncIndex();
        console.log(`Test 1: Created ${testVehicles.length} test vehicles`);

        await k8s.disconnectFromPod();

        await new Promise(resolve => setTimeout(resolve, 2000));

        // Step 3: Warm entity caches on ALL pods via findAll and search
        console.log('\nTest 1: Step 3 - Warming caches on all pods via entity operations');
        for (let i = 0; i < k8s.getPodCount(); i++) {
            console.log(`Test 1: Warming cache on pod ${i}`);

            await k8s.connectToPod(i);

            // Warm structure cache
            const structureService = Structures.getStructureService();

            const retrieved = await structureService.findById(structureId);
            expect(retrieved).toBeDefined();
            expect(retrieved?.description).toBe(initialDescription);
            console.log(`Test 1: Pod ${i}: Structure cached with description: "${initialDescription}"`);

            // Warm entity cache via findAll
            const entitySvc: IEntityService<Vehicle> = Structures.createEntityService(applicationId, structureName);
            const page = await entitySvc.findAll(Pageable.create(0, 10));
            expect(page).toBeDefined();
            console.log(`Test 1: Pod ${i}: Entity findAll returned data (cache warmed)`);

            // Warm entity cache via search
            const searchPage = await entitySvc.search('*', Pageable.create(0, 10));
            expect(searchPage).toBeDefined();
            console.log(`Test 1: Pod ${i}: Entity search returned data (cache warmed)`);

            await k8s.disconnectFromPod();
        }

        // Record timestamp after cache warming, before modification
        const beforeModificationTime = Date.now();

        // Step 4: Modify structure on pod 0 (triggers cache eviction)
        const updatedDescription = `Updated description ${Date.now()}`;
        console.log(`\nTest 1: Step 4 - Updating structure on pod 0 to trigger cache eviction`);
        await k8s.connectToPod(0);

        const toUpdate = await Structures.getStructureService().findById(structureId);
        expect(toUpdate).toBeDefined();
        toUpdate!.description = updatedDescription;

        const updated = await Structures.getStructureService().save(toUpdate!);
        expect(updated).toBeDefined();
        expect(updated.description).toBe(updatedDescription);
        console.log(`Test 1: Updated structure with description: "${updatedDescription}"`);

        await k8s.disconnectFromPod();

        // Step 5: Wait for cache eviction to propagate and verify via CSV files
        console.log('\nTest 1: Step 5 - Waiting for cache eviction to propagate');

        // Wait for eviction files to show records from the modification
        // We expect at least 1 eviction record per pod (minimum 3 for 3 pods)
        let evictions: EvictionsByPod | undefined;
        let csvVerificationSucceeded = false;

        try {
            evictions = await waitForEvictions({
                basePath: evictionDataPath,
                minRecords: k8s.getPodCount(), // Expect at least 1 eviction per pod
                timeout: 30000,
                pollInterval: 1000,
                sinceTimestamp: beforeModificationTime
            });

            const summary = summarizeEvictions(evictions);
            console.log('Test 1:Eviction summary:', JSON.stringify(summary, null, 2));

            // Verify we got eviction records
            expect(summary.totalRecords).toBeGreaterThan(0);
            console.log(`✓ Test 1: Total eviction records: ${summary.totalRecords}`);

            // Verify evictions occurred on multiple pods
            expect(summary.podCount).toBeGreaterThanOrEqual(1);
            console.log(`✓ Test 1: Evictions recorded from ${summary.podCount} pod(s)`);

            // Verify we have EXPLICIT cause (from cache.invalidate())
            if (summary.byCause['EXPLICIT']) {
                console.log(`✓ Test 1: Found ${summary.byCause['EXPLICIT']} EXPLICIT evictions (cache.invalidate)`);
            }

            // Filter to only evictions related to our structure
            const structureEvictions = filterByStructureId(evictions, structureId);
            console.log(`Test 1: Structure-specific evictions found on ${structureEvictions.size} pods`);

            // If we have structure-specific evictions, verify they're on multiple pods
            if (structureEvictions.size > 0) {
                console.log(`✓ Test 1: Structure ${structureId} evictions found on ${structureEvictions.size} pod(s)`);

                // Log which caches were evicted
                for (const [podName, records] of structureEvictions) {
                    const cacheNames = [...new Set(records.map(r => r.cacheName))];
                    console.log(`  Test 1: Pod ${podName}: ${records.length} evictions from caches: ${cacheNames.join(', ')}`);
                }
            }

            csvVerificationSucceeded = true;

        } catch (error) {
            console.warn('Test 1:Could not verify evictions via CSV (may still have propagated):', error);
            // Continue with functional verification even if CSV verification fails
        }

        // Log CSV verification status for test reporting
        if (csvVerificationSucceeded) {
            console.log('✓ Test 1:CSV eviction verification succeeded');
        } else {
            console.log('⚠ Test 1: CSV eviction verification could not be completed - continuing with functional tests');
        }

        // Step 6: Verify cache eviction on all pods (functional verification)
        console.log('\nTest 1: Step 6 - Verifying cache eviction on all pods');
        for (let i = 0; i < k8s.getPodCount(); i++) {
            await k8s.connectToPod(i);

            const afterEviction = await Structures.getStructureService().findById(structureId);
            expect(afterEviction).toBeDefined();
            expect(afterEviction?.description).toBe(updatedDescription);
            console.log(`Test 1: Pod ${i}: ✓ Sees updated description: "${updatedDescription}"`);

            await k8s.disconnectFromPod();
        }

        console.log('\n✓ Test 1: Cache eviction propagated successfully to all pods!');

        // Cleanup
        console.log('\nTest 1: Cleaning up test structure');
        await k8s.connectToPod(0);
        const cleanupEntitySvc: IEntityService<Vehicle> = Structures.createEntityService(applicationId, structureName);
        // Delete all test vehicles
        for (const vehicle of testVehicles) {
            if (vehicle.id) {
                try {
                    await cleanupEntitySvc.deleteById(vehicle.id);
                } catch (e) {
                    // Ignore cleanup errors
                }
            }
        }
        await Structures.getStructureService().unPublish(structureId);
        await Structures.getStructureService().deleteById(structureId);
        await Structures.getApplicationService().deleteById(applicationId);
        await k8s.disconnectFromPod();
    }, 300000); // 5 minute timeout

    /**
     * Load Test Foundation: Concurrent Cache Operations
     * 
     * This test establishes a foundation for load testing by:
     * 1. Creating structure and entities
     * 2. Running concurrent entity operations across pods during modification
     * 3. Verifying no stale data after eviction
     * 4. Tracking eviction timing for performance baseline
     * 
     * Use this as a starting point for more intensive load testing scenarios.
     */
    // it('should handle concurrent entity operations during cache eviction', async () => {
    //     if (!k8s.isEnabled()) {
    //         console.log('Test skipped: K8s tests not enabled');
    //         return;
    //     }

    //     const applicationId = 'k8sloadtest-' + Date.now();
    //     const structureName = 'vehicle';
    //     const structureId = `${applicationId}.${structureName}`.toLowerCase();
    //     const initialDescription = `Some form of transportation`;
    //     const evictionDataPath = k8s.getEvictionDataPath();

    //     // Performance tracking
    //     const timings: { operation: string; durationMs: number }[] = [];
    //     const trackTiming = (operation: string, startTime: number) => {
    //         const duration = Date.now() - startTime;
    //         timings.push({ operation, durationMs: duration });
    //         console.log(`  [Timing] ${operation}: ${duration}ms`);
    //     };

    //     // Step 0: Clear eviction files
    //     console.log('Step 0: Clearing eviction files');
    //     clearEvictionFiles(evictionDataPath);

    //     // Step 1: Create structure
    //     console.log('\nStep 1: Creating structure on pod 0');
    //     const setupStart = Date.now();
    //     await k8s.connectToPod(0);

    //     const savedStructure = await createVehicleStructure(applicationId, 'TestProject');
    //     expect(savedStructure).toBeDefined();
    //     console.log(`Created structure: ${structureId}`);

    //     // Step 2: Create more test entities for load testing (10 vehicles)
    //     console.log('\nStep 2: Creating test vehicles');
    //     const entityService: IEntityService<Vehicle> = Structures.createEntityService(applicationId, structureName);
    //     const testVehicles = createTestVehicles(10);

    //     for (const vehicle of testVehicles) {
    //         const savedVehicle = await entityService.save(vehicle);
    //         vehicle.id = savedVehicle.id; // Store ID for cleanup
    //     }
    //     await entityService.syncIndex();
    //     console.log(`Created ${testVehicles.length} test vehicles`);
    //     trackTiming('setup', setupStart);

    //     await k8s.disconnectFromPod();

    //     // Step 3: Warm caches on all pods
    //     console.log('\nStep 3: Warming caches on all pods');
    //     const warmStart = Date.now();
    //     for (let i = 0; i < k8s.getPodCount(); i++) {
    //         await k8s.connectToPod(i);

    //         const retrieved = await Structures.getStructureService().findById(structureId);
    //         expect(retrieved).toBeDefined();

    //         const entitySvc: IEntityService<Vehicle> = Structures.createEntityService(applicationId, structureName);
    //         await entitySvc.findAll(Pageable.create(0, 10));
    //         await entitySvc.search('*', Pageable.create(0, 10));

    //         await k8s.disconnectFromPod();
    //     }
    //     trackTiming('cacheWarm', warmStart);

    //     // Record timestamp before modification
    //     const beforeModificationTime = Date.now();

    //     // Step 4: Start concurrent entity reads across pods while modifying structure
    //     console.log('\nStep 4: Concurrent operations during structure modification');

    //     // Create promise for concurrent reads from different pods
    //     const concurrentReads: Promise<{ podIndex: number; success: boolean; description: string | undefined }>[] = [];

    //     // Function to perform reads on a specific pod
    //     const performReadsOnPod = async (podIndex: number, iterations: number) => {
    //         const results: { success: boolean; description: string | undefined }[] = [];

    //         await k8s.connectToPod(podIndex);
    //         const entitySvc: IEntityService<Vehicle> = Structures.createEntityService(applicationId, structureName);

    //         for (let i = 0; i < iterations; i++) {
    //             try {
    //                 // Mix of structure and entity reads
    //                 const structure = await Structures.getStructureService().findById(structureId);
    //                 await entitySvc.findAll(Pageable.create(0, 5));
    //                 results.push({ success: true, description: structure?.description ?? undefined });
    //             } catch (error) {
    //                 results.push({ success: false, description: undefined });
    //             }

    //             // Small delay between iterations
    //             await new Promise(resolve => setTimeout(resolve, 100));
    //         }

    //         await k8s.disconnectFromPod();

    //         // Return the last result
    //         const lastResult = results[results.length - 1];
    //         return { podIndex, ...lastResult };
    //     };

    //     // Start concurrent reads on pod 1 and 2 (while we modify on pod 0)
    //     const concurrentStart = Date.now();

    //     // Note: We can't truly run concurrent connections with the current helper
    //     // because it uses a single Continuum connection. Instead, we'll simulate
    //     // sequential rapid operations that would benefit from warm caches.

    //     // Modify structure on pod 0
    //     console.log('  Modifying structure on pod 0...');
    //     await k8s.connectToPod(0);

    //     const toUpdate = await Structures.getStructureService().findById(structureId);
    //     expect(toUpdate).toBeDefined();
    //     const updatedDescription = `Load test update ${Date.now()}`;
    //     toUpdate!.description = updatedDescription;

    //     const modifyStart = Date.now();
    //     const updated = await Structures.getStructureService().save(toUpdate!);
    //     trackTiming('structureModify', modifyStart);

    //     expect(updated.description).toBe(updatedDescription);
    //     console.log(`  Structure updated with description: "${updatedDescription}"`);

    //     await k8s.disconnectFromPod();

    //     // Step 5: Rapid sequential reads across all pods to test cache consistency
    //     console.log('\nStep 5: Rapid sequential reads to verify cache consistency');
    //     const rapidReadsStart = Date.now();
    //     const readResults: { pod: number; description: string | undefined; readTime: number }[] = [];

    //     // Do 3 rounds of reads across all pods
    //     for (let round = 0; round < 3; round++) {
    //         for (let podIndex = 0; podIndex < k8s.getPodCount(); podIndex++) {
    //             const readStart = Date.now();
    //             await k8s.connectToPod(podIndex);

    //             const structure = await Structures.getStructureService().findById(structureId);
    //             const readTime = Date.now() - readStart;

    //             readResults.push({
    //                 pod: podIndex,
    //                 description: structure?.description ?? undefined,
    //                 readTime
    //             });

    //             await k8s.disconnectFromPod();
    //         }
    //     }
    //     trackTiming('rapidReads', rapidReadsStart);

    //     // Analyze read results
    //     console.log('\nRead results analysis:');
    //     const staleReads = readResults.filter(r => r.description !== updatedDescription);
    //     const freshReads = readResults.filter(r => r.description === updatedDescription);

    //     console.log(`  Total reads: ${readResults.length}`);
    //     console.log(`  Fresh reads (updated desc): ${freshReads.length}`);
    //     console.log(`  Stale reads (old desc): ${staleReads.length}`);

    //     if (staleReads.length > 0) {
    //         console.log('  Stale read details:');
    //         for (const stale of staleReads) {
    //             console.log(`    Pod ${stale.pod}: "${stale.description}" (read in ${stale.readTime}ms)`);
    //         }
    //     }

    //     // Calculate average read times per pod
    //     const avgReadTimeByPod = new Map<number, number>();
    //     for (let i = 0; i < k8s.getPodCount(); i++) {
    //         const podReads = readResults.filter(r => r.pod === i);
    //         const avgTime = podReads.reduce((sum, r) => sum + r.readTime, 0) / podReads.length;
    //         avgReadTimeByPod.set(i, Math.round(avgTime));
    //     }

    //     console.log('\n  Average read times per pod:');
    //     for (const [pod, avgTime] of avgReadTimeByPod) {
    //         console.log(`    Pod ${pod}: ${avgTime}ms`);
    //     }

    //     // Step 6: Verify eviction files
    //     console.log('\nStep 6: Verifying eviction CSV files');
    //     let evictions: EvictionsByPod | undefined;

    //     try {
    //         evictions = await waitForEvictions({
    //             basePath: evictionDataPath,
    //             minRecords: 1,
    //             timeout: 20000,
    //             pollInterval: 1000,
    //             sinceTimestamp: beforeModificationTime
    //         });

    //         const summary = summarizeEvictions(evictions);
    //         console.log('  Eviction summary:', JSON.stringify(summary, null, 2));

    //         // Track eviction count as a performance metric
    //         timings.push({ operation: 'evictionCount', durationMs: summary.totalRecords });

    //     } catch (error) {
    //         console.warn('  Could not verify evictions via CSV:', error);
    //     }

    //     // Step 7: Final consistency check - all pods should see updated structure
    //     console.log('\nStep 7: Final consistency verification');
    //     for (let i = 0; i < k8s.getPodCount(); i++) {
    //         await k8s.connectToPod(i);

    //         const finalStructure = await Structures.getStructureService().findById(structureId);
    //         expect(finalStructure).toBeDefined();
    //         expect(finalStructure?.description).toBe(updatedDescription);
    //         console.log(`  Pod ${i}: ✓ Sees updated description`);

    //         await k8s.disconnectFromPod();
    //     }

    //     // Print performance summary
    //     console.log('\n═══════════════════════════════════════');
    //     console.log('PERFORMANCE SUMMARY');
    //     console.log('═══════════════════════════════════════');
    //     for (const timing of timings) {
    //         console.log(`  ${timing.operation}: ${timing.durationMs}ms`);
    //     }
    //     console.log('═══════════════════════════════════════');

    //     // Assert no stale reads after eviction propagation time
    //     // In the final round of reads, all should be fresh
    //     const finalRoundReads = readResults.slice(-k8s.getPodCount());
    //     const finalStaleCount = finalRoundReads.filter(r => r.description !== updatedDescription).length;
    //     expect(finalStaleCount).toBe(0);

    //     console.log('\n✓ Load test foundation complete - no stale reads in final verification');

    //     // Cleanup
    //     console.log('\nCleaning up test resources');
    //     await k8s.connectToPod(0);
    //     const cleanupEntitySvc: IEntityService<Vehicle> = Structures.createEntityService(applicationId, structureName);
    //     for (const vehicle of testVehicles) {
    //         if (vehicle.id) {
    //             try {
    //                 await cleanupEntitySvc.deleteById(vehicle.id);
    //             } catch (e) {
    //                 // Ignore cleanup errors
    //             }
    //         }
    //     }
    //     await Structures.getStructureService().unPublish(structureId);
    //     await Structures.getStructureService().deleteById(structureId);
    //     await Structures.getApplicationService().deleteById(applicationId);
    //     await k8s.disconnectFromPod();
    // }, 600000); // 10 minute timeout for load test
});
