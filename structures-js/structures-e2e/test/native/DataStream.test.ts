import {Pageable} from '@kinotic/continuum-client'
import {ObjectC3Type, PropertyDefinition, StringC3Type} from '@kinotic/continuum-idl'
import {IEntityService, Structure, Structures} from '@kinotic/structures-api'
import * as allure from 'allure-js-commons'
import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it} from 'vitest'
import {WebSocket} from 'ws'
import {Alert} from '../domain/Alert.js'
import {
    createAlertStructureIfNotExist,
    createTestAlert,
    createTestAlerts,
    deleteStructure,
    generateRandomString,
    initContinuumClient,
    logFailure,
    shutdownContinuumClient
} from '../TestHelpers.js'

Object.assign(global, { WebSocket })

interface LocalTestContext {
    structure: Structure
    entityService: IEntityService<Alert>
}

describe('End To End Tests', () => {
    beforeAll(async () => {
        await allure.suite('Typescript Client')
        await allure.subSuite('Data Stream Tests')
        await initContinuumClient()
    }, 300000)

    afterAll(async () => {
        await shutdownContinuumClient()
    }, 60000)

    beforeEach<LocalTestContext>(async (context) => {
        context.structure = await createAlertStructureIfNotExist(generateRandomString(10), generateRandomString(5))
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

    it<LocalTestContext>('Test Basic Stream Write and Search', async ({entityService}) => {
        const alert = createTestAlert()
        const alertId = alert.alertId

        await logFailure(entityService.save(alert), 'Failed to save alert')
        await entityService.syncIndex()

        const results = await entityService.search(`alertId:${alertId}`, Pageable.create(0, 10))
        expect(results.totalElements).toBe(1)
        expect(results.content?.[0].alertId).toBe(alertId)
        expect(results.content?.[0].severity).toBe(alert.severity)
        expect(results.content?.[0].active).toBe(alert.active)
    })

    // Replaced timestamp ordering test with a more relevant data stream test
    it<LocalTestContext>('Test Multiple Writes Maintain Data Integrity', async ({entityService}) => {
        const alert1 = createTestAlert({ index: 1 })
        const alert2 = createTestAlert({ index: 2 })

        await entityService.save(alert1)
        await entityService.save(alert2)
        await entityService.syncIndex()

        const results = await entityService.search(`alertId:(${alert1.alertId} OR ${alert2.alertId})`, Pageable.create(0, 2))
        expect(results.totalElements).toBe(2)

        const foundIds = results.content?.map(a => a.alertId) || []
        expect(foundIds).toContain(alert1.alertId)
        expect(foundIds).toContain(alert2.alertId)
    })

    it<LocalTestContext>('Test Bulk Stream Write', async ({entityService}) => {
        const alerts = createTestAlerts(50)

        await entityService.bulkSave(alerts)
        await entityService.syncIndex()

        const count = await entityService.count()
        expect(count).toBe(50)

        const highSeverityCount = await entityService.countByQuery('severity:HIGH')
        expect(highSeverityCount).toBe(16)
    })

    it<LocalTestContext>('Test Stream Filtering by Severity', async ({entityService}) => {
        const alertLow = createTestAlert({ index: 0 }) // LOW
        const alertMedium = createTestAlert({ index: 1 }) // MEDIUM
        const alertHigh = createTestAlert({ index: 2 }) // HIGH
        const alerts = [alertLow, alertMedium, alertHigh]

        await entityService.bulkSave(alerts)
        await entityService.syncIndex()

        const highAlerts = await entityService.search('severity:HIGH', Pageable.create(0, 10))
        expect(highAlerts.totalElements).toBe(1)
        expect(highAlerts.content?.[0].alertId).toBe(alertHigh.alertId)

        const lowMediumAlerts = await entityService.search('severity:(LOW OR MEDIUM)', Pageable.create(0, 10))
        expect(lowMediumAlerts.totalElements).toBe(2)
        const lowMediumIds = lowMediumAlerts.content?.map(a => a.alertId) || []
        expect(lowMediumIds).toContain(alertLow.alertId)
        expect(lowMediumIds).toContain(alertMedium.alertId)
    })

    it<LocalTestContext>('Test Stream Time Range Search', async ({entityService}) => {
        const now = new Date()
        const alerts = [
            createTestAlert({ index: 0, timestamp: new Date(now.getTime() - 3000) }),
            createTestAlert({ index: 1, timestamp: new Date(now.getTime() - 2000) }),
            createTestAlert({ index: 2, timestamp: new Date(now.getTime() - 1000) })
        ]

        await entityService.bulkSave(alerts)
        await entityService.syncIndex()

        const rangeStart = new Date(now.getTime() - 2500)
        const recentAlerts = await entityService.search(
            `timestamp:[${rangeStart.toISOString()} TO ${now.toISOString()}]`,
            Pageable.create(0, 10)
        )
        expect(recentAlerts.totalElements).toBe(2)
        const foundIds = recentAlerts.content?.map(a => a.alertId) || []
        expect(foundIds).toContain(alerts[1].alertId)
        expect(foundIds).toContain(alerts[2].alertId)
    })

    it<LocalTestContext>('Test Stream Pagination', async ({entityService}) => {
        const alerts = createTestAlerts(25)

        await entityService.bulkSave(alerts)
        await entityService.syncIndex()

        const page1 = await entityService.findAll(Pageable.create(0, 10))
        expect(page1.content?.length).toBe(10)
        expect(page1.totalElements).toBe(25)

        const page2 = await entityService.findAll(Pageable.create(1, 10))
        expect(page2.content?.length).toBe(10)
    })

    it<LocalTestContext>('Test Delete By Query', async ({entityService}) => {
        const alertLow = createTestAlert({ index: 0 }) // LOW
        const alertHigh1 = createTestAlert({ index: 2 }) // HIGH
        const alertHigh2 = createTestAlert({ index: 5 }) // HIGH
        const alerts = [alertLow, alertHigh1, alertHigh2]

        await entityService.bulkSave(alerts)
        await entityService.syncIndex()

        await entityService.deleteByQuery('severity:HIGH')
        await entityService.syncIndex()

        const remainingCount = await entityService.count()
        expect(remainingCount).toBe(1)

        const remainingAlerts = await entityService.findAll(Pageable.create(0, 10))
        expect(remainingAlerts.content?.[0].alertId).toBe(alertLow.alertId)
        expect(remainingAlerts.content?.[0].severity).toBe('LOW')
    })

    it<LocalTestContext>('Test Alert Schema Evolution - Add Field (Non-Breaking)', async ({structure, entityService}) => {
        // Step 1: Save initial data with existing structure
        const initialAlert = createTestAlert()
        await logFailure(entityService.save(initialAlert), 'Failed to save initial alert')
        await entityService.syncIndex()

        // Verify initial data
        let results = await entityService.search(`alertId:${initialAlert.alertId}`, Pageable.create(0, 10))
        expect(results.totalElements).toBe(1)
        expect(results.content?.[0].alertId).toBe(initialAlert.alertId)

        // Step 2: Modify the schema - add a new 'category' field (no unpublish needed)
        const propertyDefinition = new PropertyDefinition('category', new StringC3Type())
        structure.entityDefinition.properties.push(propertyDefinition)

        // Step 3: Update the existing structure
        await Structures.getStructureService().save(structure)

        // Step 4: Save data with new field
        const newAlert = createTestAlert({index: 2})
        const alertWithCategory = {
            ...newAlert,
            category: 'System Failure'
        }

        await logFailure(entityService.save(alertWithCategory as Alert), 'Failed to save alert with category')
        await entityService.syncIndex()

        // Verify new data with category
        results = await entityService.search(`alertId:${newAlert.alertId}`, Pageable.create(0, 10))
        expect(results.totalElements).toBe(1)
        expect(results.content?.[0].alertId).toBe(newAlert.alertId)
        expect(results.content?.[0]).toHaveProperty('category')
        // @ts-ignore We manually added the category field
        expect(results.content?.[0].category).toBe('System Failure')
    })

    it<LocalTestContext>('Test Alert Schema Evolution - Remove Field (Breaking)', async ({structure, entityService}) => {
        // Step 1: Save initial data with existing structure
        const initialAlert = createTestAlert()
        await logFailure(entityService.save(initialAlert), 'Failed to save initial alert')
        await entityService.syncIndex()

        // Verify initial data
        let results = await entityService.search(`alertId:${initialAlert.alertId}`, Pageable.create(0, 10))
        expect(results.totalElements).toBe(1)
        expect(results.content?.[0].alertId).toBe(initialAlert.alertId)
        expect(results.content?.[0].source).toBe(initialAlert.source)

        // Step 2: Modify the schema - remove the 'source' field (breaking change)
        await Structures.getStructureService().unPublish(structure.id as string)

        const updatedEntityDefinition = structure.entityDefinition
        updatedEntityDefinition.properties = updatedEntityDefinition.properties.filter(
            prop => prop.name !== 'source'
        )

        // Step 3: Update the existing structure
        structure.entityDefinition = updatedEntityDefinition
        await Structures.getStructureService().save(structure)
        await Structures.getStructureService().publish(structure.id as string)

        // Step 4: Save data without the removed 'source' field (using existing entityService)
        const newAlert = createTestAlert()
        const alertWithoutSource = {
            alertId: newAlert.alertId,
            message: newAlert.message,
            severity: newAlert.severity,
            timestamp: newAlert.timestamp,
            active: newAlert.active
        }

        await logFailure(entityService.save(alertWithoutSource as Alert), 'Failed to save alert without source')
        await entityService.syncIndex()

        // Verify new data doesn't have 'source'
        results = await entityService.search(`alertId:${newAlert.alertId}`, Pageable.create(0, 10))
        expect(results.totalElements).toBe(1)
        expect(results.content?.[0].alertId).toBe(newAlert.alertId)
        expect(results.content?.[0]).not.toHaveProperty('source')
    })
})