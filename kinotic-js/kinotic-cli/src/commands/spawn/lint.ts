import {Args, Command, Flags} from '@oclif/core'
import chalk from 'chalk'
import fs from 'node:fs'
import path from 'node:path'
import {lintSpawnDir} from '@kinotic-ai/spawn/node'

export class Lint extends Command {
  static description = 'Reports variables used in a spawn template that are not declared in its spawn.json'

  static examples = [
    '$ kinotic spawn lint',
    '$ kinotic spawn lint ./my-template --fix',
  ]

  static args = {
    dir: Args.string({description: 'The spawn template directory', default: '.'})
  }

  static flags = {
    fix: Flags.boolean({description: 'Add stub propertySchema entries for undeclared variables'})
  }

  async run(): Promise<void> {
    const {args, flags} = await this.parse(Lint)
    const dir = path.resolve(args.dir)

    const {undeclared} = await lintSpawnDir(dir)

    if (undeclared.length === 0) {
      this.log(chalk.green('✓ spawn.json declares everything the templates use'))
      return
    }

    this.log(chalk.yellow('Undeclared variables used in templates:'))
    for (const variable of undeclared) {
      this.log(`  ${chalk.bold(variable.name)}  ${chalk.dim(variable.files.join(', '))}`)
    }

    if (flags.fix) {
      this.applyFix(dir, undeclared.map(v => v.name))
      this.log(chalk.green(`Added ${undeclared.length} stub(s) to spawn.json — fill in description/default.`))
    } else {
      this.error('spawn.json is missing declarations (run with --fix to add stubs)', {exit: 1})
    }
  }

  // Adds a string-typed stub to the root spawn.json's propertySchema for each
  // undeclared name, leaving existing entries untouched.
  private applyFix(dir: string, names: string[]): void {
    const spawnJsonPath = path.join(dir, 'spawn.json')
    const config = fs.existsSync(spawnJsonPath)
      ? JSON.parse(fs.readFileSync(spawnJsonPath, {encoding: 'utf8'}))
      : {}
    config.propertySchema ??= {}
    for (const name of names) {
      config.propertySchema[name] ??= {type: 'string', description: 'TODO'}
    }
    fs.writeFileSync(spawnJsonPath, JSON.stringify(config, null, 2) + '\n')
  }
}
