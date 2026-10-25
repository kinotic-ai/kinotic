import { describe, expect, it } from 'bun:test'
import { OidcProviderType } from '../src/api/model/security/OidcProviderType'
import { ORGANIZATION_OIDC_PROVIDERS, admitsEveryDirectory, isProviderAccountValid } from '../src/api/model/security/OrganizationOidcProviders'

describe('isProviderAccountValid', () => {

    it('offers organizations Google, Entra and Okta', () => {
        expect(ORGANIZATION_OIDC_PROVIDERS.map(offered => offered.provider))
            .toEqual([OidcProviderType.GOOGLE, OidcProviderType.AZURE_AD, OidcProviderType.OKTA])
    })

    it('accepts an Okta domain on each of Okta\'s own domains and nothing that leaves them', () => {
        for (const domain of ['acme.okta.com', 'acme-dev.oktapreview.com', 'acme.okta-emea.com']) {
            expect(isProviderAccountValid(OidcProviderType.OKTA, domain)).toBe(true)
        }
        for (const domain of ['acme.okta.com/x', 'evil.example', 'acme.okta.com.evil.example', 'Acme.okta.com', 'https://acme.okta.com', '', null]) {
            expect(isProviderAccountValid(OidcProviderType.OKTA, domain)).toBe(false)
        }
    })

    it('accepts an Entra directory id, or common or organizations, alone', () => {
        expect(isProviderAccountValid(OidcProviderType.AZURE_AD, '9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718')).toBe(true)
        expect(isProviderAccountValid(OidcProviderType.AZURE_AD, 'common')).toBe(true)
        expect(isProviderAccountValid(OidcProviderType.AZURE_AD, 'organizations')).toBe(true)
        expect(isProviderAccountValid(OidcProviderType.AZURE_AD, 'contoso.onmicrosoft.com')).toBe(false)
        expect(isProviderAccountValid(OidcProviderType.AZURE_AD, '9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718x')).toBe(false)
        expect(isProviderAccountValid(OidcProviderType.AZURE_AD, 'Common')).toBe(false)
    })

    it('tells the accounts that admit every directory from one directory', () => {
        expect(admitsEveryDirectory(OidcProviderType.AZURE_AD, 'common')).toBe(true)
        expect(admitsEveryDirectory(OidcProviderType.AZURE_AD, 'organizations')).toBe(true)
        expect(admitsEveryDirectory(OidcProviderType.AZURE_AD, '9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718')).toBe(false)
        expect(admitsEveryDirectory(OidcProviderType.AZURE_AD, null)).toBe(false)
        expect(admitsEveryDirectory(OidcProviderType.OKTA, 'common')).toBe(false)
    })

    it('takes no account for Google, and refuses every kind organizations may not configure', () => {
        expect(isProviderAccountValid(OidcProviderType.GOOGLE, null)).toBe(true)
        expect(isProviderAccountValid(OidcProviderType.GOOGLE, ' ')).toBe(true)
        expect(isProviderAccountValid(OidcProviderType.GOOGLE, 'acme.com')).toBe(false)
        expect(isProviderAccountValid(OidcProviderType.GITHUB, null)).toBe(false)
    })
})
