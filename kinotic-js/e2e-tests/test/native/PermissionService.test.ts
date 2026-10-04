import {Kinotic, Pageable} from '@kinotic-ai/core'
import {Group, Project, SubjectKind, type RoleDefinition} from '@kinotic-ai/management-api'
import * as allure from 'allure-js-commons'
import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {initKinoticClient, shutdownKinoticClient} from '../TestHelpers.js'

// the second kinotic-test organization user V2__kinotic_test_users seeds
const SALLY_ID = '00000000-0000-0000-0000-000000000004'

/**
 * Covers the access control client as the organization's administrator uses it: the catalog and the roles,
 * a custom role defined from the catalog and deleted once unused, a group with a member, a grant made,
 * listed, explained and revoked, and the caller's own access listed.
 */
describe('Kinotic JS', () => {

    let application: {id: string}
    let project: Project

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('PermissionService')
        await initKinoticClient()
        application = await Kinotic.applications.createApplicationIfNotExist('e2e-permissions', 'e2e fixture application for the permission service test')
        project = await Kinotic.projects.createProjectIfNotExist(new Project(null, application.id, 'permissions-a', 'Project A'))
    }, 300000)

    afterAll(async () => {
        if (project?.id) {
            await Kinotic.projects.deleteById(project.id)
            await Kinotic.projects.syncIndex()
        }
        if (application?.id) {
            await Kinotic.applications.deleteById(application.id)
        }
        await shutdownKinoticClient()
    }, 120000)

    it('lists the catalog and the built-in roles', async () => {
        const catalog = await Kinotic.permissions.findPermissions()
        expect(catalog.project).toContain('project_can_edit')
        expect(catalog.organization).toContain('organization_can_manage_access')

        const roles = await Kinotic.permissions.findRoles()
        const editor = roles.find(role => role.id === 'project.editor')
        expect(editor?.builtIn).toBe(true)
        expect(editor?.permissions).toContain('project_can_edit')
    })

    it('defines a custom role from the catalog and deletes it once unused', async () => {
        const saved = await Kinotic.permissions.saveRole({id: null, name: 'Release Manager e2e', description: 'ships projects',
                                                          builtIn: false, permissions: ['project_can_edit', 'project_can_view']})
        expect(saved.id).toBeTruthy()
        expect(saved.builtIn).toBe(false)
        try {
            const listed = (await Kinotic.permissions.findRoles()).find(role => role.id === saved.id) as RoleDefinition
            expect(listed.permissions.sort()).toEqual(['project_can_edit', 'project_can_view'])

            // a permission the model does not have is refused
            await expect(Kinotic.permissions.saveRole({...saved, permissions: ['project_can_fly']})).rejects.toThrow()
        } finally {
            await Kinotic.permissions.deleteRole(saved.id!)
        }
        expect((await Kinotic.permissions.findRoles()).some(role => role.id === saved.id)).toBe(false)
    })

    it('grants through a group, explains the access and revokes it', async () => {
        const group = Object.assign(new Group(), {name: 'QA e2e', description: 'tests everything'})
        const savedGroup = await Kinotic.permissions.saveGroup(group)
        const onProject = {type: 'project', id: project.id!}
        const sally = {kind: SubjectKind.USER, id: SALLY_ID}
        try {
            await Kinotic.permissions.addGroupMember(savedGroup.id!, SALLY_ID)
            expect((await Kinotic.permissions.findGroupMembers(savedGroup.id!)).map(member => member.id)).toEqual([SALLY_ID])
            expect((await Kinotic.permissions.findGroups(Pageable.create(0, 100))).content?.some(listed => listed.id === savedGroup.id)).toBe(true)

            const grant = await Kinotic.permissions.grant({kind: SubjectKind.GROUP, id: savedGroup.id!}, 'project.viewer', onProject)
            try {
                expect(await Kinotic.permissions.findGrants(onProject)).toEqual([grant])
                const views = await Kinotic.permissions.explain(sally, 'can_view', onProject)
                expect(views.allowed).toBe(true)
                expect(views.through).toEqual([grant])
                const edits = await Kinotic.permissions.explain(sally, 'can_edit', onProject)
                expect(edits.allowed).toBe(false)

                // a group a grant holds stays until the grant is revoked
                await expect(Kinotic.permissions.deleteGroup(savedGroup.id!)).rejects.toThrow()
            } finally {
                await Kinotic.permissions.revoke(onProject, grant.id)
            }
            expect((await Kinotic.permissions.explain(sally, 'can_view', onProject)).allowed).toBe(false)
            await Kinotic.permissions.removeGroupMember(savedGroup.id!, SALLY_ID)
        } finally {
            await Kinotic.permissions.deleteGroup(savedGroup.id!).catch(() => undefined)
        }
    })

    it('lists what the caller holds', async () => {
        // the administrator holds everything in the organization, the project included
        expect(await Kinotic.permissions.listAccessible('project', 'can_view')).toContain(project.id)
    })
})
