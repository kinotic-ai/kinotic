<script setup lang="ts">
/**
 * A large video poster framed by a red glow and an underline rule, for the one video a
 * section leads with. Clicking it emits `play`, as {@link VideoPoster} does.
 */
defineProps<{
  poster: string
  label: string
}>()

defineEmits<{ play: [] }>()
</script>

<template>
  <div class="featured">
    <div class="featured__glow featured__glow--top" />
    <div class="featured__glow featured__glow--left" />
    <div class="featured__glow featured__glow--right" />
    <div class="featured__glow featured__glow--bottom" />
    <VcVideoPoster class="featured__player" :poster="poster" :label="label" @play="$emit('play')" />
    <div class="featured__rule" />
  </div>
</template>

<style scoped>
.featured {
  position: relative;
}

.featured__glow {
  position: absolute;
  pointer-events: none;
  filter: blur(24px);
  background: radial-gradient(ellipse 50% 50% at 50% 50%, rgba(236, 31, 82, 0.2), transparent 70%);
}

.featured__glow--top {
  left: 6%;
  top: -56px;
  width: 88%;
  height: 200px;
  background: radial-gradient(ellipse 50% 50% at 50% 50%, rgba(236, 31, 82, 0.32), transparent 70%);
}

.featured__glow--left {
  left: -70px;
  top: 22%;
  width: 220px;
  height: 400px;
}

.featured__glow--right {
  right: -70px;
  top: 30%;
  width: 220px;
  height: 380px;
}

.featured__glow--bottom {
  left: 14%;
  bottom: -52px;
  width: 72%;
  height: 170px;
  background: radial-gradient(ellipse 50% 50% at 50% 50%, rgba(236, 31, 82, 0.26), transparent 70%);
}

.featured__player {
  aspect-ratio: 16 / 9;
  border: 1px solid rgba(255, 255, 255, 0.09);
  border-radius: 18px;
}

.featured__rule {
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.18), transparent);
}

@media (max-width: 768px) {
  .featured__player {
    border-radius: 12px;
  }
}
</style>
