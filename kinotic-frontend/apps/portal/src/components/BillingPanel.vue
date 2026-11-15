<template>
  <div class="flex flex-col gap-6">
    <p v-if="unavailable" class="text-sm text-surface-700 dark:text-surface-200">
      Billing is not available on this installation.
    </p>

    <template v-else-if="account">
      <section :class="CARD">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div>
            <div :class="LABEL">Current plan</div>
            <div class="mt-1 text-lg font-semibold text-surface-950 dark:text-surface-0">
              {{ currentPlan?.name ?? 'Free trial' }}
            </div>
            <p class="mt-1 text-sm text-surface-600 dark:text-surface-300">{{ standing }}</p>
          </div>
          <div class="flex items-center gap-2">
            <Tag :value="STATUS_LABELS[account.status]" :severity="STATUS_SEVERITY[account.status]" />
            <Button v-if="account.stripeCustomerId" label="Manage billing" size="small" outlined
                    :loading="opening === 'portal'" @click="openPortal" />
          </div>
        </div>
      </section>

      <section v-if="!account.stripeSubscriptionId">
        <h3 :class="HEADING">Choose a plan</h3>
        <p v-if="plans.length === 0" class="text-sm text-surface-600 dark:text-surface-300">No plans are offered yet.</p>
        <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <div v-for="plan in plans" :key="plan.id!" :class="[CARD, 'flex flex-col gap-3']">
            <div>
              <div class="font-semibold text-surface-950 dark:text-surface-0">{{ plan.name }}</div>
              <p v-if="plan.description" class="mt-1 text-sm text-surface-600 dark:text-surface-300">{{ plan.description }}</p>
            </div>
            <ul class="flex flex-col gap-1 text-sm text-surface-700 dark:text-surface-200">
              <li v-for="(component, index) in plan.components" :key="index">{{ describeComponent(plan, component) }}</li>
            </ul>
            <Button label="Subscribe" size="small" class="mt-auto self-start"
                    :loading="opening === plan.id" @click="subscribe(plan)" />
          </div>
        </div>
      </section>

      <section v-if="forecast" :class="CARD">
        <div :class="LABEL">This billing period</div>
        <div class="mt-1 text-sm text-surface-600 dark:text-surface-300">
          {{ formatDate(forecast.periodStart) }} to {{ formatDate(forecast.periodEnd) }}
        </div>
        <div class="mt-4 flex flex-wrap gap-8">
          <div>
            <div class="text-2xl font-semibold text-surface-950 dark:text-surface-0">
              {{ formatAmount(forecast.projectedAmount, forecast.currency) }}
            </div>
            <div class="text-xs text-surface-500">Forecast for the period, before tax</div>
          </div>
          <div v-for="descriptor in meters" :key="descriptor.meter">
            <div class="text-lg font-semibold text-surface-950 dark:text-surface-0">
              {{ formatQuantity(forecast.usageToDate[descriptor.meter]) }}
              <span class="text-sm font-normal text-surface-500">of about {{ formatQuantity(forecast.projectedUsage[descriptor.meter]) }}</span>
            </div>
            <div class="text-xs text-surface-500">{{ descriptor.displayName }} so far, in {{ descriptor.unit }}s</div>
          </div>
        </div>
      </section>

      <section :class="CARD">
        <div class="flex items-center justify-between">
          <div :class="LABEL">Usage, last {{ USAGE_DAYS }} days</div>
          <SelectButton v-if="chartMeter" v-model="chartMeter" :options="meters" option-label="displayName" :allow-empty="false" size="small" />
        </div>
        <div v-if="chartMeter" class="mt-4 flex h-32 items-end gap-[3px]" role="img"
             :aria-label="`Daily ${chartMeter.displayName} for the last ${USAGE_DAYS} days`">
          <div v-for="day in dailyUsage" :key="day.label" class="flex h-full flex-1 items-end"
               :title="`${day.label}: ${formatQuantity(day.quantities[chartMeter.meter])} ${chartMeter.unit}s`">
            <div class="w-full rounded-t bg-orange-400 dark:bg-orange-500"
                 :style="{ height: barHeight(day.quantities[chartMeter.meter]) }" />
          </div>
        </div>
        <div class="mt-1 flex justify-between text-[11px] text-surface-500">
          <span>{{ dailyUsage[0]?.label }}</span>
          <span>{{ dailyUsage[dailyUsage.length - 1]?.label }}</span>
        </div>
      </section>

      <section v-if="usageItems.length > 0">
        <h3 :class="HEADING">Usage by item, last {{ USAGE_DAYS }} days</h3>
        <DataTable :value="usageItems" size="small" row-group-mode="subheader" group-rows-by="category">
          <template #groupheader="{ data }">
            <span class="font-semibold">{{ CATEGORY_LABELS[data.category as UsageCategory] }}</span>
          </template>
          <Column field="subjectName" header="Item" />
          <Column field="meterName" header="Meter" />
          <Column header="Usage">
            <template #body="{ data }">{{ formatQuantity(data.quantity) }} {{ data.unit }}s</template>
          </Column>
        </DataTable>
      </section>

      <section>
        <h3 :class="HEADING">Invoices</h3>
        <p v-if="invoices.length === 0" class="text-sm text-surface-600 dark:text-surface-300">No invoices yet.</p>
        <DataTable v-else :value="invoices" size="small">
          <Column header="Invoice">
            <template #body="{ data }">{{ data.number ?? 'Draft' }}</template>
          </Column>
          <Column header="Date">
            <template #body="{ data }">{{ formatDate(data.created) }}</template>
          </Column>
          <Column header="Total">
            <template #body="{ data }">{{ formatAmount(data.total, data.currency) }}</template>
          </Column>
          <Column header="Status">
            <template #body="{ data }">
              <Tag v-if="data.status" :value="data.status" :severity="INVOICE_SEVERITY[data.status as InvoiceStatus]" />
            </template>
          </Column>
          <Column>
            <template #body="{ data }">
              <a v-if="data.hostedInvoiceUrl" :href="data.hostedInvoiceUrl" target="_blank" rel="noopener" class="text-sm underline">View</a>
            </template>
          </Column>
        </DataTable>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import SelectButton from 'primevue/selectbutton'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { Kinotic } from '@kinotic-ai/core'
