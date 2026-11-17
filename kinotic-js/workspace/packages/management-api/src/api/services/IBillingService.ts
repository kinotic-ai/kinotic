import { MANAGEMENT_API_ZONE } from '@/api/Zones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { BillingAccount } from '@/api/model/billing/BillingAccount'
import type { BillingForecast } from '@/api/model/billing/BillingForecast'
import type { BillingInvoice } from '@/api/model/billing/BillingInvoice'
import type { BillingPlan } from '@/api/model/billing/BillingPlan'
import type { UsageMeterDescriptor } from '@/api/model/billing/UsageMeterDescriptor'
import type { UsageRecord } from '@/api/model/billing/UsageRecord'

/**
 * The caller's organization's billing for Kinotic Cloud: its account, the plans it may subscribe to, the Stripe pages
 * where it subscribes and manages payment, and its usage, forecast and invoices. Published only where billing is
 * enabled; elsewhere every call fails.
 */
export interface IBillingService {

    /**
     * The organization's billing account, created with a free trial the first time any organization member reads it.
     */
    findAccount(): Promise<BillingAccount>

    /**
     * The plans the organization may subscribe to, ordered by name.
     */
    findPlans(): Promise<BillingPlan[]>

    /**
     * Opens a Stripe Checkout page that subscribes the organization to the plan and returns to the portal's
     * organization settings. Only the page opened last can subscribe the organization.
     * @param planId one of the plans findPlans returns
     * @return the URL of the Checkout page; fails when the organization already subscribes
     */
    createCheckoutSession(planId: string): Promise<string>

    /**
     * Opens the Stripe Customer Portal, where the organization updates its payment method, downloads its invoices and
     * cancels its subscription.
     * @return the URL of the Customer Portal page; fails when the organization has never started a Checkout
     */
    createPortalSession(): Promise<string>

    /**
     * Every meter usage is recorded under, in the order to show them.
     */
    findMeters(): Promise<UsageMeterDescriptor[]>

    /**
     * The organization's hourly usage, a line for each meter and thing that used it, for the hours starting from from
     * up to to, oldest first. An hour in which the organization used nothing has no record.
     * @param from the start of the range
     * @param to the end of the range, at most 93 days after from
     */
    findUsage(from: Date, to: Date): Promise<UsageRecord[]>

    /**
     * What the organization's current billing period is expected to cost, or null when the organization has no
     * subscription or its free trial is still running.
     */
    findForecast(): Promise<BillingForecast | null>

    /**
     * The organization's last 24 invoices, newest first; none when it has never started a Checkout.
     */
    findInvoices(): Promise<BillingInvoice[]>

}

export class BillingService implements IBillingService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.billing.BillingService`)
    }

    public findAccount(): Promise<BillingAccount> {
        return this.serviceProxy.invoke('findAccount', [])
    }

    public findPlans(): Promise<BillingPlan[]> {
        return this.serviceProxy.invoke('findPlans', [])
    }

    public createCheckoutSession(planId: string): Promise<string> {
        return this.serviceProxy.invoke('createCheckoutSession', [planId])
    }

    public createPortalSession(): Promise<string> {
        return this.serviceProxy.invoke('createPortalSession', [])
    }

    public findMeters(): Promise<UsageMeterDescriptor[]> {
        return this.serviceProxy.invoke('findMeters', [])
    }

    public findUsage(from: Date, to: Date): Promise<UsageRecord[]> {
        return this.serviceProxy.invoke('findUsage', [from.toISOString(), to.toISOString()])
    }

    public findForecast(): Promise<BillingForecast | null> {
        return this.serviceProxy.invoke('findForecast', [])
    }

    public findInvoices(): Promise<BillingInvoice[]> {
        return this.serviceProxy.invoke('findInvoices', [])
    }

}
