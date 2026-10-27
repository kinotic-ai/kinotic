import {Command} from '@oclif/core'
import {LocalStack} from '@/internal/local/LocalStack'

export class LocalStatus extends Command {

    static description = 'Show the state of Kinotic on this machine: each of its containers, and the node'

    static examples = [
        '$ kinotic local status'
    ]

    async run(): Promise<void> {
        const stack = new LocalStack(this.config.dataDir)
        stack.printContainers()
        const pid = stack.nodePid()
        this.log('')
        this.log(pid !== null ? `Node: running (process ${pid}), logging to ${stack.nodeLog}` : 'Node: not running')
        this.log(`Files: ${stack.dir}`)
    }
}