import {
  BillingStatus, InvoiceStatus, PlanComponentType, UsageCategory,
  type BillingAccount, type BillingForecast, type BillingInvoice, type BillingPlan, type PlanComponent, type UsageMeter,
  type UsageMeterDescriptor, type UsageRecord
} from '@kinotic-ai/management-api'

const CARD = 'rounded-xl border border-surface-200 bg-surface-0 p-5 dark:border-surface-700 dark:bg-surface-900'
const LABEL = 'text-[11px] font-semibold uppercase tracking-wider text-surface-500'
const HEADING = 'mb-3 text-base font-semibold text-surface-950 dark:text-surface-0'
const USAGE_DAYS = 30
const DAY_MS = 86_400_000
// how long payment may be due before the organization is deactivated, as the system server enforces it
const GRACE_DAYS = 14

const CATEGORY_LABELS: Record<UsageCategory, string> = {
  [UsageCategory.COMPUTE]: 'Compute',
  [UsageCategory.STORAGE]: 'Storage'
}
const STATUS_LABELS: Record<BillingStatus, string> = {
  [BillingStatus.TRIALING]: 'Free trial',
  [BillingStatus.ACTIVE]: 'Active',
  [BillingStatus.PAST_DUE]: 'Payment due'
}
const STATUS_SEVERITY: Record<BillingStatus, string> = {
  [BillingStatus.TRIALING]: 'info',
  [BillingStatus.ACTIVE]: 'success',
  [BillingStatus.PAST_DUE]: 'danger'
}
const INVOICE_SEVERITY: Record<InvoiceStatus, string> = {
  [InvoiceStatus.DRAFT]: 'secondary',
  [InvoiceStatus.OPEN]: 'warn',
  [InvoiceStatus.PAID]: 'success',
  [InvoiceStatus.UNCOLLECTIBLE]: 'danger',
  [InvoiceStatus.VOID]: 'secondary'
}

const toast = useToast()

const unavailable = ref(false)
const account = ref<BillingAccount | null>(null)
const plans = ref<BillingPlan[]>([])
const forecast = ref<BillingForecast | null>(null)
const invoices = ref<BillingInvoice[]>([])
const usage = ref<UsageRecord[]>([])
const meters = ref<UsageMeterDescriptor[]>([])
const chartMeter = ref<UsageMeterDescriptor | null>(null)
const opening = ref<string | null>(null)

const meterOf = computed(() => new Map(meters.value.map(descriptor => [descriptor.meter, descriptor])))

const currentPlan = computed(() => plans.value.find(plan => plan.id === account.value?.planId) ?? null)

const standing = computed(() => {
  const current = account.value!
  let ret: string
  if (current.status === BillingStatus.TRIALING) {
    ret = `Your free trial ends ${formatDate(current.trialEndsAt)}.`
  } else if (current.status === BillingStatus.PAST_DUE) {
    const deactivation = new Date(Date.parse(current.pastDueSince!) + GRACE_DAYS * DAY_MS)
    ret = `Payment is due since ${formatDate(current.pastDueSince)}. Your microservices stop and your sites go offline `
        + `on ${formatDate(deactivation.toISOString())} unless it is paid.`
  } else {
    ret = current.currentPeriodEnd ? `Renews ${formatDate(current.currentPeriodEnd)}.` : 'Subscribed.'
  }
  return ret
})

