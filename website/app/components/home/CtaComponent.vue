<script setup lang="ts">
const WAITLIST_URL = 'https://waitlister.me/s/eKQTYP_j3hGR'

const email = ref('')
const loading = ref(false)
const error = ref('')
const successTitle = ref('')
const successMessage = ref('')

async function joinWaitlist() {
  if (loading.value) return

  loading.value = true
  error.value = ''
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 15000)

  try {
    const response = await fetch(WAITLIST_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Accept: 'application/json',
      },
      body: new URLSearchParams({ email: email.value.trim() }),
      signal: controller.signal,
    })

    if (!response.ok) {
      error.value = response.status === 429
        ? 'Too many attempts. Please wait a little and try again.'
        : 'Unable to join the waitlist. Please try again.'
      return
    }

    const result = await response.json()
    if (result.success !== true) throw new Error('Signup was not accepted')

    if (result.is_pending_confirmation) {
      successTitle.value = 'Check your inbox.'
      successMessage.value = 'Confirm your email to join the Kinotic OS Cloud waitlist.'
    } else {
      successTitle.value = result.is_new_sign_up === false
        ? 'You’re already on the cloud waitlist.'
        : 'You’re on the cloud waitlist.'
      successMessage.value = 'We’ll email you when Kinotic OS Cloud access opens.'
    }
    email.value = ''
  } catch {
    error.value = 'Unable to join the waitlist. Please try again.'
  } finally {
    clearTimeout(timeout)
    loading.value = false
  }
}
</script>

<template>
  <section id="cloud-waitlist" class="cta" aria-labelledby="cloud-waitlist-title">
    <div id="cta" class="k-wrap cta__grid">
      <div class="cta__copy" data-reveal>
        <div class="k-eyebrow cta__eyebrow">
          <span>KINOTIC OS CLOUD · EARLY ACCESS</span>
        </div>
        <h2 id="cloud-waitlist-title" class="k-heading cta__title">Be first to build on Kinotic OS Cloud</h2>
        <p class="cta__description">Get early access to the cloud release.</p>
      </div>

      <div class="cta__signup" data-reveal>
        <div v-if="successTitle" class="cta__success" role="status" aria-live="polite">
          <h3>{{ successTitle }}</h3>
          <p>{{ successMessage }}</p>
        </div>
        <form v-else :action="WAITLIST_URL" method="post" :aria-busy="loading" @submit.prevent="joinWaitlist">
          <label for="cloud-waitlist-email" class="cta__label">Get notified when cloud access opens</label>
          <div class="cta__form-row">
            <input
              id="cloud-waitlist-email"
              v-model.trim="email"
              class="cta__email"
              name="email"
              type="email"
              placeholder="you@company.com"
              autocomplete="email"
              autocapitalize="none"
              :disabled="loading"
              :aria-invalid="error ? true : undefined"
              :aria-describedby="error ? 'cloud-waitlist-note cloud-waitlist-error' : 'cloud-waitlist-note'"
              required
            >
            <button type="submit" class="k-btn k-btn--mint cta__submit" :disabled="loading">
              {{ loading ? 'Joining…' : 'Join cloud waitlist ↗' }}
            </button>
          </div>
          <p v-if="error" id="cloud-waitlist-error" class="cta__error" role="alert">{{ error }}</p>
          <p id="cloud-waitlist-note" class="cta__note">
            We’ll email you when cloud access opens.
            <NuxtLink to="/privacy">Privacy policy</NuxtLink>
          </p>
        </form>
      </div>
    </div>
  </section>
</template>

<style scoped>
.cta {
  padding: 76px 0;
  border-top: 1px solid var(--color-k-border);
  border-bottom: 1px solid var(--color-k-border);
  background: radial-gradient(ellipse at 5% 90%, rgba(236, 31, 82, 0.08), transparent 65%), var(--color-k-bg-panel);
  scroll-margin-top: 150px;
}

.cta__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  gap: 64px;
}

.cta__copy,
.cta__signup {
  min-width: 0;
}

.cta__eyebrow {
  margin-bottom: 20px;
}

.cta__eyebrow > span {
  font-size: 12px;
  letter-spacing: 0.12em;
}

.cta__title {
  font-size: clamp(30px, 3.6vw, 48px);
  line-height: 1.1;
  letter-spacing: -0.02em;
  max-width: 480px;
}

.cta__description {
  margin: 18px 0 0;
  color: var(--color-k-body);
  font-size: 16px;
}

.cta__label {
  display: block;
  margin-bottom: 12px;
  font-size: 14px;
  color: var(--color-k-text);
}

.cta__form-row {
  display: flex;
  align-items: stretch;
  gap: 10px;
}

.cta__email {
  flex: 1;
  width: 100%;
  min-width: 0;
  padding: 14px;
  border: 1px solid #45454C;
  border-radius: 3px;
  background: var(--color-k-bg-chip);
  color: var(--color-k-text);
  font-family: var(--font-k-body);
  font-size: 16px;
}

.cta__email::placeholder {
  color: var(--color-k-muted);
}

.cta__email:focus-visible,
.cta__submit:focus-visible {
  outline: 2px solid var(--color-k-mint);
  outline-offset: 3px;
}

.cta__submit {
  justify-content: center;
  min-width: 200px;
  padding: 15px 18px;
  border: none;
  font-size: 12.5px;
  white-space: nowrap;
  cursor: pointer;
}

.cta__submit:disabled,
.cta__email:disabled {
  opacity: 0.65;
  cursor: wait;
}

.cta__note {
  margin: 12px 0 0;
  color: var(--color-k-muted);
  font-size: 12px;
  line-height: 1.6;
}

.cta__note a {
  color: var(--color-k-text);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.cta__error {
  margin: 12px 0 0;
  color: #FF8BA8;
  font-size: 14px;
}

.cta__success {
  padding: 24px;
  border: 1px solid #285942;
  border-radius: 4px;
  background: #102019;
}

.cta__success h3 {
  margin: 0;
  color: var(--color-k-mint);
  font-family: var(--font-k-display);
  font-size: 24px;
  font-weight: 500;
  line-height: 1.25;
}

.cta__success p {
  margin: 12px 0 0;
  color: var(--color-k-text);
  font-size: 14px;
  line-height: 1.6;
}

@media (max-width: 930px) {
  .cta__grid {
    grid-template-columns: 1fr;
    gap: 32px;
  }
}

@media (max-width: 600px) {
  .cta {
    padding: 52px 0;
  }

  .cta__form-row {
    flex-direction: column;
  }
}
</style>
