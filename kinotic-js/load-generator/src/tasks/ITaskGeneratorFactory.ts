import {ITaskGenerator} from '@/tasks/ITaskGenerator.js'

export type ITaskGeneratorFactory<T> = (options: T) => ITaskGenerator
