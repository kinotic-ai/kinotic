import {Command} from '@oclif/core'
import {CONSOLE_URL, LocalStack, PORTAL_URL} from '@/internal/local/LocalStack'

export class LocalUp extends Command {

    static description = 'Start Kinotic on this machine: its servers, portal and system console, and the node your projects deploy to'

    static examples = [
        '$ kinotic local up'
    ]

    async run(): Promise<void> {
        const stack = new LocalStack(this.config.dataDir)
        for (const warning of stack.checkPrerequisites()) {
            this.warn(warning)
        }

        this.log(`Starting Kinotic ${stack.stackVersions.kinoticVersion}`)
        await stack.start(() => this.log('Waiting for the servers to start; the first start takes a few minutes'))

        if (stack.canRunNode) {
            const running = stack.nodePid() !== null
            const pid = await stack.startNode()
            this.log(running ? `The node is running (process ${pid})`
                             : `Started the node (process ${pid}), logging to ${stack.nodeLog}`)
        }

        this.log('')
        this.log(`Kinotic is running. Create your organization at ${PORTAL_URL}/signup,`)
        this.log('then follow the Quickstart in the portal to build your first app.')
        this.log(`The system console is at ${CONSOLE_URL}. Stop Kinotic with: kinotic local down`)
    }
}
