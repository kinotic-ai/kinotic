import { ITask } from "../ITask"
import { IEntityService, Project, ProjectType } from '@kinotic/structures-api'
import { Structures } from '@kinotic/structures-api'
import { Patient } from '../../entity/domain/health/Patient'
import { Provider } from '../../entity/domain/health/Provider'
import { Appointment } from '../../entity/domain/health/Appointment'
import { Diagnosis } from '../../entity/domain/health/Diagnosis'
import { Treatment } from '../../entity/domain/health/Treatment'
import { EntityDefinitionLoader } from '../../utils/EntityDefinitionLoader'
import { CreateStructureTaskBuilder } from './CreateStructureTaskBuilder'
import { createStructureTaskBuilder } from './CreateStructureTaskBuilder'
import { ObjectC3Type } from '@kinotic/continuum-idl'
import { TestDataGenerator } from '../../entity/domain/health/TestDataGenerator'
import path from 'path'

export class HealthTaskFactory {
    private readonly applicationId = 'healthcare'
    private projectId = 'healthcare_main_project'
    private readonly taskBuilder: CreateStructureTaskBuilder
    private entityDefinitions: Map<string, ObjectC3Type> = new Map()
    private patientService?: IEntityService<Patient>
    private providerService?: IEntityService<Provider>
    private appointmentService?: IEntityService<Appointment>
    private diagnosisService?: IEntityService<Diagnosis>
    private treatmentService?: IEntityService<Treatment>

    constructor() {
        this.taskBuilder = createStructureTaskBuilder()
    }

    getTasks(): ITask[] {
        return [
            // Create namespace first
            {
                name: () => 'Create Health Namespace',
                execute: async () => {
                    await Structures.getApplicationService().createApplicationIfNotExist(this.applicationId, 'Healthcare Domain')
                    let project = new Project(null, this.applicationId, 'Main Project', 'Healthcare Main Project')
                    project.sourceOfTruth = ProjectType.TYPESCRIPT
                    project = await Structures.getProjectService().createProjectIfNotExist(project)
                }
            },
            // Then load entity definitions
            {
                name: () => 'Load Health Entity Definitions',
                execute: async () => {
                    const loader = new EntityDefinitionLoader(
                        this.applicationId,
                        path.join(__dirname, '../../entity/domain/health'),
                        path.join(__dirname, '../../services/health')
                    )
                    this.entityDefinitions = await loader.loadDefinitions()
                    console.log('Loaded', this.entityDefinitions.size, 'entity definitions')
                }
            },
            // Then create structures
            this.taskBuilder.buildTask({
                applicationId: this.applicationId,
                projectId: this.projectId,
                name: 'Patient',
                description: 'Patient information and medical history',
                entityDefinitionSupplier: () => this.entityDefinitions.get('patient')!,
                onServiceCreated: (service: IEntityService<any>) => {
                    this.patientService = service as IEntityService<Patient>
                }
            }),
            this.taskBuilder.buildTask({
                applicationId: this.applicationId,
                projectId: this.projectId,
                name: 'Provider',
                description: 'Healthcare provider information',
                entityDefinitionSupplier: () => this.entityDefinitions.get('provider')!,
                onServiceCreated: (service: IEntityService<any>) => {
                    this.providerService = service as IEntityService<Provider>
                }
            }),
            this.taskBuilder.buildTask({
                applicationId: this.applicationId,
                projectId: this.projectId,
                name: 'Appointment',
                description: 'Medical appointments and scheduling',
                entityDefinitionSupplier: () => this.entityDefinitions.get('appointment')!,
                onServiceCreated: (service: IEntityService<any>) => {
                    this.appointmentService = service as IEntityService<Appointment>
                }
            }),
            this.taskBuilder.buildTask({
                applicationId: this.applicationId,
                projectId: this.projectId,
                name: 'Diagnosis',
                description: 'Medical diagnoses and conditions',
                entityDefinitionSupplier: () => this.entityDefinitions.get('diagnosis')!,
                onServiceCreated: (service: IEntityService<any>) => {
                    this.diagnosisService = service as IEntityService<Diagnosis>
                }
            }),
            this.taskBuilder.buildTask({
                applicationId: this.applicationId,
                projectId: this.projectId,
                name: 'Treatment',
                description: 'Medical treatments and procedures',
                entityDefinitionSupplier: () => this.entityDefinitions.get('treatment')!,
                onServiceCreated: (service: IEntityService<any>) => {
                    this.treatmentService = service as IEntityService<Treatment>
                }
            }),
            // Generate and save test data
            {
                name: () => 'Generate and Save Health Domain Test Data',
                execute: async () => {
                    if (!this.patientService || !this.providerService || 
                        !this.diagnosisService || !this.treatmentService || 
                        !this.appointmentService) {
                        throw new Error('Required services not initialized')
                    }

                    const { patients, providers, diagnoses, treatments, appointments } = 
                        TestDataGenerator.generateTestData(500)

                    // Save all entities in bulk to improve performance
                    await this.patientService.bulkSave(patients)
                    await this.patientService.syncIndex()

                    await this.providerService.bulkSave(providers)
                    await this.providerService.syncIndex()

                    await this.diagnosisService.bulkSave(diagnoses)
                    await this.diagnosisService.syncIndex()

                    await this.treatmentService.bulkSave(treatments)
                    await this.treatmentService.syncIndex()

                    await this.appointmentService.bulkSave(appointments)
                    await this.appointmentService.syncIndex()

                    console.log(`Generated and saved healthcare test data:
                        - ${patients.length} patients
                        - ${providers.length} providers
                        - ${diagnoses.length} diagnoses
                        - ${treatments.length} treatments
                        - ${appointments.length} appointments`)
                }
            }
        ]
    }
} 