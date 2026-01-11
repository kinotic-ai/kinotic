import {Page, Pageable} from '@mindignited/continuum-client'
import {IEntityService, Structure, Structures} from '@mindignited/structures-api'
import * as allure from 'allure-js-commons'
import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it} from 'vitest'
import {WebSocket} from 'ws'
import {Vehicle} from '../domain/Vehicle.js'
import {
    createTestVehicle,
    createTestVehicles,
    createVehicleStructureIfNotExist,
    deleteStructure,
    generateRandomString,
    initContinuumClient,
    logFailure,
    shutdownContinuumClient
} from '../TestHelpers.js'

Object.assign(global, { WebSocket})

interface LocalTestContext {
    structure: Structure
    entityService: IEntityService<Vehicle>
}

describe('End To End Tests', () => {

    beforeAll(async () => {
        await allure.suite('Typescript Client')
        await allure.subSuite('Versioned Tests')
        await initContinuumClient()
    }, 300000)

    afterAll(async () => {
        await shutdownContinuumClient()
    }, 60000)

    beforeEach<LocalTestContext>(async (context) => {
        context.structure = await createVehicleStructureIfNotExist(generateRandomString(10), generateRandomString(5))
        expect(context.structure).toBeDefined()
        context.entityService = Structures.createEntityService(context.structure.applicationId, context.structure.name)
        expect(context.entityService).toBeDefined()
    })  

    afterEach<LocalTestContext>(async (context) => {
        await expect(deleteStructure(context.structure.id as string)).resolves.toBeUndefined()
        await expect(Structures.getStructureService().syncIndex()).resolves.toBeNull()
        await Structures.getProjectService().deleteById(context.structure.projectId)
        await expect(Structures.getProjectService().syncIndex()).resolves.toBeNull()
        await Structures.getApplicationService().deleteById(context.structure.applicationId)
    })

    it<LocalTestContext>('Test Basic CRUD',
     async ({entityService}) => {
         const vehicle = createTestVehicle()
         let savedVehicle: Vehicle = await logFailure(entityService.save(vehicle), 'Failed to save vehicle')

         expect(savedVehicle).toBeDefined()
         expect(savedVehicle.id).toBeDefined()
         expect(savedVehicle.version).toBeDefined()

         // Update and save again
         savedVehicle.color = 'Grey'
         savedVehicle = await logFailure(entityService.update(savedVehicle), 'Failed to update vehicle')

         expect(savedVehicle).toBeDefined()
         expect(savedVehicle.id).toBeDefined()
         expect(savedVehicle.version).toBeDefined()

         const loaded = await logFailure(entityService.findById(savedVehicle.id), 'Failed to find vehicle')
         expect(savedVehicle.id).toEqual(loaded.id)
         expect(savedVehicle.version).toEqual(loaded.version)

         // Count
         await expect(entityService.count()).resolves.toBe(1)

         // Delete
         await expect(entityService.deleteById(loaded.id)).resolves.toBeNull()
     })

    it<LocalTestContext>('Test Optimistic Locking',
     async ({entityService}) => {
         const vehicle = createTestVehicle()
         let savedVehicle: Vehicle = await logFailure(entityService.save(vehicle), 'Failed to save vehicle')

         expect(savedVehicle).toBeDefined()
         expect(savedVehicle.id).toBeDefined()
         expect(savedVehicle.version).toBeDefined()

         savedVehicle.color = 'Grey'
         await logFailure(entityService.update(savedVehicle), 'Failed to update vehicle')

         // try and save same one again
         try {
             await entityService.update(savedVehicle)
         } catch (e: any) {
             expect(e.message).toEqual(expect.stringContaining('version conflict'))
         }

         await expect(entityService.deleteById(savedVehicle.id)).resolves.toBeNull()
     })

    it<LocalTestContext>('Test Bulk CRUD',
     async ({entityService}) => {
         // Create Vehicles
         const vehicles: Vehicle[] = createTestVehicles(100)
         await expect(entityService.bulkSave(vehicles)).resolves.toBeNull()

         await expect(entityService.syncIndex()).resolves.toBeDefined()

         await expect(entityService.count()).resolves.toBe(100)

         const page: Page<Vehicle> = await entityService.findAll(Pageable.create(0, 10))
         expect(page).toBeDefined()
         expect(page.totalElements).toBe(100)
         expect(page.content).toBeDefined()
         if(page.content) {
             expect(page.content.length).toBe(10)

             // Update the first 10 people
             for (let vehicle of page.content) {
                 expect(vehicle.version).toBeDefined()
                 vehicle.color = 'CLEAR'
             }

             await expect(entityService.bulkUpdate(page.content)).resolves.toBeNull()
         }
         await expect(entityService.syncIndex()).resolves.toBeDefined()

         // Search for all the people
         const searchPage: Page<Vehicle> = await entityService.search('color:CLEAR',Pageable.create(0, 10))
         expect(searchPage).toBeDefined()
         expect(searchPage.totalElements).toBe(10)
         expect(searchPage.content).toBeDefined()
         // @ts-ignore
         expect(searchPage.content.length).toBe(10)
     })

    it<LocalTestContext>('Test FindByIds ',
     async ({entityService}) => {
         const vehicles: Vehicle[] = createTestVehicles(100)
         await expect(entityService.bulkSave(vehicles)).resolves.toBeNull()

         await expect(entityService.syncIndex()).resolves.toBeDefined()

         await expect(entityService.count()).resolves.toBe(100)

         // Find all the vehicles
         let elementsFound = 0
         let vehicleIds: string[] = []
         const pageable = Pageable.create(0, 10)
         const pageOne = await entityService.findAll(pageable)
         for await(const page of pageOne){
             expect(page).toBeDefined()
             expect(page.content).toBeDefined()
             if(page.content) {
                 expect(page.content.length).toBe(10)
                 for (const vehicle of page.content) {
                     elementsFound++
                     if (elementsFound % 2 === 0) {
                         vehicleIds.push(vehicle.id)
                     }
                 }
             }
         }
         expect(elementsFound, 'Should have found 100 Entities').toBe(100)

         const vehiclesByIds = await entityService.findByIds(vehicleIds)
         expect(vehiclesByIds.length, 'Should have 50 Entities when using findByIds').toBe(50)
         for(const vehicle of vehiclesByIds){
             expect(vehicle.version).toBeDefined()
         }

     })


    // Should fail since you cannot use update with a new object and optimistic locking
    it<LocalTestContext>('Test Update Fails',
     async ({entityService}) => {
         const vehicle = createTestVehicle()
         await expect(entityService.update(vehicle)).rejects.toThrow()
     })

})