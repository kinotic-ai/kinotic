import {Command, Flags} from '@oclif/core'
import {LocalStack} from '@/internal/local/LocalStack'

export class LocalDown extends Command {

    static description = 'Stop Kinotic on this machine and every app it deployed here, keeping its data for the next kinotic local up'

    static examples = [
        '$ kinotic local down',
        '$ kinotic local down --delete-data'
    ]

    static flags = {
        'delete-data': Flags.boolean({description: 'Also delete everything Kinotic stored: its organizations, projects, data and deployed apps\' files'})
    }

    async run(): Promise<void> {
        const {flags} = await this.parse(LocalDown)
        const stack = new LocalStack(this.config.dataDir)
        if (stack.nodePid() !== null) {
            // the servers still run, so the node reports each workload it stops
            this.log('Stopping the node and the apps it runs')
            await stack.stopNode()
        }
        stack.stop(flags['delete-data'])
        this.log(flags['delete-data'] ? 'Kinotic is stopped and its data deleted' : 'Kinotic is stopped')
    }
}
