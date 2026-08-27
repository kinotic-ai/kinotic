/** Marks a block whose animations have been released; see app.css for the hold. */
const MOTION_RUN = 'k-motion-run'

/** The SMIL timelines held on one block, keyed by the block that releases them. */
type HeldSmil = Map<Element, SVGSVGElement[]>

/**
 * The block an animated node is released with: its nearest containing block,
 * never the node itself. Releasing an ancestor rather than the node frees
 * siblings rendered later — the code panes replace their rows on every slide.
 */
function blockOf(node: Element, root: Element): Element {
  return node.parentElement?.closest('div, article, section') ?? root
}

/**
 * Collects everything animating under `root` and stops it, returning the blocks
 * to watch and the SVG timelines each one owns.
 */
function holdAnimations(root: Element): { blocks: Set<Element>, smil: HeldSmil } {
  const smil: HeldSmil = new Map()
  const blocks = new Set<Element>()

  // SMIL is outside the CSS animation model, so the paused rule in app.css never
  // reaches <animate>/<animateMotion>. Each SVG's own timeline has to be stopped.
  for (const svg of root.querySelectorAll('svg')) {
    if (!svg.querySelector('animate, animateMotion, animateTransform')) continue
    svg.pauseAnimations()
    // Rewound as well as stopped: the timeline ran from first paint until this
    // call, so a held SVG would otherwise resume from however long hydration took.
    svg.setCurrentTime(0)
    const block = blockOf(svg, root)
    smil.set(block, [...(smil.get(block) ?? []), svg])
    blocks.add(block)
  }

  for (const node of root.querySelectorAll('*')) {
    if (getComputedStyle(node).animationName !== 'none') blocks.add(blockOf(node, root))
  }

  return { blocks, smil }
}

/** Starts a block's animations, which holdAnimations() left at their first frame. */
function release(block: Element, smil: HeldSmil) {
  block.classList.add(MOTION_RUN)
  for (const svg of smil.get(block) ?? []) svg.unpauseAnimations()
}

/**
 * Wires the shared motion behaviour of the marketing pages: elements carrying
 * `data-reveal` fade in as they scroll into view, and every animation is held at
 * its first frame until the block containing it appears — or held for good, when
 * the viewer has asked for reduced motion.
 *
 * Call once from a page's `setup`; the observers are released when that page
 * unmounts.
 */
export function useMarketingMotion() {
  let reveal: IntersectionObserver | undefined
  let motion: IntersectionObserver | undefined

  onMounted(() => {
    const observable = 'IntersectionObserver' in window
    const targets = document.querySelectorAll('[data-reveal]')

    // Without an observer the reveal targets would stay at opacity 0 forever, so
    // show everything rather than render a blank page.
    if (!observable) {
      targets.forEach(el => el.classList.add('in'))
    }
    else {
      reveal = new IntersectionObserver((entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue
          entry.target.classList.add('in')
          reveal?.unobserve(entry.target)
        }
      }, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' })

      targets.forEach(el => reveal!.observe(el))
    }

    const root = document.querySelector('.k-marketing')
    if (!root) return

    const { blocks, smil } = holdAnimations(root)

    // Reduced motion keeps everything held: app.css stops the CSS animations
    // outright, and leaving the SVG timelines paused freezes the SMIL with them.
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

    // With nothing to report visibility, the hold would never lift.
    if (!observable) {
      blocks.forEach(block => release(block, smil))
      return
    }

    motion = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue
        release(entry.target, smil)
        motion?.unobserve(entry.target)
      }
    }, { threshold: 0, rootMargin: '0px 0px -8% 0px' })

    blocks.forEach(block => motion!.observe(block))
  })

  onBeforeUnmount(() => {
    reveal?.disconnect()
    motion?.disconnect()
  })
}
