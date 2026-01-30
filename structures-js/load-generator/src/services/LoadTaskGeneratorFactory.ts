import {LoadTestConfig} from '@/config/LoadTestConfig.js'
import {StructuresConnectionConfig} from '@/config/StructuresConnectionConfig.js'
import { CreateComplexStructuresTaskGenerator } from '@/tasks/schema/CreateComplexStructuresTaskGenerator'
import {FindTaskGenerator} from '@/tasks/FindTaskGenerator.js'
import {ITaskGenerator} from '@/tasks/ITaskGenerator.js'
import {ITaskGeneratorFactory} from '@/tasks/ITaskGeneratorFactory.js'
import {MultiTenantFindTaskGenerator} from '@/tasks/MultiTenantFindTaskGenerator.js'
import {MultiTenantSearchTaskGenerator} from '@/tasks/MultiTenantSearchTaskGenerator.js'
import {MultiTenantTaskGeneratorDelegator, TenantId} from '@/tasks/MultiTenantTaskGeneratorDelegator.js'
import {SaveTaskGenerator} from '@/tasks/SaveTaskGenerator.js'
import {SearchPeopleTaskGenerator} from '@/tasks/SearchPeopleTaskGenerator.js'
import {ConnectionInfo} from '@kinotic/continuum-client'


export class LoadTaskGeneratorFactory {

    public static createTaskGenerator(structuresConfig: StructuresConnectionConfig,
                                      loadTestConfig: LoadTestConfig): ITaskGenerator | never {

        if(loadTestConfig.testName === 'bulkLoadSmall') {

            const peopleGenFactory: ITaskGeneratorFactory<TenantId>
                      = (tenantId) => {
                return new SaveTaskGenerator(
                    this.createConnectionInfo(tenantId, structuresConfig),
                    1000,
                    1000)
            }

            return new MultiTenantTaskGeneratorDelegator(loadTestConfig.beginTenantIdNumber,
                                                         loadTestConfig.numberOfTenants,
                                                         peopleGenFactory)

        }else if(loadTestConfig.testName === 'bulkLoadMedium') {

            const peopleGenFactory: ITaskGeneratorFactory<TenantId>
                      = (tenantId) => {
                return new SaveTaskGenerator(
                    this.createConnectionInfo(tenantId, structuresConfig),
                    2000,
                    10000)
            }

            return new MultiTenantTaskGeneratorDelegator(loadTestConfig.beginTenantIdNumber,
                                                         loadTestConfig.numberOfTenants,
                                                         peopleGenFactory)

        }else if(loadTestConfig.testName === 'bulkLoadLarge') {

            const peopleGenFactory: ITaskGeneratorFactory<TenantId>
                      = (tenantId) => {
                return new SaveTaskGenerator(
                    this.createConnectionInfo(tenantId, structuresConfig),
                    5000,
                    50000)
            }

            return new MultiTenantTaskGeneratorDelegator(loadTestConfig.beginTenantIdNumber,
                                                         loadTestConfig.numberOfTenants,
                                                         peopleGenFactory)

        }else if(loadTestConfig.testName === 'search') {

            const peopleGenFactory: ITaskGeneratorFactory<TenantId>
                      = (tenantId) => {
                return new SearchPeopleTaskGenerator(
                    this.createConnectionInfo(tenantId, structuresConfig),1, 'firstName: John', 100)
            }

            return new MultiTenantTaskGeneratorDelegator(loadTestConfig.beginTenantIdNumber,
                                                         loadTestConfig.numberOfTenants,
                                                         peopleGenFactory)

        }else if(loadTestConfig.testName === 'searchMultiTenantSmall') {

            // Tenant used during connection does not matter for this test
            return new MultiTenantSearchTaskGenerator(this.createConnectionInfo('mindignited', structuresConfig),
                                                      100,
                                                      'firstName: John',
                                                      100,
                                                      loadTestConfig.numberOfTenants)

        }else if(loadTestConfig.testName === 'searchMultiTenantLarge') {

            // Tenant used during connection does not matter for this test
            return new MultiTenantSearchTaskGenerator(this.createConnectionInfo('mindignited', structuresConfig),
                                                      1000,
                                                      'firstName: John',
                                                      100,
                                                      loadTestConfig.numberOfTenants)

        }else if(loadTestConfig.testName === 'findAll') {

            const peopleGenFactory: ITaskGeneratorFactory<TenantId>
                      = (tenantId) => {
                return new FindTaskGenerator(
                    this.createConnectionInfo(tenantId, structuresConfig),1, 100)
            }

            return new MultiTenantTaskGeneratorDelegator(loadTestConfig.beginTenantIdNumber,
                                                         loadTestConfig.numberOfTenants,
                                                         peopleGenFactory)

        }else if(loadTestConfig.testName === 'findAllMultiTenantSmall') {

            // Tenant used during connection does not matter for this test
            return new MultiTenantFindTaskGenerator(this.createConnectionInfo('mindignited', structuresConfig),
                                                    100,
                                                    100,
                                                    loadTestConfig.numberOfTenants)

        }else if(loadTestConfig.testName === 'findAllMultiTenantLarge') {

            // Tenant used during connection does not matter for this test
            return new MultiTenantFindTaskGenerator(this.createConnectionInfo('mindignited', structuresConfig),
                                                    1000,
                                                    100,
                                                    loadTestConfig.numberOfTenants)
        } else if(loadTestConfig.testName === 'generateComplexStructures'){

            return new CreateComplexStructuresTaskGenerator(this.createConnectionInfo('mindignited', structuresConfig))
            
        }else {
            throw new Error(`Unsupported test name: ${loadTestConfig.testName}`)
        }
    }

    private static createConnectionInfo(tenantId: string,
                                        structuresConfig: StructuresConnectionConfig):() => Promise<ConnectionInfo> {
        return  async () => {
            return {
                host                 : structuresConfig.structuresHost,
                port                 : structuresConfig.structuresPort,
                useSSL               : structuresConfig.stucturesUseSsl,
                maxConnectionAttempts: 5,
                connectHeaders       : {
                    login   : 'admin',
                    passcode: 'structures',
                    tenantId: tenantId
                }
            }
        }
    }

}
