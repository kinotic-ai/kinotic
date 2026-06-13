import {Command, Flags} from '@oclif/core'
import {resolveServer} from '@/internal/state/Environment'
import {CliAuthenticator} from '@/internal/CliAuthenticator'

export class Login extends Command {

    static description = 'Log in to a Kinotic Server and store credentials for subsequent commands'

    static examples = [
        '$ kinotic login',
        '$ kinotic login --server http://localhost:9090'
    ]

    static flags = {
        server: Flags.string({char: 's', description: 'The Kinotic server to log in to'})
    }

    async run(): Promise<void> {
        const {flags} = await this.parse(Login)
        const serverConfig = await resolveServer(this.config.configDir, flags.server)
        if (!(await new CliAuthenticator(serverConfig.url, this.config.configDir, this).login())) {
            this.error('Login failed')
        }
        this.log(`Logged in to ${serverConfig.url}`)
    }
}
