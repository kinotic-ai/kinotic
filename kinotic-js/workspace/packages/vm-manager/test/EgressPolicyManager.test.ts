import { describe, expect, it } from 'bun:test'
import { EgressPolicyManager } from '@/internal/api/network/EgressPolicyManager'

// Rule application needs iptables and root, so what is exercised here is the part that runs
// before any of that: the guard deciding whether a destination can be enforced at all, and
// whether this node enforces egress. Both are reached on any machine.
describe('EgressPolicyManager', () => {

    it('refuses a hostname, which egress rules cannot match', () => {
        const egress = new EgressPolicyManager()

        expect(() => egress.apply('wl-1', '172.17.0.2', ['api.stripe.com']))
            .toThrow(/not an IPv4 address or CIDR/)
    })

    it('names the workload whose address is unusable', () => {
        const egress = new EgressPolicyManager()

        expect(() => egress.apply('wl-2', 'not-an-address', []))
            .toThrow(/workload wl-2/)
    })

    it('accepts addresses and CIDRs', () => {
        const egress = new EgressPolicyManager()

        // Reaching past validation means the shape was accepted; whether the rules land
        // depends on iptables, which is the node's business rather than this guard's
        expect(() => egress.apply('wl-4', '172.17.0.2', ['10.0.1.0/24', '10.0.2.7', '10.0.3.0/28']))
            .not.toThrow(/not an IPv4 address or CIDR/)
    })

    it('honours a protected address the policy names outright', () => {
        const egress = new EgressPolicyManager()

        // Only the server can set a workload's policy, so naming it is a decision it made
        expect(() => egress.apply('wl-5', '172.17.0.2', ['169.254.169.254/32']))
            .not.toThrow(/denies every workload/)
        expect(() => egress.apply('wl-5b', '172.17.0.2', ['169.254.169.254']))
            .not.toThrow(/denies every workload/)
    })

    it('refuses a range that covers a protected address without naming it', () => {
        const egress = new EgressPolicyManager()

        expect(() => egress.apply('wl-6', '172.17.0.2', ['169.254.0.0/16']))
            .toThrow(/covers 169\.254\.169\.254 without naming it/)
        expect(() => egress.apply('wl-7', '172.17.0.2', ['0.0.0.0/0']))
            .toThrow(/without naming it/)
        expect(() => egress.apply('wl-8', '172.17.0.2', ['168.63.129.0/24']))
            .toThrow(/covers 168\.63\.129\.16 without naming it/)
    })

    it('reports that a node without the default-deny marker does not enforce egress', () => {
        // The marker is written by node provisioning, so a developer machine never has one
        expect(new EgressPolicyManager().enforces()).toBe(false)
    })

})
