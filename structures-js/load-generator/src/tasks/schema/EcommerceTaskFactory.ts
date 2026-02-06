import { ITask } from "../ITask"
import { IEntityService, Project, ProjectType } from '@kinotic/structures-api'
import { Structures } from '@kinotic/structures-api'
import path from 'path'
import { Customer } from '../../entity/domain/ecommerce/Customer'
import { Product } from '../../entity/domain/ecommerce/Product'
import { ProductReview } from '../../entity/domain/ecommerce/ProductReview'
import { Purchase } from '../../entity/domain/ecommerce/Purchase'
import { TestDataGenerator } from '../../entity/domain/ecommerce/TestDataGenerator'
import { EntityDefinitionLoader } from '../../utils/EntityDefinitionLoader'
import { CreateStructureTaskBuilder } from './CreateStructureTaskBuilder'
import { ObjectC3Type } from '@kinotic/continuum-idl'
import { createStructureTaskBuilder } from './CreateStructureTaskBuilder'

export class EcommerceTaskFactory {
    private readonly applicationId = 'ecommerce'
    private projectId = 'ecommerce_main_project'
    private readonly taskBuilder: CreateStructureTaskBuilder
    private entityDefinitions: Map<string, ObjectC3Type> = new Map()
    private customerService?: IEntityService<Customer>
    private productService?: IEntityService<Product>
    private reviewService?: IEntityService<ProductReview>
    private purchaseService?: IEntityService<Purchase>

    constructor() {
        this.taskBuilder = createStructureTaskBuilder()
    }

    getTasks(): ITask[] {
        return [
            // Create namespace first
            {
                name: () => 'Create Ecommerce Namespace',
                execute: async () => {
                    await Structures.getApplicationService().createApplicationIfNotExist(this.applicationId, 'Ecommerce Domain')
                    let project = new Project(null, this.applicationId, 'Main Project', 'Ecommerce Main Project')
                    project.sourceOfTruth = ProjectType.TYPESCRIPT
                    project = await Structures.getProjectService().createProjectIfNotExist(project)    
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
                applicationId: this.applicationId,
                projectId: this.projectId,
                name: 'Customer',
                description: 'Customer information and preferences',
                entityDefinitionSupplier: () => this.entityDefinitions.get('customer')!,
                onServiceCreated: (service) => {
                    this.customerService = service as IEntityService<Customer>
                }
            }),
            this.taskBuilder.buildTask({
                applicationId: this.applicationId,
                projectId: this.projectId,
                name: 'Product',
                description: 'Product catalog information',
                entityDefinitionSupplier: () => this.entityDefinitions.get('product')!,
                onServiceCreated: (service) => {
                    this.productService = service as IEntityService<Product>
                }
            }),
            this.taskBuilder.buildTask({
                applicationId: this.applicationId,
                projectId: this.projectId,
                name: 'ProductReview',
                description: 'Product reviews and ratings',
                entityDefinitionSupplier: () => this.entityDefinitions.get('productreview')!,
                onServiceCreated: (service) => {
                    this.reviewService = service as IEntityService<ProductReview>
                }
            }),
            this.taskBuilder.buildTask({
                applicationId: this.applicationId,
                projectId: this.projectId,
                name: 'Purchase',
                description: 'Purchase orders and transactions',
                entityDefinitionSupplier: () => this.entityDefinitions.get('purchase')!,
                onServiceCreated: (service) => {
                    this.purchaseService = service as IEntityService<Purchase>
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
            }
        ]
    }
} 