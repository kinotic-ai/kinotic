import { describe, it, expect } from "vitest"
import { ServiceIdentifier } from "../src/api/ServiceIdentifier"

// Pure construction guards, no gateway needed, so this runs regardless of USE_GATEWAY_DOCKER.
describe('Kinotic JS', () => {
  describe('packages/core', () => {
    describe('ServiceIdentifier construction', () => {

        it('builds a zone prefixed cri', () => {
            const identifier = new ServiceIdentifier('org.kinotic.os.api.services.iam', 'MemberService', 'os-api')
            identifier.version = '1.0.0'
            expect(identifier.qualifiedName()).toBe('os-api.org.kinotic.os.api.services.iam.MemberService')
            expect(identifier.cri().raw()).toBe('srv://os-api.org.kinotic.os.api.services.iam.MemberService#1.0.0')
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
