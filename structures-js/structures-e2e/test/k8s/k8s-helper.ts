import { Continuum } from '@kinotic/continuum-client';
import { ChildProcess, execSync } from 'child_process';


export interface K8sConfig {
    enabled: boolean;
    context: string;
    namespace: string;
    labelSelector: string;
    replicaCount: number;
    stompPort: number;
    startingLocalPort: number;
    /** Path to eviction data directory (host mount from KinD) */
    evictionDataPath: string;
}

export interface PodInfo {
    name: string;
    localPort: number;
}

export class K8sTestHelper {
    private config: K8sConfig;
    private pods: PodInfo[] = [];
    private portForwardMap: Map<number, ChildProcess> = new Map();
    private connections: typeof Continuum[] = [];
    private currentPodIndex: number | null = null;

    constructor() {
        this.config = {
            enabled: process.env.K8S_TEST_ENABLED === 'true',
            context: process.env.K8S_CONTEXT || 'kind-structures-cluster',
            namespace: process.env.K8S_NAMESPACE || 'default',
            labelSelector: process.env.K8S_LABEL_SELECTOR || 'app=structures',
            replicaCount: parseInt(process.env.K8S_REPLICA_COUNT || '3'),
            stompPort: parseInt(process.env.K8S_STOMP_PORT || '58503'),
            startingLocalPort: parseInt(process.env.K8S_STARTING_LOCAL_PORT || '58511'),
            evictionDataPath: process.env.K8S_EVICTION_DATA_PATH || '../../dev-tools/kind/eviction-data'
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
     * Check if a port-forward process is still alive and functional
     */
    private isPortForwardAlive(podIndex: number): boolean {
        const proc = this.portForwardMap.get(podIndex);
        if (!proc) {
            console.log(`[port-forward-health] Pod ${podIndex}: No process found in map`);
            return false;
        }
        
        // Check if process has exited
        if (proc.exitCode !== null) {
            console.log(`[port-forward-health] Pod ${podIndex}: Process exited with code ${proc.exitCode}`);
            return false;
        }
        
        if (proc.killed) {
            console.log(`[port-forward-health] Pod ${podIndex}: Process was killed`);
            return false;
        }

        // Check if process PID is still valid (process is running)
        if (proc.pid) {
            try {
                // Sending signal 0 checks if process exists without actually sending a signal
                process.kill(proc.pid, 0);
                console.log(`[port-forward-health] Pod ${podIndex}: Process ${proc.pid} is alive`);
                return true;
            } catch (e) {
                console.log(`[port-forward-health] Pod ${podIndex}: Process ${proc.pid} is not running: ${e}`);
                return false;
            }
        }
        
        console.log(`[port-forward-health] Pod ${podIndex}: No PID available`);
        return false;
    }

    /**
     * Kill existing port-forward for a specific pod if one exists
     */
    private async killPortForward(podIndex: number): Promise<void> {
        const existingProc = this.portForwardMap.get(podIndex);
        if (existingProc) {
            const pod = this.pods[podIndex];
            console.log(`[port-forward] Killing existing port-forward for pod ${podIndex} (${pod?.name}), PID: ${existingProc.pid}`);
            try {
                existingProc.kill('SIGTERM');
                // Wait a bit for graceful shutdown
                await new Promise(resolve => setTimeout(resolve, 500));
                // Force kill if still running
                if (existingProc.exitCode === null) {
                    console.log(`[port-forward] Force killing port-forward for pod ${podIndex}`);
                    existingProc.kill('SIGKILL');
                }
            } catch (e) {
                console.log(`[port-forward] Error killing port-forward for pod ${podIndex}:`, e);
            }
            this.portForwardMap.delete(podIndex);
            // Wait for port to be released
            console.log(`[port-forward] Waiting 2s for port to be released...`);
            await new Promise(resolve => setTimeout(resolve, 2000));
        }
    }

    /**
     * Create a fresh port-forward for a specific pod
     * Always kills existing port-forward first to ensure clean state
     */
    private async ensurePortForward(podIndex: number): Promise<void> {
        const pod = this.pods[podIndex];
        if (!pod) {
            throw new Error(`Pod index ${podIndex} out of range`);
        }

        // ALWAYS kill existing port-forward and create fresh one
        // This avoids any stale tunnel issues
        await this.killPortForward(podIndex);

        console.log(`[port-forward] Starting FRESH: ${pod.name} -> localhost:${pod.localPort}`);

        const { spawn } = await import('child_process');
        const proc = spawn('kubectl', [
            '--context', this.config.context,
            '-n', this.config.namespace,
            'port-forward',
            `pod/${pod.name}`,
            `${pod.localPort}:${this.config.stompPort}`
        ]);

        proc.stdout?.on('data', (data) => {
            console.log(`[port-forward ${pod.name}] stdout: ${data.toString().trim()}`);
        });

        proc.stderr?.on('data', (data) => {
            console.error(`[port-forward ${pod.name}] stderr: ${data.toString().trim()}`);
        });

        proc.on('error', (error) => {
            console.error(`[port-forward ${pod.name}] error event:`, error);
        });

        proc.on('exit', (code, signal) => {
            console.warn(`[port-forward ${pod.name}] EXIT: code=${code}, signal=${signal}`);
        });

        proc.on('close', (code, signal) => {
            console.warn(`[port-forward ${pod.name}] CLOSE: code=${code}, signal=${signal}`);
        });

        // Store the process in the map
        this.portForwardMap.set(podIndex, proc);
        console.log(`[port-forward] Process started for ${pod.name}, PID: ${proc.pid}`);

        // Wait for port-forward to be ready (but skip the validation connect/disconnect)
        console.log(`[port-forward] Waiting 3s for port-forward to establish...`);
        await new Promise(resolve => setTimeout(resolve, 3000));
        
        console.log(`[port-forward] Ready for ${pod.name}`);
    }

    /**
     * Wait for a specific port-forward to be ready by checking connectivity
     */
    private async waitForPortForwardReady(pod: PodInfo): Promise<void> {
        const maxAttempts = 30;
        const delayMs = 1000;

        console.log(`[port-forward-validate] Waiting for port-forward to ${pod.name} to be ready...`);

        for (let attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                console.log(`[port-forward-validate] Attempt ${attempt}/${maxAttempts}: Testing connectivity to ${pod.name}...`);
                
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

                console.log(`[port-forward-validate] Connected to ${pod.name} for validation:`, JSON.stringify(connectedInfo, null, 2));

                await Continuum.disconnect(true);
                console.log(`[port-forward-validate] Disconnected from ${pod.name} after validation`);
                
                // Add delay after validation disconnect to let websocket fully close
                const postValidationDelay = 500;
                console.log(`[port-forward-validate] Waiting ${postValidationDelay}ms after validation disconnect...`);
                await new Promise(resolve => setTimeout(resolve, postValidationDelay));
                
                // Port-forward is ready
                console.log(`[port-forward-validate] Port-forward to ${pod.name} is ready`);
                return;
            } catch (error) {
                console.error(`[port-forward-validate] Attempt ${attempt}/${maxAttempts}: Error connecting to pod ${pod.name}:`, error);
                
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

        console.log(`[connect] Connecting to pod ${podIndex} (${pod.name})...`);
        const connectStartTime = Date.now();

        // Create fresh port-forward (kills any existing one first)
        await this.ensurePortForward(podIndex);

        console.log(`[connect] Port-forward ready, initiating STOMP connection to localhost:${pod.localPort}`);
        
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

        // Track which pod we're connected to
        this.currentPodIndex = podIndex;

        const connectDuration = Date.now() - connectStartTime;
        console.log(`[connect] Connected to pod ${podIndex} (${pod.name}) on localhost:${pod.localPort} in ${connectDuration}ms`);
        console.log(`[connect] Connection info:`, JSON.stringify(connectedInfo, null, 2));
    }

    /**
     * Disconnect from current pod and kill its port-forward
     */
    async disconnectFromPod(): Promise<void> {
        const podIndex = this.currentPodIndex;
        const pod = podIndex !== null ? this.pods[podIndex] : null;
        
        console.log(`[disconnect] Disconnecting from pod ${podIndex} (${pod?.name ?? 'unknown'})...`);
        const disconnectStartTime = Date.now();
        
        // First disconnect STOMP
        await Continuum.disconnect(true);
        console.log(`[disconnect] STOMP disconnected`);
        
        // Then kill the port-forward to ensure completely clean state
        if (podIndex !== null) {
            await this.killPortForward(podIndex);
        }
        
        this.currentPodIndex = null;
        
        const disconnectDuration = Date.now() - disconnectStartTime;
        console.log(`[disconnect] Disconnect complete (total time: ${disconnectDuration}ms)`);
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

    /**
     * Get the names of all discovered pods
     */
    getPodNames(): string[] {
        return this.pods.map(p => p.name);
    }

    /**
     * Get the path to eviction data directory (resolved relative to test file)
     */
    getEvictionDataPath(): string {
        // Resolve relative to this test file's directory
        const path = require('path');
        return path.resolve(__dirname, this.config.evictionDataPath);
    }

    /**
     * Get pod info by index
     */
    getPod(index: number): PodInfo | undefined {
        return this.pods[index];
    }

    /**
     * Check Ignite cluster topology via kubectl logs
     * Returns the number of nodes in the cluster according to Ignite
     */
    async getIgniteClusterSize(): Promise<number> {
        try {
            const pod = this.pods[0];
            if (!pod) {
                throw new Error('No pods discovered');
            }

            const output = execSync(
                `kubectl --context ${this.config.context} -n ${this.config.namespace} logs ${pod.name} --tail=100 | grep -o "servers=[0-9]*" | tail -1`,
                { encoding: 'utf-8', timeout: 30000, stdio: ['pipe', 'pipe', 'pipe'] }
            );

            const match = output.trim().match(/servers=(\d+)/);
            if (match) {
                return parseInt(match[1], 10);
            }
            return 0;
        } catch (error) {
            console.warn('Could not determine Ignite cluster size:', error);
            return 0;
        }
    }

    /**
     * Wait for Ignite cluster to have expected number of nodes
     */
    async waitForClusterTopology(expectedNodes: number, timeoutMs: number = 60000): Promise<boolean> {
        const startTime = Date.now();
        
        while (Date.now() - startTime < timeoutMs) {
            const clusterSize = await this.getIgniteClusterSize();
            if (clusterSize >= expectedNodes) {
                console.log(`Ignite cluster has ${clusterSize} nodes (expected: ${expectedNodes})`);
                return true;
            }
            console.log(`Waiting for Ignite cluster: ${clusterSize}/${expectedNodes} nodes...`);
            await new Promise(resolve => setTimeout(resolve, 5000));
        }
        
        console.warn(`Timeout waiting for Ignite cluster to reach ${expectedNodes} nodes`);
        return false;
    }
}