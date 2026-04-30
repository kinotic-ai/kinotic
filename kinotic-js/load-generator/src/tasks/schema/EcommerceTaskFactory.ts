import { ITask } from "../ITask"
import { IEntityRepository } from '@kinotic-ai/persistence'
import { Project, ProjectType } from '@kinotic-ai/os-api'
import { ConnectionInfo, Kinotic, KinoticSingleton } from '@kinotic-ai/core'
import path from 'path'
import { Customer } from '../../entity/domain/ecommerce/Customer'
import { Product } from '../../entity/domain/ecommerce/Product'
import { ProductReview } from '../../entity/domain/ecommerce/ProductReview'
import { Purchase } from '../../entity/domain/ecommerce/Purchase'
import { TestDataGenerator } from '../../entity/domain/ecommerce/TestDataGenerator'
import { EntityDefinitionLoader } from '../../utils/EntityDefinitionLoader'
import { CreateStructureTaskBuilder } from './CreateStructureTaskBuilder'
import { ObjectC3Type } from '@kinotic-ai/idl'
import { createStructureTaskBuilder } from './CreateStructureTaskBuilder'
import { initKinoticAppClient } from '../../utils/AuthHelpers'

export class EcommerceTaskFactory {
    private readonly organizationId = 'kinotic'
    private readonly applicationId = 'ecommerce'
    private projectId = 'ecommerce_main_project'
    private readonly taskBuilder: CreateStructureTaskBuilder
    private readonly connectionInfoSupplier: () => Promise<ConnectionInfo>
    private appKinotic?: KinoticSingleton
    private entityDefinitions: Map<string, ObjectC3Type> = new Map()
    private customerService?: IEntityRepository<Customer>
    private productService?: IEntityRepository<Product>
    private reviewService?: IEntityRepository<ProductReview>
    private purchaseService?: IEntityRepository<Purchase>

    constructor(connectionInfoSupplier: () => Promise<ConnectionInfo>) {
        this.connectionInfoSupplier = connectionInfoSupplier
        this.taskBuilder = createStructureTaskBuilder()
    }

    getTasks(): ITask[] {
        return [
            // Create namespace first
            {
                name: () => 'Create Ecommerce Namespace',
                execute: async () => {
                    await Kinotic.applications.createApplicationIfNotExist(this.applicationId, 'Ecommerce Domain')
                    let project = new Project(null, this.applicationId, 'Main Project', 'Ecommerce Main Project')
                    project.organizationId = this.organizationId
                    project.sourceOfTruth = ProjectType.TYPESCRIPT
                    project = await Kinotic.projects.createProjectIfNotExist(project)
                }
            },
            // Provision the application user and connect an APP-scoped client
            // for entity-data CRUD. App/project/entity-definition management
            // continues to use the global ORG-scoped Kinotic.
            {
                name: () => 'Connect Ecommerce App Client',
                execute: async () => {
                    const baseConnectionInfo = await this.connectionInfoSupplier()
                    this.appKinotic = await initKinoticAppClient(baseConnectionInfo, this.applicationId)
                }
            },
            // Then load entity definitions
            {
                name: () => 'Load Ecommerce Entity Definitions',
                execute: async () => {
                    const loader = new EntityDefinitionLoader(
                        this.applicationId,
                        path.join(__dirname, '../../entity/domain/ecommerce'),
                        path.join(__dirname, '../../services/ecommerce')
                    )
                    this.entityDefinitions = await loader.loadDefinitions()
                    console.log('Loaded', this.entityDefinitions.size, 'entity definitions')
                }
            },
            // Then create structures
            this.taskBuilder.buildTask({
                organizationId: this.organizationId,
                appKinoticSupplier: () => this.appKinotic!,
                applicationId: this.applicationId,
                projectId: this.projectId,
                name: 'Customer',
                description: 'Customer information and preferences',
                entityDefinitionSupplier: () => this.entityDefinitions.get('customer')!,
                onServiceCreated: (service) => {
                    this.customerService = service as IEntityRepository<Customer>
                }
            }),
            this.taskBuilder.buildTask({
                organizationId: this.organizationId,
                appKinoticSupplier: () => this.appKinotic!,
                applicationId: this.applicationId,
                projectId: this.projectId,
                name: 'Product',
                description: 'Product catalog information',
                entityDefinitionSupplier: () => this.entityDefinitions.get('product')!,
                onServiceCreated: (service) => {
                    this.productService = service as IEntityRepository<Product>
                }
            }),
            this.taskBuilder.buildTask({
                organizationId: this.organizationId,
                appKinoticSupplier: () => this.appKinotic!,
                applicationId: this.applicationId,
                projectId: this.projectId,
                name: 'ProductReview',
                description: 'Product reviews and ratings',
                entityDefinitionSupplier: () => this.entityDefinitions.get('productreview')!,
                onServiceCreated: (service) => {
                    this.reviewService = service as IEntityRepository<ProductReview>
                }
            }),
            this.taskBuilder.buildTask({
                organizationId: this.organizationId,
                appKinoticSupplier: () => this.appKinotic!,
                applicationId: this.applicationId,
                projectId: this.projectId,
                name: 'Purchase',
                description: 'Purchase orders and transactions',
                entityDefinitionSupplier: () => this.entityDefinitions.get('purchase')!,
                onServiceCreated: (service) => {
                    this.purchaseService = service as IEntityRepository<Purchase>
                }
            }),
            // Generate and save test data
            {
                name: () => 'Generate and Save Ecommerce Test Data',
                execute: async () => {
                    if (!this.customerService || !this.productService || !this.reviewService || !this.purchaseService) {
                        throw new Error('Entity services not initialized')
                    }

                    const { customers, products, reviews, purchases } = TestDataGenerator.generateTestData(500)

                    await this.customerService.bulkSave(customers)
                    await this.customerService.syncIndex()

                    await this.productService.bulkSave(products)
                    await this.productService.syncIndex()

                    await this.reviewService.bulkSave(reviews)
                    await this.reviewService.syncIndex()

                    await this.purchaseService.bulkSave(purchases)
                    await this.purchaseService.syncIndex()

                    console.log(`Generated and saved ecommerce test data:
                        - ${customers.length} customers
                        - ${products.length} products
                        - ${reviews.length} reviews
                        - ${purchases.length} purchases`)
                }
            },
            {
                name: () => 'Disconnect Ecommerce App Client',
                execute: async () => {
                    if (this.appKinotic) {
                        await this.appKinotic.disconnect()
                    }
                }
            }
        ]
    }
}