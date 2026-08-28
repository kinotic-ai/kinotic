---
name: dca
description: Don't Change Anything - discussion-only mode. Use whenever the user invokes /dca, or asks for analysis with phrases like "dca", "don't change anything", "without making any changes", "do not change yet", "before we make any changes", "lets just discuss", or asks a design question while explicitly holding off on implementation. The deliverable is the assessment itself; no repository or external state may be modified until the user explicitly asks for the change.
---

# DCA - Don't Change Anything

The user is thinking through a design with you. In this repo, discussion is how decisions
converge before any code moves: a premature edit can anchor the conversation to an
implementation nobody has agreed to, burn a review cycle, and force the user to unwind work
instead of weighing options. When this mode is active, the deliverable is the assessment -
delivered in chat, grounded in the real code.

## What stays allowed

Investigate as deeply as the question deserves. Reading, grepping, and inspecting git history
are always fine, and so is anything that only produces build output: compiling, running
tests, type-checking. Evidence beats speculation - an answer grounded in a failing test or a
disassembled dependency is worth the commands it took.

## What is off limits

Anything that alters the repository or the world outside it:

- Editing, creating, or deleting files in the repository (scratch files outside the repo are fine)
- Every git mutation: add, commit, push, branch, rebase, tag
- GitHub state: PRs, comments, reviews, labels
- Publishing anything: packages, artifacts, deployments

## How to answer

Follow the repo's "explain with code, not prose" convention. Quote the current code with
`path:line` references, show proposals as code blocks or diffs in chat, and put options side
by side so the user can compare them directly. End with a recommendation, not a menu.
Proposed code in chat is the point of this mode - it is how a change gets discussed without
being made.

## When the mode ends

The mode holds until the user clearly asks for implementation - "go ahead", "lets do it",
"apply that", "proceed". Agreement that a proposal is good is not that signal: "yeah that
looks right" continues the discussion. When it is genuinely ambiguous whether the user has
released the hold, stay in discussion mode and say what you are ready to apply - a held
change costs one message to release, an unwanted change costs much more.
