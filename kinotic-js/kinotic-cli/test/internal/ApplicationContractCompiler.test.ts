import {expect} from 'chai'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import {RequirePermissionDecorator} from '@kinotic-ai/idl'
import {ApplicationContractCompiler} from '../../src/internal/ApplicationContractCompiler.js'

describe('ApplicationContractCompiler', () => {
    let originalDirectory: string
    let directory: string

    beforeEach(() => {
        originalDirectory = process.cwd()
        directory = fs.mkdtempSync(path.join(os.tmpdir(), 'kinotic-access-contract-'))
        fs.mkdirSync(path.join(directory, 'src'))
        fs.writeFileSync(path.join(directory, 'tsconfig.json'), JSON.stringify({
            compilerOptions: {target: 'esnext', module: 'ES2020', moduleResolution: 'bundler'},
            files: []
        }))
        process.chdir(directory)
    })

    afterEach(() => {
        process.chdir(originalDirectory)
        fs.rmSync(directory, {recursive: true, force: true})
    })

    function source(body: string): void {
        fs.writeFileSync(path.join(directory, 'src/InvoiceService.ts'), body)
    }

    it('compiles namespace-relative permissions and explicit named targets with injected context', () => {
        source(`
            @Publish('billing', 'Invoices')
            @Zone('api')
            @Version('2.0.0')
            @PermissionNamespace('invoices')
            @ResourceTarget({type: 'invoice'})
            @RequirePermission()
            class InvoiceService {
                @Context
                @RequirePermission('repo.initialize', {label: 'Initialize invoice', tenantDelegable: true})
                @ResourceTarget({idArgument: 'invoice.id'})
                initialize(note: string, invoice: {id: string}, context: unknown) {}
                @RequirePermission('list')
                list(search: string) {}
                @ResourceTarget({idArgument: 'invoiceId'})
                delete(invoiceId: string) {}
                private internal(invoiceId: string) {}
            }
        `)
        const contracts = new ApplicationContractCompiler().compile(['src'])
        expect(contracts).to.have.length(1)
        const contract = contracts[0]!
        expect([contract.namespace, contract.name, contract.zone, contract.version]).to.deep.equal(['billing', 'Invoices', 'api', '2.0.0'])
        expect(contract.functions.map(value => value.name)).to.deep.equal(['initialize', 'list', 'delete'])
        const initialize = contract.functions[0]!
        expect(initialize.parameters.map(value => value.name)).to.deep.equal(['note', 'invoice'])
        expect(initialize.findDecorator(new RequirePermissionDecorator())).to.include({
            permission: 'invoices.repo.initialize', resourceType: 'invoice', idArgument: 'invoice.id',
            argumentIndex: 1, label: 'Initialize invoice', tenantDelegable: true
        })
        expect(contract.functions[1]!.findDecorator(new RequirePermissionDecorator())).to.include({
            permission: 'invoices.list', idArgument: '', argumentIndex: -1
        })
        expect(contract.functions[2]!.findDecorator(new RequirePermissionDecorator())).to.include({
            permission: 'invoices.delete', idArgument: 'invoiceId', argumentIndex: 0
        })
    })

    it('uses the configured zone and never infers a target from argument zero', () => {
        source(`
            @Publish()
            @PermissionNamespace('invoices')
            @RequirePermission('export')
            class InvoiceService { export(invoiceId: string) {} }
        `)
        const contract = new ApplicationContractCompiler().compile(['src'], 'customer.api')[0]!
        expect(contract.zone).to.equal('customer.api')
        expect(contract.functions[0]!.findDecorator(new RequirePermissionDecorator())).to.include({
            permission: 'invoices.export', idArgument: '', argumentIndex: -1, tenantDelegable: false
        })
    })

    for (const target of ['missing', 'context']) {
        it(`rejects a target referring to ${target === 'missing' ? 'an unknown parameter' : 'injected context'}`, () => {
            source(`
                @Publish()
                @PermissionNamespace('invoices')
                class InvoiceService {
                    @Context
                    @RequirePermission('read')
                    @ResourceTarget({type: 'invoice', idArgument: '${target}'})
                    read(invoiceId: string, context: unknown) {}
                }
            `)
            expect(() => new ApplicationContractCompiler().compile(['src'])).to.throw()
        })
    }

    it('rejects dynamic authorization declarations', () => {
        source(`
            const permission = 'read'
            @Publish()
            @PermissionNamespace('invoices')
            class InvoiceService { @RequirePermission(permission) read(invoiceId: string) {} }
        `)
        expect(() => new ApplicationContractCompiler().compile(['src'])).to.throw('literal strings')
    })
})
