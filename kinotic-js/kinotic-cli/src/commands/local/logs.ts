import {Args, Command, Flags} from '@oclif/core'
import {spawnSync} from 'node:child_process'
import fs from 'node:fs'
import {LocalStack} from '@/internal/local/LocalStack'

export class LocalLogs extends Command {

    static description = 'Print the logs of Kinotic on this machine: the node\'s, one container\'s, or every container\'s'

    static examples = [
        '$ kinotic local logs',
        '$ kinotic local logs kinotic-server-management --follow',
        '$ kinotic local logs node'
    ]

    static args = {
        service: Args.string({description: '`node` for the node, or a container such as kinotic-server-management; every container when omitted'})
    }

    static flags = {
        follow: Flags.boolean({char: 'f', description: 'Keep printing new lines until interrupted'})
    }

    async run(): Promise<void> {
        const {args, flags} = await this.parse(LocalLogs)
        const stack = new LocalStack(this.config.dataDir)
        if (args.service === 'node') {
            if (!fs.existsSync(stack.nodeLog)) {
                this.error('The node has not run on this machine yet')
            }
            spawnSync('tail', flags.follow ? ['-n', '200', '-f', stack.nodeLog] : ['-n', '200', stack.nodeLog], {stdio: 'inherit'})
        } else {
            stack.printContainerLogs(args.service, flags.follow)
        }
    }
}
