export class ConcurrencyConfig {
    public maxConcurrentRequests: number
    public maxRequestsPerSecond: number

    constructor(maxConcurrentRequests: number,
                maxRequestsPerSecond: number) {
        this.maxConcurrentRequests = maxConcurrentRequests
        this.maxRequestsPerSecond = maxRequestsPerSecond
    }

    public static fromEnv(): ConcurrencyConfig {
        const maxConcurrentRequests = parseInt(process.env.MAX_CONCURRENT_REQUESTS || '1')
        if (maxConcurrentRequests <= 0) {
            throw new Error('MAX_CONCURRENT_REQUESTS must be greater than 0')
        }
        const maxRequestsPerSecond = parseInt(process.env.MAX_REQUESTS_PER_SECOND || '10')
        if (maxRequestsPerSecond <= 0) {
            throw new Error('MAX_REQUESTS_PER_SECOND must be greater than 0')
        }

        return new ConcurrencyConfig(maxConcurrentRequests, maxRequestsPerSecond)
    }

    public print(): void {
        console.log(`MAX_CONCURRENT_REQUESTS=${this.maxConcurrentRequests}`)
        console.log(`MAX_REQUESTS_PER_SECOND=${this.maxRequestsPerSecond}`)
    }
}
