export class LoadTestConfig {
    public beginTenantIdNumber: number
    public numberOfTenants: number
    public testName: string

    constructor(beginTenantIdNumber: number, numberOfTenants: number, testName: string) {
        this.beginTenantIdNumber = beginTenantIdNumber
        this.numberOfTenants = numberOfTenants
        this.testName = testName
    }

    public static fromEnv(): LoadTestConfig {
        const beginTenantIdNumber = parseInt(process.env.BEGIN_TENANT_ID_NUMBER || '0')
        const numberOfTenants = parseInt(process.env.NUMBER_OF_TENANTS || '1')
        if (numberOfTenants <= 0) {
            throw new Error('NUMBER_OF_TENANTS must be greater than 0')
        }
        const testName = process.env.TEST_NAME
        if (!testName) {
            throw new Error('TEST_NAME environment variable is required')
        }

        return new LoadTestConfig(beginTenantIdNumber, numberOfTenants, testName)
    }

    public print(): void {
        console.log(`BEGIN_TENANT_ID_NUMBER=${this.beginTenantIdNumber}`)
        console.log(`NUMBER_OF_TENANTS=${this.numberOfTenants}`)
        console.log(`TEST_NAME=${this.testName}`)
    }

}
