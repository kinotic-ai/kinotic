<script setup lang="ts">
const principles = [
  {
    number: '01',
    title: 'No black boxes',
    body: 'Generated code is real code — readable, versioned and yours. Verify every line. No hidden logic.',
  },
  {
    number: '02',
    title: 'Customer-managed compute',
    body: 'Forward-deploy the complete system onto your own Kubernetes. Your cluster, your network, your data — with a clear exit path.',
  },
  {
    number: '03',
    title: 'Governed by default',
    body: 'Cedar policies, document-level security, PII tracking on any field, and OIDC providers you already use.',
  },
]
</script>

<template>
  <section class="k-section trust">
    <div class="k-wrap">
      <div class="k-eyebrow trust__eyebrow" data-reveal>
        <span>TRUST THROUGH TRANSPARENCY</span>
      </div>
      <h2 class="k-heading trust__title" data-reveal>
        Inspectable code. Your cloud. Governed by design
      </h2>

      <div class="trust__panel" data-reveal>
        <div class="trust__visual">
          <div class="trust__glow" />
          <!-- A generated app's own console: the artifact the customer owns and can inspect. -->
          <div class="console">
            <div class="console__urlbar">
              <span class="console__url">booking-tool.kinotic.app</span>
            </div>
            <div class="console__body">
              <div class="console__rail">
                <span class="console__railicon console__railicon--active"><KinoticIcon name="ThLarge" :size="14" /></span>
                <span class="console__railicon"><KinoticIcon name="Database" :size="14" /></span>
                <span class="console__railicon"><KinoticIcon name="Refresh" :size="14" /></span>
                <span class="console__railicon"><KinoticIcon name="Cog" :size="14" /></span>
              </div>
              <div class="console__main">
                <div class="console__heading">
                  <div class="console__apptitle">A booking tool for my yoga stu…</div>
                  <div class="console__appid">app.booking.tool</div>
                </div>

                <div class="console__stats">
                  <div class="console__stat">
                    <div class="console__statlabel">RECORDS</div>
                    <div class="console__statvalue">1,284</div>
                  </div>
                  <div class="console__stat">
                    <div class="console__statlabel">API CALLS</div>
                    <div class="console__statvalue">48.2k</div>
                  </div>
                  <div class="console__stat">
                    <div class="console__statlabel">UPTIME</div>
                    <div class="console__statvalue console__statvalue--good">100%</div>
                  </div>
                </div>

                <div class="console__table">
                  <div class="console__thead">
                    <span>FIELD</span><span>TYPE</span>
                  </div>
                  <div class="console__trow">
                    <span>id</span><span>ID!</span>
                  </div>
                  <div class="console__trow">
                    <span>name</span><span>String!</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="trust__list">
          <div v-for="principle in principles" :key="principle.number" class="trust__row">
            <span class="trust__number">{{ principle.number }}</span>
            <span>
              <span class="trust__rowtitle">{{ principle.title }}</span>
              <span class="trust__rowbody">{{ principle.body }}</span>
            </span>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.trust {
  --k-section-padding: 80px 0 110px;
}

.trust__eyebrow {
  margin-bottom: 18px;
}

.trust__title {
  font-size: clamp(29px, 3.4vw, 44px);
  line-height: 1.16;
  margin-bottom: 56px;
  max-width: 640px;
}

.trust__panel {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border: 1px solid var(--k-border);
}

.trust__visual {
  position: relative;
  display: flex;
  align-items: center;
  overflow: hidden;
  padding: 44px 40px;
  background: var(--k-bg-panel);
  border-right: 1px solid var(--k-border);
}

.trust__glow {
  position: absolute;
  top: -60px;
  left: -40px;
  width: 420px;
  height: 280px;
  background: radial-gradient(ellipse, rgba(40, 254, 180, 0.09), transparent 70%);
}

.trust__list {
  display: flex;
  flex-direction: column;
}

.trust__row {
  flex: 1;
  display: flex;
  gap: 22px;
  padding: 34px 38px;
  border-bottom: 1px solid var(--k-border);
}

.trust__row:last-child {
  border-bottom: none;
}

.trust__number {
  flex: none;
  width: 46px;
  height: 46px;
  border-radius: 6px;
  background: rgba(40, 254, 180, 0.1);
  border: 1px solid rgba(40, 254, 180, 0.4);
  color: var(--k-mint);
  font-family: var(--k-font-mono);
  font-size: 15px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.trust__rowtitle {
  display: block;
  font-family: var(--k-font-mono);
  font-size: 16.5px;
  color: var(--k-text);
  margin-bottom: 7px;
}

.trust__rowbody {
  display: block;
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--k-muted);
}

/* ── Generated app console ── */
.console {
  position: relative;
  width: 100%;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  background: #0A0A0C;
  overflow: hidden;
  box-shadow: 0 26px 70px rgba(0, 0, 0, 0.5);
}

.console__urlbar {
  padding: 12px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
}

.console__url {
  display: block;
  font-family: var(--k-font-mono);
  font-size: 11.5px;
  color: var(--k-dim);
  background: var(--k-bg-chip);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 7px;
  padding: 8px 12px;
}

.console__body {
  display: flex;
}

.console__rail {
  flex: none;
  width: 44px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  padding: 12px 0;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
}

.console__railicon {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  color: var(--k-faint);
  display: flex;
  align-items: center;
  justify-content: center;
}

.console__railicon--active {
  background: rgba(236, 31, 82, 0.14);
  color: var(--k-red);
}

.console__main {
  flex: 1;
  padding: 14px 18px;
  min-width: 0;
}

.console__heading {
  margin-bottom: 12px;
}

.console__apptitle {
  font-family: var(--k-font-display);
  font-weight: 700;
  font-size: 15px;
  color: var(--k-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.console__appid {
  font-family: var(--k-font-mono);
  font-size: 10px;
  color: var(--k-faint);
  margin-top: 2px;
}

.console__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 7px;
  margin-bottom: 12px;
}

.console__stat {
  background: #121214;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 7px;
  padding: 8px 10px;
}

.console__statlabel {
  font-family: var(--k-font-mono);
  font-size: 8.5px;
  letter-spacing: 0.05em;
  color: var(--k-faint);
}

.console__statvalue {
  font-family: var(--k-font-display);
  font-weight: 700;
  font-size: 16px;
  color: var(--k-text);
  margin-top: 2px;
}

.console__statvalue--good {
  color: var(--k-mint);
}

.console__table {
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 7px;
  overflow: hidden;
}

.console__thead,
.console__trow {
  display: flex;
  padding: 7px 12px;
  font-family: var(--k-font-mono);
}

.console__thead {
  background: #121214;
  font-size: 9px;
  letter-spacing: 0.05em;
  color: var(--k-faint);
}

.console__trow {
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  font-size: 10.5px;
  color: #D6D6DB;
}

.console__trow span:last-child {
  color: var(--k-dim);
}

.console__thead span:first-child,
.console__trow span:first-child {
  flex: 2;
}

.console__thead span:last-child,
.console__trow span:last-child {
  flex: 1.4;
}

@media (max-width: 930px) {
  .trust__panel {
    grid-template-columns: 1fr;
  }

  .trust__visual {
    border-right: none;
    border-bottom: 1px solid var(--k-border);
  }
}

@media (max-width: 600px) {
  .trust__visual {
    padding: 26px 14px;
  }

  .trust__row {
    padding: 26px 20px;
  }
}
</style>
