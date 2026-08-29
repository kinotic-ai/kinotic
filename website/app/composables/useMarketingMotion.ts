/**
 * Wires the shared motion behaviour of the marketing pages: elements carrying
 * `data-reveal` fade in as they scroll into view, and the illustrations inside
 * them hold their first frame until that happens. Under reduced motion every
 * SVG on the page stays frozen instead.
 *
 * Call once from a page's `setup`; the observer is released when that page
 * unmounts.
 */
export function useMarketingMotion() {
  let observer: IntersectionObserver | undefined

  onMounted(() => {
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches

    // SMIL sits outside the CSS animation model, so neither the `animation: none`
    // rule nor the `animation-play-state` gate in app.css reaches the
    // <animate>/<animateMotion> elements in the section illustrations. Pausing
    // each SVG's own timeline is what actually holds them, and seeking to zero
    // discards the time that ran between page load and hydration.
    const held = document.querySelectorAll<SVGSVGElement>(reduced ? 'svg' : '[data-reveal] svg')
    held.forEach((svg) => {
      svg.pauseAnimations()
      svg.setCurrentTime(0)
    })

    // Reduced motion never reaches this, so those timelines stay at frame one.
    const reveal = (target: Element) => {
      target.classList.add('in')
      if (!reduced) {
        target.querySelectorAll<SVGSVGElement>('svg').forEach(svg => svg.unpauseAnimations())
      }
    }

    const targets = document.querySelectorAll('[data-reveal]')

    // Without an observer the reveal targets would stay at opacity 0 forever, so
    // show everything rather than render a blank page.
    if (!('IntersectionObserver' in window)) {
      targets.forEach(reveal)
      return
    }

    observer = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue
        reveal(entry.target)
        observer?.unobserve(entry.target)
      }
    }, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' })

    targets.forEach(el => observer!.observe(el))
  })

  onBeforeUnmount(() => observer?.disconnect())
}
