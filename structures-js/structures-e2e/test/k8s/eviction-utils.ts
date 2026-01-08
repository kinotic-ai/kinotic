/**
 * Utilities for parsing and verifying cache eviction CSV files.
 * 
 * These files are written by EvictionEventRecorder in structures-auth
 * and contain records of all cache removals (explicit, expired, size, replaced).
 * 
 * CSV format: timestamp,cacheName,key,cause
 * Example: 1704384000000,structureCache,myapp.vehicle,EXPLICIT
 */

import * as fs from 'fs';
import * as path from 'path';

/**
 * Represents a single eviction record from the CSV file.
 */
export interface EvictionRecord {
    /** Unix timestamp in milliseconds when the eviction occurred */
    timestamp: number;
    /** Name of the Caffeine cache that was evicted from */
    cacheName: string;
    /** The cache key that was evicted */
    key: string;
    /** 
     * The cause of removal:
     * - EXPLICIT: cache.invalidate() was called
     * - REPLACED: entry was replaced with a new value
     * - EXPIRED: entry expired (TTL or access-based)
     * - SIZE: evicted due to cache size limits
     * - COLLECTED: garbage collected (weak/soft references)
     */
    cause: string;
}

/**
 * Result of reading eviction files, keyed by pod name.
 */
export type EvictionsByPod = Map<string, EvictionRecord[]>;

/**
 * Options for waiting for eviction records.
 */
export interface WaitForEvictionsOptions {
    /** Base path to the eviction data directory */
    basePath: string;
    /** Minimum number of total records expected */
    minRecords: number;
    /** Timeout in milliseconds */
    timeout: number;
    /** Poll interval in milliseconds (default: 500) */
    pollInterval?: number;
    /** Optional filter to match specific records */
    filter?: (record: EvictionRecord) => boolean;
    /** Optional: minimum timestamp to consider (records before this are ignored) */
    sinceTimestamp?: number;
}

/**
 * Parses a CSV content string into eviction records.
 * 
 * @param content - The raw CSV content
 * @returns Array of parsed eviction records
 */
export function parseEvictionCsv(content: string): EvictionRecord[] {
    const records: EvictionRecord[] = [];
    const lines = content.split('\n').filter(line => line.trim().length > 0);
    
    for (const line of lines) {
        const parts = line.split(',');
        if (parts.length >= 4) {
            const timestamp = parseInt(parts[0], 10);
            if (!isNaN(timestamp)) {
                records.push({
                    timestamp,
                    cacheName: parts[1],
                    key: parts[2],
                    cause: parts[3].trim()
                });
            }
        }
    }
    
    return records;
}

/**
 * Extracts the pod name from an eviction CSV filename.
 * Expected format: evictions-{podName}.csv
 * 
 * @param filename - The filename to parse
 * @returns The pod name or null if not matching expected format
 */
export function extractPodNameFromFilename(filename: string): string | null {
    const match = filename.match(/^evictions-(.+)\.csv$/);
    return match ? match[1] : null;
}

/**
 * Reads all eviction CSV files from the specified directory.
 * 
 * @param basePath - Path to the directory containing eviction CSV files
 * @returns Map of pod name to eviction records
 */
export function readEvictionFiles(basePath: string): EvictionsByPod {
    const result: EvictionsByPod = new Map();
    
    if (!fs.existsSync(basePath)) {
        console.warn(`Eviction data path does not exist: ${basePath}`);
        return result;
    }
    
    const files = fs.readdirSync(basePath);
    
    for (const file of files) {
        if (file.startsWith('evictions-') && file.endsWith('.csv')) {
            const podName = extractPodNameFromFilename(file);
            if (podName) {
                const filePath = path.join(basePath, file);
                try {
                    const content = fs.readFileSync(filePath, 'utf-8');
                    const records = parseEvictionCsv(content);
                    result.set(podName, records);
                    console.log(`Read ${records.length} eviction records from ${file}`);
                } catch (error) {
                    console.error(`Failed to read eviction file ${filePath}:`, error);
                }
            }
        }
    }
    
    return result;
}

/**
 * Waits for eviction records to appear, polling until criteria are met or timeout.
 * 
 * @param opts - Options for waiting
 * @returns Map of pod name to matching eviction records
 * @throws Error if timeout is reached before criteria are met
 */
