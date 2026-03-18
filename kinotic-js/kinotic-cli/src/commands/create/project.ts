import {Args, Command} from '@oclif/core'
import {input, select} from '@inquirer/prompts'
import chalk from 'chalk'
import ora from 'ora'
import path from 'node:path'
import process from 'node:process'
import {spawnEngine} from '@/internal/spawn/SpawnEngine'
import {createFrontEnd} from '@/internal/CommandHelper'

export class Project extends Command {
  static description = 'Creates a Kinotic Project'

  static examples = [
    '$ kinotic create project MyProjectName',
  ]

  static args = {
    name: Args.string({description: 'The name for the project', required: true})
  }

  async run(): Promise<void> {
    const {args} = await this.parse(Project)

    const projectDir: string = path.resolve(args.name)
    let context: Record<string, unknown> = {projectName: args.name}

    this.log(chalk.cyan('Creating Kinotic Project'))

    const spinner = ora('Generating project...').start()
    try {
      context = (await spawnEngine.renderSpawn('project', projectDir, context)) ?? context
      spinner.succeed(chalk.green('Project generated'))
    } catch (err) {
      spinner.fail('Project generation failed')
      throw err
    }

    process.chdir(projectDir)

    let choice: string
    do {
      choice = await select({
        message: 'What would you like to add?',
        choices: [{value: 'Library'}, {value: 'Frontend'}, {value: 'Quit'}]
      })

      switch (choice) {
        case 'Library': {
          const libraryName = await input({message: 'Library Name'})
          context.libraryName = libraryName
          await this.renderProjectModule('library', libraryName, projectDir, context)
          break
        }
        case 'Frontend': {
          const name = await input({message: 'Project Name'})
          await createFrontEnd(name)
          break
        }
        case 'Quit':
          break
      }
    } while (choice !== 'Quit')
  }

  private async renderProjectModule(spawn: string, name: string, projectDir: string, context: Record<string, unknown>): Promise<void> {
    const dir: string = path.resolve(name)

    if (!dir.startsWith(projectDir)) {
      throw new Error(`Module dir ${dir} must be within ${projectDir}`)
    }

    await spawnEngine.renderSpawn(spawn, dir, context)
  }
}
