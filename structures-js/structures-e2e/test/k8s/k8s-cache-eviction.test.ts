import { Continuum } from '@kinotic/continuum-client';
import { Structures } from '@kinotic/structures-api';
import { WebSocket } from 'ws';
import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { execSync } from 'child_process';
import { createVehicleStructure } from '../TestHelpers';

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

interface K8sConfig {
    enabled: boolean;
    context: string;
    namespace: string;
    labelSelector: string;
    replicaCount: number;
    stompPort: number;
    startingLocalPort: number;
}

interface PodInfo {
    name: string;
    localPort: number;
}

class K8sTestHelper {
    private config: K8sConfig;
    private pods: PodInfo[] = [];
    private portForwardMap: Map<number, any> = new Map();
    private connections: typeof Continuum[] = [];

    constructor() {
        this.config = {
            enabled: process.env.K8S_TEST_ENABLED === 'true',
            context: process.env.K8S_CONTEXT || 'kind-structures-cluster',
            namespace: process.env.K8S_NAMESPACE || 'default',
            labelSelector: process.env.K8S_LABEL_SELECTOR || 'app=structures',
            replicaCount: parseInt(process.env.K8S_REPLICA_COUNT || '3'),
            stompPort: parseInt(process.env.K8S_STOMP_PORT || '58503'),
            startingLocalPort: parseInt(process.env.K8S_STARTING_LOCAL_PORT || '58511')
        };
    }

    isEnabled(): boolean {
        return this.config.enabled;
    }

    /**
     * Check if cluster is accessible
     */
    async isClusterAccessible(): Promise<boolean> {
        try {
            execSync(`kubectl --context ${this.config.context} cluster-info`, { 
                stdio: 'pipe',
                timeout: 10000 
            });
            return true;
        } catch (error) {
            console.error('Cluster not accessible:', error);
            return false;
        }
    }

    /**
     * Discover pod names
     */
    async discoverPods(): Promise<PodInfo[]> {
        try {
            const output = execSync(
                `kubectl --context ${this.config.context} -n ${this.config.namespace} get pods -l ${this.config.labelSelector} -o jsonpath='{.items[*].metadata.name}'`,
                { encoding: 'utf-8', timeout: 30000 }
            );

            const podNames = output.trim().split(/\s+/).filter(name => name.length > 0);
            
            if (podNames.length < this.config.replicaCount) {
                throw new Error(`Expected ${this.config.replicaCount} pods but found ${podNames.length}`);
            }

            this.pods = podNames.map((name, index) => ({
                name,
                localPort: this.config.startingLocalPort + index
            }));

            console.log(`Discovered ${this.pods.length} pods:`, this.pods);
            return this.pods;
        } catch (error) {
            throw new Error(`Failed to discover pods: ${error}`);
        }
    }

    /**
     * Ensure port-forward exists for a specific pod
     * Creates it on-demand if it doesn't exist
     */
    private async ensurePortForward(podIndex: number): Promise<void> {
        // Check if port-forward already exists for this pod
        if (this.portForwardMap.has(podIndex)) {
            console.log(`Port-forward already exists for pod ${podIndex}`);
            return;
        }

        const pod = this.pods[podIndex];
        if (!pod) {
            throw new Error(`Pod index ${podIndex} out of range`);
        }

        console.log(`Starting port-forward: ${pod.name} -> localhost:${pod.localPort}`);

        const { spawn } = await import('child_process');
        const process = spawn('kubectl', [
            '--context', this.config.context,
            '-n', this.config.namespace,
            'port-forward',
            `pod/${pod.name}`,
            `${pod.localPort}:${this.config.stompPort}`
        ]);

        process.stdout?.on('data', (data) => {
            console.log(`[port-forward ${pod.name}] ${data}`);
        });

        process.stderr?.on('data', (data) => {
            console.error(`[port-forward ${pod.name}] ${data}`);
        });

        process.on('error', (error) => {
            console.error(`Port-forward error for ${pod.name}:`, error);
        });

        // Store the process in the map
        this.portForwardMap.set(podIndex, process);

        // Wait for port-forward to be ready
        await this.waitForPortForwardReady(pod);
        
        // Add 5-second delay after successful port-forward creation
        console.log(`Port-forward ready for ${pod.name}, waiting 5 seconds for stabilization...`);
        await new Promise(resolve => setTimeout(resolve, 5000));
        console.log(`Port-forward fully ready for ${pod.name}`);
    }

    /**
     * Wait for a specific port-forward to be ready by checking connectivity
     */
    private async waitForPortForwardReady(pod: PodInfo): Promise<void> {
        const maxAttempts = 30;
        const delayMs = 1000;

        for (let attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Try to connect briefly to test connectivity
                const connectedInfo = await Continuum.connect({
                    host: 'localhost',
                    port: pod.localPort,
                    useSSL: false,
                    maxConnectionAttempts: 5,
                    disableStickySession: false,
                    connectHeaders       : {
                        login   : 'admin',
                        passcode: 'structures',
                        // tenantId: 'kinotic'
                    }
                });

                console.log('Connected info:', connectedInfo);

                console.log(`Connected to pod ${pod.name} for validation`);

                // i think the problem here is that the cluster cannot communicate properly with other pods. 
                // what we need to do is remove possible culprits, like the invokation service 
                // we know a single pod works but when we scale out it fails - we can also try two nodes to see if we have issues there as well 

                // The other issue is that since all pods are gateways they all try and send requests out - since there are more than one pod 
                // we should see if we can tell the services to prefer handling requests locally - since k8s will manage the round robin of requests to the pods.


                await Continuum.disconnect();
                console.log(`Disconnected from pod ${pod.name} after validation`);
                
                // Port-forward is ready
                return;
            } catch (error) {
                console.error(`Attempt ${attempt}/${maxAttempts}: Error connecting to pod ${pod.name}:`, error);
                
                if (attempt < maxAttempts) {
                    await new Promise(resolve => setTimeout(resolve, delayMs));
                }
            }
        }