export async function waitForEvictions(opts: WaitForEvictionsOptions): Promise<EvictionsByPod> {
    const { 
        basePath, 
        minRecords, 
        timeout, 
        pollInterval = 500, 
        filter,
        sinceTimestamp 
    } = opts;
    
    const startTime = Date.now();
    let lastTotalRecords = 0;
    
    while (Date.now() - startTime < timeout) {
        const allRecords = readEvictionFiles(basePath);
        
        // Apply filters and count
        let totalMatchingRecords = 0;
        const filteredResult: EvictionsByPod = new Map();
        
        for (const [podName, records] of allRecords) {
            let filtered = records;
            
            // Filter by timestamp if specified
            if (sinceTimestamp !== undefined) {
                filtered = filtered.filter(r => r.timestamp >= sinceTimestamp);
            }
            
            // Apply custom filter if specified
            if (filter) {
                filtered = filtered.filter(filter);
            }
            
            filteredResult.set(podName, filtered);
            totalMatchingRecords += filtered.length;
        }
        
        // Log progress if record count changed
        if (totalMatchingRecords !== lastTotalRecords) {
            console.log(`Eviction records found: ${totalMatchingRecords}/${minRecords} (pods: ${filteredResult.size})`);
            lastTotalRecords = totalMatchingRecords;
        }
        
        if (totalMatchingRecords >= minRecords) {
            return filteredResult;
        }
        
        await new Promise(resolve => setTimeout(resolve, pollInterval));
    }
    
    // Timeout reached - return what we have but log a warning
    const finalRecords = readEvictionFiles(basePath);
    let totalFound = 0;
    for (const records of finalRecords.values()) {
        totalFound += records.length;
    }
    
    throw new Error(
        `Timeout waiting for evictions. Expected ${minRecords} records, found ${totalFound} after ${timeout}ms`
    );
}

/**
 * Clears all eviction CSV files from the specified directory.
 * Useful for ensuring clean state before a test.
 * 
 * @param basePath - Path to the directory containing eviction CSV files
 */
export function clearEvictionFiles(basePath: string): void {
    if (!fs.existsSync(basePath)) {
        console.log(`Creating eviction data directory: ${basePath}`);
        fs.mkdirSync(basePath, { recursive: true });
        return;
    }
    
    const files = fs.readdirSync(basePath);
    let cleared = 0;
    
    for (const file of files) {
        if (file.startsWith('evictions-') && file.endsWith('.csv')) {
            const filePath = path.join(basePath, file);
            try {
                fs.unlinkSync(filePath);
                cleared++;
            } catch (error) {
                console.error(`Failed to delete eviction file ${filePath}:`, error);
            }
        }
    }
    
    console.log(`Cleared ${cleared} eviction file(s) from ${basePath}`);
}

/**
 * Gets a summary of eviction records grouped by cache name and cause.
 * Useful for debugging and test assertions.
 * 
 * @param evictions - Map of evictions by pod
 * @returns Summary object with counts by cache and cause
 */
export function summarizeEvictions(evictions: EvictionsByPod): {
    totalRecords: number;
    podCount: number;
    byCacheName: Record<string, number>;
    byCause: Record<string, number>;
    byPod: Record<string, number>;
} {
    const summary = {
        totalRecords: 0,
        podCount: evictions.size,
        byCacheName: {} as Record<string, number>,
        byCause: {} as Record<string, number>,
        byPod: {} as Record<string, number>
    };
    
    for (const [podName, records] of evictions) {
        summary.byPod[podName] = records.length;
        summary.totalRecords += records.length;
        
        for (const record of records) {
            summary.byCacheName[record.cacheName] = (summary.byCacheName[record.cacheName] || 0) + 1;
            summary.byCause[record.cause] = (summary.byCause[record.cause] || 0) + 1;
        }
    }
    
    return summary;
}

/**
 * Filters eviction records to only include those related to a specific structure.
 * 
 * @param evictions - Map of evictions by pod
 * @param structureId - The structure ID to filter by (checked against cache key)
 * @returns Filtered evictions
 */
export function filterByStructureId(evictions: EvictionsByPod, structureId: string): EvictionsByPod {
    const result: EvictionsByPod = new Map();
    const lowerId = structureId.toLowerCase();
    
    for (const [podName, records] of evictions) {
        const filtered = records.filter(r => 
            r.key.toLowerCase().includes(lowerId)
        );
        if (filtered.length > 0) {
            result.set(podName, filtered);
        }
    }
    
    return result;
}

/**
 * Asserts that evictions occurred on a minimum number of pods.
 * 
 * @param evictions - Map of evictions by pod
 * @param minPods - Minimum number of pods that should have evictions
 * @param description - Description for error messages
 * @throws Error if assertion fails
 */
export function assertEvictionsOnPods(
    evictions: EvictionsByPod, 
    minPods: number, 
    description: string = 'evictions'
): void {
    const podsWithEvictions = Array.from(evictions.entries())
        .filter(([_, records]) => records.length > 0)
        .map(([podName]) => podName);
    
    if (podsWithEvictions.length < minPods) {
        const summary = summarizeEvictions(evictions);
        throw new Error(
            `Expected ${description} on at least ${minPods} pods, but only found on ${podsWithEvictions.length}: [${podsWithEvictions.join(', ')}]\n` +
            `Summary: ${JSON.stringify(summary, null, 2)}`
        );
    }
}

