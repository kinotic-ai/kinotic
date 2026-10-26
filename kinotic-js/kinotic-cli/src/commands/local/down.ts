import {Command, Flags} from '@oclif/core'
import {LocalStack} from '@/internal/local/LocalStack'

export class LocalDown extends Command {

    static description = 'Stop Kinotic on this machine, keeping its data for the next kinotic local up'

    static examples = [
        '$ kinotic local down',
        '$ kinotic local down --delete-data'
    ]

    static flags = {
        'delete-data': Flags.boolean({description: 'Also delete everything Kinotic stored: its organizations, projects and data'})
    }

    async run(): Promise<void> {
        const {flags} = await this.parse(LocalDown)
        const stack = new LocalStack(this.config.dataDir)
        await stack.stopNode()
        stack.stop(flags['delete-data'])
        this.log(flags['delete-data'] ? 'Kinotic is stopped and its data deleted' : 'Kinotic is stopped')
    }
}