// one bar per day, oldest first, summing that day's hourly records
const dailyUsage = computed(() => {
  const today = startOfDay(new Date())
  const days = Array.from({ length: USAGE_DAYS }, (_, index) => {
    const start = new Date(today.getTime() - (USAGE_DAYS - 1 - index) * DAY_MS)
    return { start, label: start.toLocaleDateString(undefined, { month: 'short', day: 'numeric' }), quantities: {} as Partial<Record<UsageMeter, number>> }
  })
  for (const record of usage.value) {
    const index = Math.floor((startOfDay(new Date(record.hour)).getTime() - days[0].start.getTime()) / DAY_MS)
    if (index >= 0 && index < days.length) {
      for (const line of record.lines) {
        days[index].quantities[line.meter] = (days[index].quantities[line.meter] ?? 0) + line.quantity
      }
    }
  }
  return days
})

const chartMax = computed(() => Math.max(...dailyUsage.value.map(day => day.quantities[chartMeter.value?.meter as UsageMeter] ?? 0), 0))

// each thing's total of each meter over the range, grouped by the meters' categories in the order of the meters
const usageItems = computed(() => {
  const totals = new Map<string, { category: UsageCategory, subjectName: string, meterName: string, unit: string, quantity: number, order: number }>()
  for (const record of usage.value) {
    for (const line of record.lines) {
      const descriptor = meterOf.value.get(line.meter)
      if (descriptor) {
        const key = `${line.meter}:${line.subjectId}`
        const item = totals.get(key) ?? {
          category: descriptor.category,
          subjectName: line.subjectName,
          meterName: descriptor.displayName,
          unit: descriptor.unit,
          quantity: 0,
          order: meters.value.indexOf(descriptor)
        }
        item.quantity += line.quantity
        totals.set(key, item)
      }
    }
  }
  const categories = Object.values(UsageCategory)
  return [...totals.values()].sort((a, b) => categories.indexOf(a.category) - categories.indexOf(b.category)
      || a.subjectName.localeCompare(b.subjectName) || a.order - b.order)
})

onMounted(async () => {
  try {
    account.value = await Kinotic.billing.findAccount()
  } catch {
    unavailable.value = true
    return
  }
  const to = new Date()
  const from = new Date(startOfDay(to).getTime() - (USAGE_DAYS - 1) * DAY_MS)
  const [described, offered, forecasted, listed, used] = await Promise.all([
    Kinotic.billing.findMeters(),
    Kinotic.billing.findPlans(),
    Kinotic.billing.findForecast(),
    Kinotic.billing.findInvoices(),
    Kinotic.billing.findUsage(from, to)
  ])
  meters.value = described
  chartMeter.value = described[0] ?? null
  plans.value = offered
  forecast.value = forecasted
  invoices.value = listed
  usage.value = used
})

async function subscribe(plan: BillingPlan): Promise<void> {
  await redirect(plan.id!, () => Kinotic.billing.createCheckoutSession(plan.id!))
}

async function openPortal(): Promise<void> {
  await redirect('portal', () => Kinotic.billing.createPortalSession())
}

// opens a Stripe page; the button stays loading while the browser leaves
async function redirect(key: string, open: () => Promise<string>): Promise<void> {
  opening.value = key
  try {
    window.location.assign(await open())
  } catch (error) {
    opening.value = null
    toast.add({ severity: 'error', summary: 'Billing', detail: (error as Error).message, life: 8000 })
  }
}

function describeComponent(plan: BillingPlan, component: PlanComponent): string {
  let ret: string
  if (component.type === PlanComponentType.FLAT) {
    ret = `${formatAmount(component.amount ?? 0, plan.currency)} per ${plan.interval.toLowerCase()}`
  } else {
    const descriptor = meterOf.value.get(component.meter!)
    const included = component.includedQuantity > 0 ? `, ${component.includedQuantity} included` : ''
    ret = `${descriptor?.displayName ?? component.meter}: ${formatAmount(component.unitAmount ?? 0, plan.currency, 4)} `
        + `per ${descriptor?.unit ?? 'unit'}${included}`
  }
  return ret
}

function barHeight(quantity: number | undefined): string {
  return chartMax.value > 0 ? `${Math.max(((quantity ?? 0) / chartMax.value) * 100, quantity ? 2 : 0)}%` : '0%'
}

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function formatDate(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) : ''
}

function formatQuantity(value: number | undefined): string {
  return (value ?? 0).toLocaleString(undefined, { maximumFractionDigits: 1 })
}

// amounts are in the currency's smallest unit, whose size Intl knows
function formatAmount(minorUnits: number, currency: string, maximumFractionDigits?: number): string {
  const format = new Intl.NumberFormat(undefined, { style: 'currency', currency: currency.toUpperCase() })
  const digits = format.resolvedOptions().maximumFractionDigits ?? 2
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: currency.toUpperCase(),
    maximumFractionDigits: Math.max(maximumFractionDigits ?? digits, digits)
  }).format(minorUnits / 10 ** digits)
}
</script>
