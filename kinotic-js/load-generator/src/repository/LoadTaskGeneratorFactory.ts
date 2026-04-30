import {LoadTestConfig} from '@/config/LoadTestConfig.js'
import {KinoticConnectionConfig} from '@/config/KinoticConnectionConfig.ts'
import { CreateComplexEntitiesTaskGenerator } from '@/tasks/schema/CreateComplexEntitiesTaskGenerator.ts'
import {FindTaskGenerator} from '@/tasks/FindTaskGenerator.js'
import {ITaskGenerator} from '@/tasks/ITaskGenerator.js'
import {ITaskGeneratorFactory} from '@/tasks/ITaskGeneratorFactory.js'
import {MultiTenantFindTaskGenerator} from '@/tasks/MultiTenantFindTaskGenerator.js'
import {MultiTenantSearchTaskGenerator} from '@/tasks/MultiTenantSearchTaskGenerator.js'
import {MultiTenantTaskGeneratorDelegator, TenantId} from '@/tasks/MultiTenantTaskGeneratorDelegator.js'
import {SaveTaskGenerator} from '@/tasks/SaveTaskGenerator.js'
import {SearchPeopleTaskGenerator} from '@/tasks/SearchPeopleTaskGenerator.js'
import {ConnectionInfo} from '@kinotic-ai/core'


export class LoadTaskGeneratorFactory {

    public static createTaskGenerator(kinoticConnectionConfig: KinoticConnectionConfig,
                                      loadTestConfig: LoadTestConfig): ITaskGenerator | never {

        if(loadTestConfig.testName === 'bulkLoadSmall') {

            const peopleGenFactory: ITaskGeneratorFactory<TenantId>
                      = (tenantId) => {
                return new SaveTaskGenerator(
                    this.createConnectionInfo(tenantId, kinoticConnectionConfig),
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
                    this.createConnectionInfo(tenantId, kinoticConnectionConfig),
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
                    this.createConnectionInfo(tenantId, kinoticConnectionConfig),
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
                    this.createConnectionInfo(tenantId, kinoticConnectionConfig), 1, 'firstName: John', 100)
            }

            return new MultiTenantTaskGeneratorDelegator(loadTestConfig.beginTenantIdNumber,
                                                         loadTestConfig.numberOfTenants,
                                                         peopleGenFactory)

        }else if(loadTestConfig.testName === 'searchMultiTenantSmall') {

            // Tenant used during connection does not matter for this test
            return new MultiTenantSearchTaskGenerator(this.createConnectionInfo('kinotic', kinoticConnectionConfig),
                                                      100,
                                                      'firstName: John',
                                                      100,
                                                      loadTestConfig.numberOfTenants)

        }else if(loadTestConfig.testName === 'searchMultiTenantLarge') {

            // Tenant used during connection does not matter for this test
            return new MultiTenantSearchTaskGenerator(this.createConnectionInfo('kinotic', kinoticConnectionConfig),
                                                      1000,
                                                      'firstName: John',
                                                      100,
                                                      loadTestConfig.numberOfTenants)

        }else if(loadTestConfig.testName === 'findAll') {

            const peopleGenFactory: ITaskGeneratorFactory<TenantId>
                      = (tenantId) => {
                return new FindTaskGenerator(
                    this.createConnectionInfo(tenantId, kinoticConnectionConfig), 1, 100)
            }

            return new MultiTenantTaskGeneratorDelegator(loadTestConfig.beginTenantIdNumber,
                                                         loadTestConfig.numberOfTenants,
                                                         peopleGenFactory)

        }else if(loadTestConfig.testName === 'findAllMultiTenantSmall') {

            // Tenant used during connection does not matter for this test
            return new MultiTenantFindTaskGenerator(this.createConnectionInfo('kinotic', kinoticConnectionConfig),
                                                    100,
                                                    100,
                                                    loadTestConfig.numberOfTenants)

        }else if(loadTestConfig.testName === 'findAllMultiTenantLarge') {

            // Tenant used during connection does not matter for this test
            return new MultiTenantFindTaskGenerator(this.createConnectionInfo('kinotic', kinoticConnectionConfig),
                                                    1000,
                                                    100,
                                                    loadTestConfig.numberOfTenants)
        } else if(loadTestConfig.testName === 'generateComplexEntities'){

            return new CreateComplexEntitiesTaskGenerator(this.createConnectionInfo('kinotic', kinoticConnectionConfig))
            
        }else {
            throw new Error(`Unsupported test name: ${loadTestConfig.testName}`)
        }
    }

    private static createConnectionInfo(tenantId: string,
                                        kinoticConnectionConfig: KinoticConnectionConfig):() => Promise<ConnectionInfo> {
        return  async () => {
            return {
                host                 : kinoticConnectionConfig.kinoticHost,
                port                 : kinoticConnectionConfig.kinoticPort,
                useSSL               : kinoticConnectionConfig.kinoticUseSsl,
                maxConnectionAttempts: 5,
                connectHeaders       : {
                    login        : 'kinotic@kinotic.local',
                    passcode     : 'kinotic',
                    authScopeType: 'ORGANIZATION',
                    authScopeId  : 'kinotic-test',
                    tenantId     : tenantId
                }
            }
        }
    }

}
