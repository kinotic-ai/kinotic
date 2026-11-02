<script setup lang="ts">
interface Founder {
  name: string
  role: string
  roleTitle: string
  photo: string
  linkedIn: string
  bio: string
}

const founders: Founder[] = [
  {
    name: 'Navid Mitchell',
    role: 'CEO',
    roleTitle: 'Chief Executive Officer',
    photo: '/images/vc/founder-navid.webp',
    linkedIn: 'https://www.linkedin.com/in/navidmitchell/',
    bio: 'Two decades architecting cloud, embedded and data-infrastructure systems — with a conviction '
      + 'that quality software comes from building the platform right, not patching it later.',
  },
  {
    name: 'Nicholas Padilla',
    role: 'CTO',
    roleTitle: 'Chief Technology Officer',
    photo: '/images/vc/founder-nicholas.webp',
    linkedIn: 'https://www.linkedin.com/in/nicholaspadilla/',
    bio: 'Engineer\'s engineer — cloud, embedded and big-data systems across two decades, and a '
      + 'long-time collaborator with Navid on the platforms Kinotic grew out of.',
  },
  {
    name: 'Jimmy Gramblin',
    role: 'CCO',
    roleTitle: 'Chief Creative Officer',
    photo: '/images/vc/founder-jimmy.webp',
    linkedIn: 'https://www.linkedin.com/in/jimmygramblin/',
    bio: 'Design leader with 20+ years shaping products and brands for the world\'s leading companies. '
      + 'Believes good design is artful restraint — and that platforms should feel that way too.',
  },
]
</script>

<template>
  <section class="founders">
    <div class="k-wrap">
      <h2 class="k-heading founders__title" data-reveal>Meet the founders</h2>
      <div class="founders__grid">
        <article v-for="founder in founders" :key="founder.name" class="founder" data-reveal>
          <div class="founder__photo">
            <img :src="founder.photo" :alt="founder.name" loading="lazy">
            <a
              :href="founder.linkedIn"
              target="_blank"
              rel="noreferrer"
              class="founder__linkedin"
              :aria-label="`${founder.name} on LinkedIn`"
            >
              <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M20.45 20.45h-3.56v-5.57c0-1.33-.02-3.04-1.85-3.04-1.85 0-2.14 1.45-2.14 2.94v5.67H9.34V9h3.42v1.56h.05c.48-.9 1.64-1.85 3.37-1.85 3.6 0 4.27 2.37 4.27 5.46zM5.34 7.43a2.07 2.07 0 1 1 0-4.13 2.07 2.07 0 0 1 0 4.13zM7.12 20.45H3.55V9h3.57zM22.22 0H1.77C.79 0 0 .77 0 1.72v20.55C0 23.22.79 24 1.77 24h20.45c.98 0 1.78-.78 1.78-1.73V1.72C24 .77 23.2 0 22.22 0z" />
              </svg>
            </a>
          </div>
          <div class="founder__body">
            <h3 class="founder__name">{{ founder.name }}</h3>
            <div class="founder__role">
              <span>CO-FOUNDER /</span>
              <abbr class="founder__abbr" tabindex="0" :aria-label="founder.roleTitle">
                {{ founder.role }}
                <span class="founder__tip" aria-hidden="true">{{ founder.roleTitle }}</span>
              </abbr>
            </div>
            <p class="founder__bio">{{ founder.bio }}</p>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.founders {
  padding: 60px 0 40px;
}

.founders__title {
  font-size: clamp(27px, 3.2vw, 42px);
  text-align: center;
  margin-bottom: 56px;
}

.founders__grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.founder {
  border: 1px solid var(--color-k-border);
  background: var(--color-k-bg-card);
}

.founder__photo {
  position: relative;
  height: 360px;
  overflow: hidden;
  background: var(--color-k-bg-panel);
  border-bottom: 1px solid var(--color-k-border);
}

.founder__photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.founder__linkedin {
  position: absolute;
  top: 16px;
  right: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 4px;
  background: rgba(8, 9, 10, 0.7);
  backdrop-filter: blur(6px);
  color: #C9C9CE;
  transition: background 0.18s ease, color 0.18s ease, border-color 0.18s ease, transform 0.18s ease;
}

.founder__linkedin:hover {
  background: var(--color-k-mint);
  border-color: var(--color-k-mint);
  color: var(--color-k-bg);
  transform: scale(1.08);
}

.founder__linkedin svg {
  width: 15px;
  height: 15px;
}

.founder__body {
  padding: 36px 32px 40px;
}

.founder__name {
  margin: 0 0 10px;
  color: var(--color-k-text);
  font-family: var(--font-k-mono);
  font-size: 20px;
  font-weight: 400;
}

.founder__role {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 20px;
  color: var(--color-k-mint);
  font-family: var(--font-k-mono);
  font-size: 11px;
  letter-spacing: 0.14em;
}

.founder__abbr {
  position: relative;
  padding-bottom: 1px;
  border-bottom: 1px dotted rgba(40, 254, 180, 0.6);
  text-decoration: none;
  cursor: help;
  outline: none;
}

.founder__tip {
  position: absolute;
  left: 0;
  top: calc(100% + 10px);
  z-index: 5;
  padding: 7px 11px;
  border: 1px solid var(--color-k-border);
  border-radius: 4px;
  background: #17181C;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
  color: var(--color-k-text);
  font-family: var(--font-k-body);
  font-size: 12px;
  letter-spacing: 0;
  white-space: nowrap;
  opacity: 0;
  transform: translateY(-4px);
  pointer-events: none;
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.founder__abbr:hover .founder__tip,
.founder__abbr:focus-visible .founder__tip {
  opacity: 1;
  transform: translateY(0);
}

.founder__bio {
  margin: 0;
  color: var(--color-k-muted);
  font-size: 13.5px;
  line-height: 1.65;
}

/* A single card spans the column, so the photo keeps its own proportions instead of
   a fixed height that would crop it, and the card width caps how far it upscales. */
@media (max-width: 1020px) {
  .founders__grid {
    grid-template-columns: 1fr;
    max-width: 440px;
    margin: 0 auto;
  }

  .founder__photo {
    height: auto;
    aspect-ratio: 342 / 360;
  }
}
</style>
