import {AuthzCheck, AuthzResource, Publish} from '@kinotic-ai/core'

/**
 * The application's own service: the runtime hosting it registers it in the platform's directory with the
 * definition `kinotic sync` generated for it, so the platform checks every request to it against the
 * application's store.
 */
@Publish('com.acme.reports')
@AuthzResource({value: 'report', parent: 'tenant', roles: [{id: 'report.generator', permissions: ['can_generate']}]})
export class ReportService {

    async findReports(): Promise<string[]> {
        return ['quarterly']
    }

    @AuthzCheck({permission: 'can_generate', resource: 'tenant'})
    async generate(name: string): Promise<string> {
        return `generated ${name}`
    }
}