        throw new Error(`Port-forward for ${pod.name} did not become ready in time`);
    }

    /**
     * Connect to a specific pod
     */
    async connectToPod(podIndex: number): Promise<void> {
        const pod = this.pods[podIndex];
        if (!pod) {
            throw new Error(`Pod index ${podIndex} out of range`);
        }

        // Ensure port-forward exists and is ready
        await this.ensurePortForward(podIndex);

        await Continuum.connect({
            host: 'localhost',
            port: pod.localPort,
            useSSL: false,
            maxConnectionAttempts: 5,
            disableStickySession: false,
            connectHeaders       : {
                login   : 'admin',
                passcode: 'structures',
                // tenantId: 'kinotic'
            }
        });

        console.log(`Connected to pod ${podIndex} (${pod.name}) on localhost:${pod.localPort}`);
    }

    /**
     * Disconnect from current pod
     */
    async disconnectFromPod(): Promise<void> {
        await Continuum.disconnect();
    }

    /**
     * Stop all port-forwards
     */
    async stopPortForwards(): Promise<void> {
        console.log('Stopping port-forwards...');
        for (const [podIndex, process] of this.portForwardMap.entries()) {
            console.log(`Stopping port-forward for pod ${podIndex}`);
            process.kill();
        }
        this.portForwardMap.clear();
    }

    getPodCount(): number {
        return this.pods.length;
    }
}

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

    it('should propagate cache eviction across all pods', async () => {
        if (!k8s.isEnabled()) {
            console.log('Test skipped: K8s tests not enabled');
            return;
        }

        const applicationId = 'k8stest-'+Date.now();
        const structureName = 'vehicle';
        const structureId = `${applicationId}.${structureName}`.toLowerCase();
        const initialDescription = `Some form of transportation`;

        // Step 1: Connect to pod 0 and create structure
        console.log('Step 1: Creating structure on pod 0');
        await k8s.connectToPod(0);

        console.log('Connected to pod 0');

        const savedStructure = await createVehicleStructure(applicationId, 'TestProject');

        console.log('Saved structure', savedStructure);

        expect(savedStructure).toBeDefined();
        expect(savedStructure.id).toBe(structureId);
        expect(savedStructure.description).toBe(initialDescription);
        console.log(`Created and Published structure: ${structureId}`);

        await k8s.disconnectFromPod();

        // Step 2: Connect to each pod and verify structure exists (warms cache)
        console.log('\nStep 2: Warming caches on all pods');
        for (let i = 0; i < k8s.getPodCount(); i++) {
            await k8s.connectToPod(i);
            
            const retrieved = await Structures.getStructureService().findById(structureId);
            expect(retrieved).toBeDefined();
            expect(retrieved?.description).toBe(initialDescription);
            console.log(`Pod ${i}: Structure cached with description: "${initialDescription}"`);
            
            await k8s.disconnectFromPod();
        }

        // Step 3: Modify structure on pod 0 (triggers cache eviction)
        const updatedDescription = `Updated description ${Date.now()}`;
        console.log(`\nStep 3: Updating structure on pod 0 to trigger cache eviction`);
        await k8s.connectToPod(0);

        const toUpdate = await Structures.getStructureService().findById(structureId);
        expect(toUpdate).toBeDefined();
        toUpdate!.description = updatedDescription;
        
        const updated = await Structures.getStructureService().save(toUpdate!);
        expect(updated).toBeDefined();
        expect(updated.description).toBe(updatedDescription);
        console.log(`Updated structure with description: "${updatedDescription}"`);

        await k8s.disconnectFromPod();

        // Step 4: Wait for cache eviction to propagate
        console.log('\nStep 4: Waiting for cache eviction to propagate (15 seconds)');
        await new Promise(resolve => setTimeout(resolve, 15000));

        // Step 5: Verify cache eviction on all pods
        console.log('\nStep 5: Verifying cache eviction on all pods');
        for (let i = 0; i < k8s.getPodCount(); i++) {
            await k8s.connectToPod(i);
            
            const afterEviction = await Structures.getStructureService().findById(structureId);
            expect(afterEviction).toBeDefined();
            expect(afterEviction?.description).toBe(updatedDescription);
            console.log(`Pod ${i}: ✓ Sees updated description: "${updatedDescription}"`);
            
            await k8s.disconnectFromPod();
        }

        console.log('\n✓ Cache eviction propagated successfully to all pods!');

        // Cleanup
        console.log('\nCleaning up test structure');
        await k8s.connectToPod(0);
        await Structures.getStructureService().unPublish(structureId);
        await Structures.getStructureService().deleteById(structureId);
        // await Structures.getProjectService().deleteById(project.id!);
        await Structures.getApplicationService().deleteById(applicationId);
        await k8s.disconnectFromPod();
    }, 180000); // 3 minute timeout
});
