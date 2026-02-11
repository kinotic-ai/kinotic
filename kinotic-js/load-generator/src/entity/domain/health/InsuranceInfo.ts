export class InsuranceInfo {
    public providerName!: string
    public policyNumber!: string
    public groupNumber!: string
    public coverageStartDate!: Date
    public coverageEndDate!: Date
    public isPrimary!: boolean
    public copayAmount!: number
    public deductibleAmount!: number
    public outOfPocketMaximum!: number
    public isActive!: boolean

    protected constructor() {}

    static create(): InsuranceInfo {
        return new InsuranceInfo()
    }

    static builder(): InsuranceInfoBuilder {
        return new InsuranceInfoBuilder()
    }
}

export class InsuranceInfoBuilder {
    private insuranceInfo: InsuranceInfo = InsuranceInfo.create()

    withProviderName(providerName: string): InsuranceInfoBuilder {
        this.insuranceInfo.providerName = providerName
        return this
    }

    withPolicyNumber(policyNumber: string): InsuranceInfoBuilder {
        this.insuranceInfo.policyNumber = policyNumber
        return this
    }

    withGroupNumber(groupNumber: string): InsuranceInfoBuilder {
        this.insuranceInfo.groupNumber = groupNumber
        return this
    }

    withCoverageStartDate(coverageStartDate: Date): InsuranceInfoBuilder {
        this.insuranceInfo.coverageStartDate = coverageStartDate
        return this
    }

    withCoverageEndDate(coverageEndDate: Date): InsuranceInfoBuilder {
        this.insuranceInfo.coverageEndDate = coverageEndDate
        return this
    }

    withIsPrimary(isPrimary: boolean): InsuranceInfoBuilder {
        this.insuranceInfo.isPrimary = isPrimary
        return this
    }

    withCopayAmount(copayAmount: number): InsuranceInfoBuilder {
        this.insuranceInfo.copayAmount = copayAmount
        return this
    }

    withDeductibleAmount(deductibleAmount: number): InsuranceInfoBuilder {
        this.insuranceInfo.deductibleAmount = deductibleAmount
        return this
    }

    withOutOfPocketMaximum(outOfPocketMaximum: number): InsuranceInfoBuilder {
        this.insuranceInfo.outOfPocketMaximum = outOfPocketMaximum
        return this
    }

    withIsActive(isActive: boolean): InsuranceInfoBuilder {
        this.insuranceInfo.isActive = isActive
        return this
    }

    build(): InsuranceInfo {
        return this.insuranceInfo
    }
} 