import {describe, expect, it} from 'vitest'
import {AuthzCheck, AuthzResource, Context, ServiceIdentifier, serviceContractOf} from '../src'

@AuthzResource({value: 'report', parent: 'tenant', roles: [{id: 'report.generator', permissions: ['can_generate']}]})
class ReportService {

    findReports(): Promise<string[]> {
        return Promise.resolve([])
    }

    @AuthzCheck({permission: 'can_generate'})
    generate(reportId: string, options = {draft: false}): Promise<void> {
        return Promise.resolve(void options)
    }

    @AuthzCheck({zoneOnly: true})
    ping(): Promise<string> {
        return Promise.resolve('pong')
    }

    @Context
    async describe(reportId: string, context: Record<string, unknown>): Promise<string> {
        return `${reportId} for ${context.participant}`
    }

    archive({reason}: {reason: string}, ...tags: string[]): Promise<void> {
        return Promise.resolve(void [reason, tags])
    }
}

class PlainService {
    findAll(): Promise<string[]> {
        return Promise.resolve([])
    }
}

class SpecialReportService extends ReportService {
    override findReports(): Promise<string[]> {
        return Promise.resolve(['special'])
    }

    summarize(reportId: string): Promise<string> {
        return Promise.resolve(reportId)
    }
}

function identifier(zone: string | undefined): ServiceIdentifier {
    const ret = new ServiceIdentifier('com.acme.reports', 'ReportService', zone)
    ret.version = '1.2.0'
    return ret
}

/**
 * Pins what a runtime publishes for a checked service: the resource it declares, every method of its prototype
 * chain with the parameter names a request carries, read from the method's source, the checks declared, and
 * nothing for a class declaring no resource.
 */
describe('Kinotic JS', () => {
    describe('packages/core', () => {
        describe('Service contracts', () => {

            it('builds the contract of a resource service from its declarations and methods', () => {
                const contract = serviceContractOf(new ReportService(), identifier('app.acme.crm'))!

                expect(contract.namespace).toBe('com.acme.reports')
                expect(contract.name).toBe('ReportService')
                expect(contract.version).toBe('1.2.0')
                expect(contract.zone).toBe('app.acme.crm')
                expect(contract.resource).toEqual({value: 'report', parent: 'tenant', roles: [{id: 'report.generator', permissions: ['can_generate']}]})
                const functions = Object.fromEntries(contract.functions.map(f => [f.name, f]))
                expect(Object.keys(functions).sort()).toEqual(['archive', 'describe', 'findReports', 'generate', 'ping'])
                expect(functions.findReports).toEqual({name: 'findReports', parameters: [], check: null})
                // a parameter with a default keeps its name
                expect(functions.generate).toEqual({name: 'generate', parameters: ['reportId', 'options'], check: {permission: 'can_generate'}})
                expect(functions.ping.check).toEqual({zoneOnly: true})
                // the context a @Context method takes last is the platform's, not the caller's
                expect(functions.describe.parameters).toEqual(['reportId'])
                // a destructured parameter has no name of its own, a rest parameter keeps its name
                expect(functions.archive.parameters).toEqual(['arg0', 'tags'])
            })

            it('carries the methods of a subclass and its parents once each', () => {
                const contract = serviceContractOf(new SpecialReportService(), identifier('app.acme.crm'))!

                const names = contract.functions.map(f => f.name)
                expect(names.filter(n => n === 'findReports')).toHaveLength(1)
                expect(names).toContain('summarize')
                expect(names).toContain('generate')
            })

            it('builds nothing for a class declaring no resource', () => {
                expect(serviceContractOf(new PlainService(), identifier('app.acme.crm'))).toBeNull()
            })

            it('refuses a resource service registered in no zone', () => {
                expect(() => serviceContractOf(new ReportService(), identifier(undefined))).toThrowError(/zone/)
            })

            it('takes a bare type as the resource declaration', () => {
                @AuthzResource('invoice')
                class InvoiceService {
                    findInvoices(): Promise<string[]> {
                        return Promise.resolve([])
                    }
                }
                expect(serviceContractOf(new InvoiceService(), identifier('app.acme.crm'))!.resource).toEqual({value: 'invoice'})
                expect(() => AuthzResource('')).toThrowError(/resource type/)
            })
        })
    })
})
