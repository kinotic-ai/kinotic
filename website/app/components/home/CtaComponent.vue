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
        // JSON selects an inline response; form-encoded requests return a redirect.
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({ email: email.value.trim() }),
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

    const redirectUrl = result.redirect_url ?? result.redirectUrl
    const pendingConfirmation = result.is_pending_confirmation
      || (typeof redirectUrl === 'string'
        && new URL(redirectUrl, WAITLIST_URL).pathname.startsWith('/confirm-pending/'))

    if (pendingConfirmation) {
      successTitle.value = 'Check your inbox.'
      successMessage.value = 'Confirm your email to join the Kinotic OS Cloud waitlist.'
    } else {
      successTitle.value = result.is_new_sign_up === false || result.message === 'Already signed up'
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
      <div class="cta__copy">
        <div class="k-eyebrow cta__eyebrow">
          <span>KINOTIC OS CLOUD · EARLY ACCESS</span>
        </div>
        <h2 id="cloud-waitlist-title" class="k-heading cta__title">Be first to build on Kinotic OS Cloud</h2>
        <p class="cta__description">Get early access to the cloud release.</p>

        <div class="cta__signup">
          <div v-if="successTitle" class="cta__success" role="status" aria-live="polite">
            <h3>{{ successTitle }}</h3>
            <p>{{ successMessage }}</p>
          </div>
          <form v-else :action="WAITLIST_URL" method="post" :aria-busy="loading" @submit.prevent="joinWaitlist">
            <label for="cloud-waitlist-email" class="cta__label">Email address</label>
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

      <div class="cta__visual" data-reveal>
        <div class="cta__glow cta__glow--red" />
        <div class="cta__glow cta__glow--mint" />
        <svg class="cta__cubes" viewBox="0 0 560 470" fill="none" preserveAspectRatio="xMidYMid meet" aria-hidden="true">
          <g class="cube cube--fast">
            <path d="M 96 92 L 141 71 L 186 92 L 141 113 Z" fill="#B0324C" />
            <path d="M 96 92 L 96 142 L 141 163 L 141 113 Z" fill="#7A1D33" />
            <path d="M 186 92 L 186 142 L 141 163 L 141 113 Z" fill="#96263F" />
          </g>
          <g class="cube cube--fast" style="animation-delay: -3s;">
            <path d="M 428 122 L 466 104 L 504 122 L 466 140 Z" fill="#4BF2B0" />
            <path d="M 428 122 L 428 164 L 466 182 L 466 140 Z" fill="#0E9E68" />
            <path d="M 504 122 L 504 164 L 466 182 L 466 140 Z" fill="#17B87C" />
          </g>
          <g class="cube">
            <path d="M 168 236 L 258 194 L 348 236 L 258 278 Z" fill="#C6455C" />
            <path d="M 168 236 L 168 336 L 258 378 L 258 278 Z" fill="#8E2038" />
            <path d="M 348 236 L 348 336 L 258 378 L 258 278 Z" fill="#A62B44" />
          </g>
          <g class="cube" style="animation-delay: -2s;">
            <path d="M 448 344 L 480 329 L 512 344 L 480 359 Z" fill="#8E3247" />
            <path d="M 448 344 L 448 378 L 480 393 L 480 359 Z" fill="#5E1A29" />
            <path d="M 512 344 L 512 378 L 480 393 L 480 359 Z" fill="#752338" />
          </g>
        </svg>
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

.cta__signup {
  margin-top: 32px;
}

.cta__visual {
  position: relative;
  height: 415px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cta__glow {
  position: absolute;
  filter: blur(6px);
}

.cta__glow--red {
  left: 12%;
  top: 18%;
  width: 520px;
  height: 420px;
  background: radial-gradient(ellipse 50% 50% at 50% 50%, rgba(236, 31, 82, 0.22), transparent 70%);
}

.cta__glow--mint {
  right: -6%;
  bottom: 2%;
  width: 460px;
  height: 400px;
  background: radial-gradient(ellipse 50% 50% at 50% 50%, rgba(40, 254, 180, 0.12), transparent 70%);
}

.cta__cubes {
  position: relative;
  width: 100%;
  height: 100%;
}

.cube {
  transform-box: fill-box;
  transform-origin: center;
  animation: cta-float 7s ease-in-out infinite;
}

.cube--fast {
  animation: cta-tumble 9s ease-in-out infinite;
}

@keyframes cta-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-12px); }
}

@keyframes cta-tumble {
  0%, 100% { transform: translateY(0) rotate(-2deg); }
  50% { transform: translateY(-22px) rotate(2deg); }
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

  .cta__visual {
    height: 330px;
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
