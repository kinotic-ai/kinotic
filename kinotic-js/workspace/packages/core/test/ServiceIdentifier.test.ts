import { describe, it, expect } from "vitest"
import { ServiceIdentifier } from "../src/api/ServiceIdentifier"
import { createCRI } from "../src/api/event/CRI"

// Pure construction guards, no gateway needed, so this runs regardless of USE_GATEWAY_DOCKER.
describe('Kinotic JS', () => {
  describe('packages/core', () => {
    describe('ServiceIdentifier construction', () => {

        it('builds a zone prefixed cri', () => {
            const identifier = new ServiceIdentifier('org.kinotic.os.api.services.iam', 'MemberService', 'os-api')
            identifier.version = '1.0.0'
            expect(identifier.qualifiedName()).toBe('os-api~org.kinotic.os.api.services.iam.memberservice')
            expect(identifier.cri().raw()).toBe('srv://os-api~org.kinotic.os.api.services.iam.memberservice#1.0.0')
            expect(identifier.cri().zone()).toBe('os-api')
            expect(identifier.cri().resourceName()).toBe('org.kinotic.os.api.services.iam.memberservice')
        })

        it('rejects a name or namespace containing the zone delimiter', () => {
            expect(() => new ServiceIdentifier('com.example', 'Sv~c', 'api')).toThrow()
            expect(() => new ServiceIdentifier('com.exa~mple', 'Svc', 'api')).toThrow()
        })

        it('rejects a scope containing the zone delimiter', () => {
            expect(() => createCRI('srv://evil~x@api~com.example.svc/save#1.0.0')).toThrow()
            expect(() => createCRI('srv', 'evil~x', 'api~com.example.svc', 'save', '1.0.0')).toThrow()
        })

        it('rejects a second zone delimiter in the host part', () => {
            expect(() => createCRI('srv://api~com.example.svc~extra/save#1.0.0')).toThrow()
            expect(() => createCRI('srv', null, 'api~com.example.svc~extra', 'save', '1.0.0')).toThrow()
        })

        it('rejects stray scope delimiters', () => {
            expect(() => createCRI('srv', 'a@b', 'api~com.example.svc', 'save', '1.0.0')).toThrow()
            expect(() => createCRI('srv', null, 'x@api~com.example.svc', 'save', '1.0.0')).toThrow()
            expect(() => createCRI('srv://a@b@api~com.example.svc/save#1.0.0')).toThrow()
        })

        it('rejects a name containing a dot', () => {
            expect(() => new ServiceIdentifier('com.example', 'Svc.Extra', 'api')).toThrow()
        })

        it('rejects a namespace containing an underscore', () => {
            expect(() => new ServiceIdentifier('com.my_example', 'Svc', 'api')).toThrow()
        })
    })
  })
})
