import {select} from '@inquirer/prompts'
import {execa} from 'execa'

/**
 * Creates a new front end project using either React or Vue depending on the user's choice
 */
export async function createFrontEnd(name: string): Promise<void> {
  const framework = await select({
    message: 'Which framework would you like to use?',
    choices: [{value: 'React'}, {value: 'Vue'}]
  })

  if (framework === 'React') {
    await createReact(name)
  } else {
    await createVue(name)
  }
}

/**
 * Creates a new React project using create-react-app
 */
async function createReact(name: string): Promise<void> {
  await execa('npx', ['create-react-app', name], {stdio: 'inherit'})
}

/**
 * Creates a new Vue project using vue-cli with npx
 */
async function createVue(name: string): Promise<void> {
  await execa('npx', ['@vue/cli', 'create', name], {stdio: 'inherit'})
}
