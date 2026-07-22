import { describe, it, expect } from "vitest"
import { ServiceIdentifier } from "../src/api/ServiceIdentifier"

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

        it('rejects a name containing a dot', () => {
            expect(() => new ServiceIdentifier('com.example', 'Svc.Extra', 'api')).toThrow()
        })

        it('rejects a namespace containing an underscore', () => {
            expect(() => new ServiceIdentifier('com.my_example', 'Svc', 'api')).toThrow()
        })
    })
  })
})
