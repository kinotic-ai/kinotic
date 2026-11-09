<script setup lang="ts">
interface Row {
  feature: string
  elastic: string | boolean
  enterprise: string | boolean
}

const groups: { title?: string, rows: Row[] }[] = [
  {
    title: 'Kinotic OS',
    rows: [
      { feature: 'Services, persistence and multi-tenancy, generated from your application model', elastic: true, enterprise: true },
      { feature: 'Push-to-deploy, with isolated builds and environments', elastic: true, enterprise: true },
      { feature: 'Logs, traces and metrics for every application', elastic: true, enterprise: true },
      { feature: 'MCP tools, so AI agents can build and operate on Kinotic', elastic: true, enterprise: true },
      { feature: 'Multi-node clusters for high availability', elastic: true, enterprise: true },
      { feature: 'Sign-in with Google and Microsoft accounts', elastic: true, enterprise: true },
    ],
  },
  {
    title: 'Enterprise features',
    rows: [
      { feature: 'Sign-in through your own Okta or Microsoft Entra directory, for your team, your applications and each of their tenants', elastic: false, enterprise: true },
      { feature: 'Secrets kept in your own Azure Key Vault', elastic: false, enterprise: true },
      { feature: 'New Enterprise features in your edition as we ship them', elastic: false, enterprise: true },
    ],
  },
  {
    rows: [
      { feature: 'Support', elastic: 'Community', enterprise: 'By plan' },
    ],
  },
]
</script>

<template>
  <section class="k-section compare" aria-labelledby="compare-title">
    <div class="k-wrap">
      <div class="k-eyebrow compare__eyebrow" data-reveal>
        <span>COMPARE LICENSES</span>
      </div>
      <h2 id="compare-title" class="k-heading compare__title" data-reveal>
        Every plan includes every Enterprise feature. Your plan only reflects your size.
      </h2>

      <div class="compare__frame" data-reveal>
        <table class="compare__table">
          <colgroup>
            <col class="compare__col-feature">
            <col class="compare__col-license">
            <col class="compare__col-license">
          </colgroup>
          <thead>
            <tr>
              <th scope="col"><span class="compare__sr">Feature</span></th>
              <th scope="col" class="compare__license">
                Elastic License 2.0
              </th>
              <th scope="col" class="compare__license compare__license--ee">
                Enterprise
                <span class="compare__license-note">Every plan, Free too</span>
              </th>
            </tr>
          </thead>
          <tbody v-for="group in groups" :key="group.rows[0]!.feature">
            <tr v-if="group.title" class="compare__group">
              <th scope="colgroup" colspan="3">{{ group.title }}</th>
            </tr>
            <tr v-for="row in group.rows" :key="row.feature">
              <th scope="row" class="compare__feature">{{ row.feature }}</th>
              <td v-for="license in (['elastic', 'enterprise'] as const)" :key="license" class="compare__cell">
                <template v-if="row[license] === true">
                  <span class="compare__check" aria-hidden="true">✓</span><span class="compare__sr">Included</span>
                </template>
                <span v-else-if="row[license] === false" class="compare__sr">Not included</span>
                <span v-else class="compare__text">{{ row[license] }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <p class="compare__footnote" data-reveal>
        Kinotic RPC is available under the Apache License 2.0.
      </p>
    </div>
  </section>
</template>

<style scoped>
.compare {
  --k-section-padding: 96px 0;
  border-top: 1px solid var(--color-k-border);
}

.compare__eyebrow {
  margin-bottom: 20px;
}

.compare__title {
  max-width: 720px;
  margin-bottom: 48px;
  font-size: clamp(28px, 3.2vw, 42px);
  line-height: 1.15;
  text-wrap: balance;
}

.compare__frame {
  min-width: 0;
}

.compare__table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
}

.compare__col-feature {
  width: auto;
}

.compare__col-license {
  width: 200px;
}

.compare__table thead th {
  padding: 0 16px 18px;
  border-bottom: 2px solid var(--color-k-border);
  vertical-align: bottom;
}

.compare__license {
  color: var(--color-k-heading);
  font-family: var(--font-k-display);
  font-size: 17px;
  font-weight: 600;
  text-align: center;
}

.compare__table thead .compare__license--ee {
  border-bottom-color: var(--color-k-mint);
  color: var(--color-k-mint);
}

.compare__license-note {
  display: block;
  margin-top: 4px;
  color: var(--color-k-muted);
  font-family: var(--font-k-mono);
  font-size: 11.5px;
  font-weight: 400;
}

.compare__group th {
  padding: 32px 0 10px;
  color: var(--color-k-muted);
  font-family: var(--font-k-mono);
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0.12em;
  text-align: left;
  text-transform: uppercase;
}

.compare__table tbody tr:not(.compare__group) {
  border-bottom: 1px solid var(--color-k-border);
}

.compare__feature {
  padding: 14px 16px 14px 0;
  color: var(--color-k-body);
  font-size: 15px;
  font-weight: 400;
  line-height: 1.5;
  text-align: left;
}

.compare__cell {
  padding: 14px 16px;
  text-align: center;
  vertical-align: middle;
}

.compare__check {
  color: var(--color-k-mint);
  font-size: 16px;
}

.compare__text {
  color: var(--color-k-body);
  font-family: var(--font-k-mono);
  font-size: 13px;
}

.compare__footnote {
  margin: 32px 0 0;
  color: var(--color-k-muted);
  font-family: var(--font-k-mono);
  font-size: 13px;
}

.compare__sr {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

@media (max-width: 700px) {
  .compare__col-license {
    width: 92px;
  }

  .compare__table thead th {
    padding: 0 6px 14px;
  }

  .compare__license {
    font-size: 14px;
  }

  .compare__feature {
    font-size: 14px;
  }

  .compare__cell {
    padding: 12px 6px;
  }

  .compare__text {
    font-size: 11.5px;
  }
}
</style>
