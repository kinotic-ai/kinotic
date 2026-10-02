/**
 * Kinotic's colour marks: a light tint with the strong shade on top, for icon tiles and
 * initials tiles alike, so every coloured mark on a page reads as one set.
 */
export const TINTS = {
  blue: 'bg-blue-100 text-blue-600 dark:bg-blue-500/15 dark:text-blue-300',
  green: 'bg-green-100 text-green-700 dark:bg-green-500/15 dark:text-green-300',
  orange: 'bg-orange-100 text-orange-600 dark:bg-orange-500/15 dark:text-orange-300',
  purple: 'bg-purple-100 text-purple-600 dark:bg-purple-500/15 dark:text-purple-300',
  red: 'bg-red-100 text-red-600 dark:bg-red-500/15 dark:text-red-300',
  sky: 'bg-sky-100 text-sky-600 dark:bg-sky-500/15 dark:text-sky-300',
  surface: 'bg-surface-100 text-surface-400 dark:bg-surface-800 dark:text-surface-500'
} as const
