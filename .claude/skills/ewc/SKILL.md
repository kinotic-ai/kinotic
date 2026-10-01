---
name: ewc
description: Explain With Code. Use only when the user invokes /ewc; never activate on your own. While active, every explanation of code — a design, a review finding, a proposed change, an answer to "what does this do" — is delivered as whole files with the reasoning written as comments inside them, and English outside a code block is limited to file paths and a one-line verdict. The mode holds for the rest of the conversation until the user turns it off.
---

# EWC - Explain With Code

The maintainers of this repo read code faster than English. When explaining anything that has a code representation — a design decision, a trade-off, a bug, an API, a proposed change — show the code itself and use prose only as connective tissue:

- A code block is one file, whole, as it would exist in the repository — never lines gathered from several files, never a fragment. A change to a file is two blocks of that one file: the file as it is now, then the file as it would be. A new file is one block headed by its full path and the word "new"; a moved file is headed `old path → new path`; a deleted file is named by its path with the word "deleted". YAML, Gradle, Terraform and docs files take the same form.
- The reader reads a file in context and does not carry one from an earlier message in their head, so a file is shown whole every time it appears, even when it appeared earlier and even when the change is one line — never "as given earlier", never "the rest is unchanged". The "after" file is the file as it would be committed: it carries every change already agreed in the conversation, so it never reintroduces something already removed and never defers an agreed change to "a separate batch". The "now" file is the file as it is in the repository.
- The explanation is comments inside the file, at the lines they are about. Comments that would ship follow the Comments section of CLAUDE.md. A comment that exists only to explain the change to its reader is prefixed `// why:` and is not part of the change; say once, at the top of the reply, that `// why:` marks review-only comments. The `// why:` at the top of a file says what is wrong with the file as it is and what the change does to it, so the reader needs no English outside the block.
- Outside the code blocks a reply carries only the path of the file that comes next and, at most, a one-line verdict at the top. A paragraph of English about code, and a block of code out of its file, are the two failure modes this section exists to prevent.
- A mechanical change repeated across files — a rename, a field swap, the same call rewritten — is shown as whole files for the first one, then listed by `path:line` for every other site, so the reader can check the list against the diff. That list is a checklist of locations and nothing more: one site per line, the repository path with its line number, no words on the line. It never groups sites by what their code does, never says what a site's callers pass, never puts a class or method name where the path goes, never says "and their counterparts", and never runs several references together in a sentence. A run of `Class.method:line` references with behaviour attached is a paragraph of English about code wearing line numbers, and is worthless to a human reader. Anything about what the code at a site does — which inputs are legal there, what a caller passes, how one site differs from another — is a reason to show that site as a file with a `// why:` comment, never to describe it. When the user allows brevity, brevity means fewer files, or one method shown in place of its file; it never means references in place of code.
- Present options and trade-offs as versions of the same file, each whole, one after the other, with a comment marking the line where they differ. Let the code carry the comparison; one sentence per option for what the code can't show.
- Never describe code indirectly when you can show it. A sentence about what a change does to an API is opaque; the call site that now compiles (or no longer compiles), with a one-line comment, is immediately legible.
- Show failure modes as code that compiles-but-misbehaves (or the verbatim compiler/test error), not as an abstract description of the risk.
- Review findings follow the same law: every finding is the file that holds the defect, whole, with the offending lines marked by a comment, then the file as corrected. A finding delivered as a prose summary is unfinished work — restate it with the code before presenting it.
- Close with the net effect: what is added, moved and deleted, by path. Keep prose for what code cannot express — intent, constraints, and consequences — as a comment beside the code it explains, or one sentence next to the block.

This governs how you communicate *about* the code in conversation — chat replies, PR descriptions, review responses. It does not apply to the repo's own artifacts: documentation (CLAUDE.md, READMEs) and code comments.

## When the mode ends

The mode holds until the user says to stop explaining with code, or asks for a summary in
English "without code". Discussion-only mode (`/dca`) and this mode compose: `/dca` decides
whether the repository may change, `/ewc` decides how the answer is written.
