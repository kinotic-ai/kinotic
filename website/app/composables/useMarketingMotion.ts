/**
 * Wires the shared motion behaviour of the marketing pages: elements carrying
 * `data-reveal` fade in as they scroll into view, and every SVG on the page is
 * frozen when the viewer has asked for reduced motion.
 *
 * Call once from a page's `setup`; the observer is released when that page
 * unmounts.
 */
export function useMarketingMotion() {
  let observer: IntersectionObserver | undefined

  onMounted(() => {
    // SMIL is outside the CSS animation model, so the `animation: none` rule in
    // app.css never reaches the <animate>/<animateMotion> elements in the section
    // illustrations, and `display: none` on them is ignored. Pausing each SVG's
    // own timeline is what actually freezes them at their first frame.
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      document.querySelectorAll('svg').forEach(svg => svg.pauseAnimations())
    }

    const targets = document.querySelectorAll('[data-reveal]')

    // Without an observer the reveal targets would stay at opacity 0 forever, so
    // show everything rather than render a blank page.
    if (!('IntersectionObserver' in window)) {
      targets.forEach(el => el.classList.add('in'))
      return
    }

    observer = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue
        entry.target.classList.add('in')
        observer?.unobserve(entry.target)
      }
    }, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' })

    targets.forEach(el => observer!.observe(el))
  })

  onBeforeUnmount(() => observer?.disconnect())
}
