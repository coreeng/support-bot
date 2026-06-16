---
name: Diátaxis types — signals reference
description: Definition, voice, signals, anti-signals, drift modes, and title patterns for each of the four Diátaxis types. Plus boundary-case section.
---

# Types reference

Each type is presented with the same structure so the agent can scan for signals consistently:

- **Definition** — one-sentence intent.
- **Reader state** — who the reader is and what they are doing.
- **Voice tells** — language that is characteristic of the type.
- **Signals** — concrete features that, if present, push classification toward this type.
- **Anti-signals** — features that, if present, push classification *away* from this type.
- **Common drift modes** — the typical ways pages of this type go wrong.
- **Title patterns** — title shapes that suggest this type (titles are weak evidence on their own; verify against content).

The boundary-case section at the foot disambiguates the two pairs most often confused: tutorial vs how-to, and reference vs explanation.

---

## Tutorial — learning-oriented

### Definition
A linear, hands-on lesson that takes a beginner from "I don't know how to do X" to "I have just done X for the first time".

### Reader state
A beginner. They have decided to learn. They are willing to follow instruction in exchange for a guaranteed, comprehensible outcome by the end.

### Voice tells
- First-person plural and inclusive: "we'll", "let's", "you'll see".
- Reassurance and orientation cues: "Notice that…", "You should see…", "If you see X instead, you're still on track."
- Imperative sequencing: "First, do X. Now, do Y."
- Closing celebration: "You have just built…", "Well done."

### Signals
- Numbered steps in a single linear sequence; no branching.
- A single concrete outcome promised in the opening paragraph.
- Each step produces a visible, named result the reader can verify.
- Concrete throughout: this command, this output, this file, this value. No generality.
- Explanation, where present, is brief and deferred ("we'll come back to why this works later").
- The page is expected to be executed end-to-end in one sitting.

### Anti-signals
- "Choose the option that fits your situation" branching.
- API parameter tables, exhaustive flag lists.
- Trade-off discussion, alternatives, "you might also want to".
- Prerequisites longer than the lesson itself.
- Abstract framing ("when working with services in general…").

### Common drift modes
- **Tutorial-as-reference**: opens as a lesson, then halfway through becomes a flag-by-flag enumeration of a command. → Strip the reference content out (move it to the reference doc) or SPLIT.
- **Tutorial with branches**: "if you're on macOS, do X; if Linux, do Y". → Pick one platform per tutorial, or split into platform-specific tutorials.
- **Tutorial-as-explanation**: lesson interleaved with multi-paragraph "why this matters" digressions. → Lift the explanation into an explanation doc, link out from the tutorial.
- **Stub tutorial**: a single command and a one-liner. Not a tutorial; usually a how-to or reference.

### Title patterns
- "Your first X with Y"
- "Getting started with Y"
- "Build a [thing] in [time]"
- "Hello world for Y"

---

## How-to guide — task-oriented

### Definition
Step-by-step instructions that help an already-competent user accomplish a specific real-world task.

### Reader state
A competent user. They already know the domain. They have a goal in mind and want a recipe that gets them to it, including how to handle the variations they will plausibly encounter.

### Voice tells
- Imperative, terse: "Run X. Set Y. Confirm Z."
- Conditional language: "If A, do B; otherwise, do C."
- Prerequisites stated up front: "Before you start, you need…".
- Goal-shaped framing: "To do X, …".

### Signals
- Title is goal-shaped ("How to …", "Configuring …", "Migrating …").
- Imperative steps, often with branches and conditionals.
- Prerequisites section.
- Focused on a single named outcome, but the *path* can vary by environment.
- Links out to reference for fields/options rather than enumerating them.
- The page assumes domain literacy; no teaching of basics.

### Anti-signals
- "We'll learn about" framing or any pedagogical voice.
- Exhaustive enumeration of every parameter (that belongs in reference).
- Narrative theory, design rationale.
- The page reads top-to-bottom as a story rather than as a recipe.

### Common drift modes
- **How-to-as-tutorial**: a recipe that opens with "first, let's understand X" and spends three sections teaching. → Strip the teaching; move it to explanation; link out.
- **How-to-as-reference**: a recipe that lists every flag the command accepts. → Remove the enumeration; link to reference for full options.
- **How-to without a goal**: a sequence of commands with no stated outcome. → Either add a goal-shaped title or reclassify as reference (if it's documenting a command) or outlier (if it's notes).

### Title patterns
- "How to …"
- "Configuring …"
- "Migrating from X to Y"
- "Integrating Y with Z"

---

## Reference — information-oriented

### Definition
A factual, exhaustive description of the machinery — APIs, commands, flags, schemas, configuration — organised to be looked up, not read.

### Reader state
Looking something up. They have a question of the form "what does flag X do?" or "what fields does resource Y have?". They will read one entry, possibly two, and leave.

### Voice tells
- Declarative, terse, neutral.
- Third-person, no "we" or "you".
- Standard patterns: every entry has the same shape (name, type, default, description).
- Stamped-out structure: tables, definition lists, alphabetical sections.

### Signals
- Tables of fields, options, flags, exit codes.
- Definition lists (`term: description`).
- Alphabetical or structural ordering (mirrors the code).
- Identical sub-headings repeated for each entry (e.g. every command has "Synopsis / Options / Examples / Exit codes").
- Code blocks are usage snippets, not narrative examples.
- No first-person voice anywhere on the page.

### Anti-signals
- Narrative paragraphs explaining background or motivation.
- Trade-off discussion.
- "If you want X, do Y" recipes.
- Pedagogical voice or "we'll learn".

### Common drift modes
- **Reference-as-how-to**: a CLI reference that ends each command's section with a "Common workflows" recipe. → Lift the recipes into how-to docs; link from reference.
- **Reference-as-explanation**: a schema reference whose field descriptions are essay-length and discuss why the field exists. → Trim descriptions to facts; move rationale to explanation.
- **Inconsistent reference**: every command documented with a different shape. → Adopt a standard pattern; this is a quality fail, not a misclassification.

### Title patterns
- "X reference"
- "API reference"
- "Configuration reference"
- "X command"
- "X resource"
- (Often the title is just the name of the thing being described.)

---

## Explanation — understanding-oriented

### Definition
Discursive prose that deepens understanding of a topic — its background, its design, its trade-offs, its place in a broader landscape.

### Reader state
Reflecting, not doing. They have a question of the form "why does Y work this way?" or "what is Y for?". They are willing to read continuous prose and follow an argument.

### Voice tells
- Discursive, paragraph-shaped, narrative.
- Hedged and opinionated language: "we chose to", "historically", "because", "in contrast to".
- Comparative: "X is preferable to Y when…".
- Acknowledges alternatives and counter-examples.

### Signals
- Paragraphs of continuous prose with few code blocks.
- Headings frame *topics*, not procedures (e.g. "Why head-based sampling", "Trade-offs").
- Diagrams or conceptual figures rather than command output.
- The page makes sense read away from the product, on a phone, on a train.
- Cross-references to design documents, RFCs, papers.

### Anti-signals
- Numbered imperative steps.
- Field tables.
- "Run this command" framing.
- A promised end-state outcome.

### Common drift modes
- **Explanation-as-how-to**: an essay that drifts into "here's how you'd configure it". → Lift the configuration steps into a how-to; keep the essay focused on the why.
- **Explanation-as-reference**: an essay that drifts into a field-by-field walkthrough. → Lift the fields into reference; keep the essay focused on rationale.
- **Hollow explanation**: a page titled "About X" that simply restates the reference in prose. → Not an explanation. Reclassify or remove.

### Title patterns
- "About …"
- "Why we …"
- "Understanding …"
- "[Concept] explained"
- "[Topic]: design and trade-offs"

---

## Boundary cases

### Tutorial vs How-to guide

Both contain steps. Both look imperative on first glance. The distinguishing question is **who is the reader**:

| | Tutorial | How-to guide |
| --- | --- | --- |
| Reader | Beginner, learning the domain | Competent, applying existing skill |
| Promise | A guaranteed learning experience | A reliable recipe for a known goal |
| Path | One carefully managed path; no branches | May branch by environment, version, choice |
| Theory | Minimised, deferred | Assumed; linked out for those who need it |
| Outcome | The reader's *competence* | The reader's *task* |
| Title shape | "Your first…", "Getting started…" | "How to…", "Configuring…", "Migrating…" |

If the title and content both pass either column cleanly, classify accordingly. If the title says "Getting started" but the content is goal-shaped with prerequisites and branches, trust the content — it's a how-to.

### Reference vs Explanation

Both are read-not-done. Both lack imperative steps. The distinguishing question is **what the prose is doing**:

| | Reference | Explanation |
| --- | --- | --- |
| Purpose | State facts about the machinery | Discuss the why |
| Tone | Neutral, declarative | Discursive, sometimes opinionated |
| Structure | Mirrors the code | Mirrors a line of argument |
| Reader behaviour | Look up one entry, leave | Read top-to-bottom |
| Completeness | Exhaustive — every field documented | Bounded — a topic, not everything about a topic |
| Examples | Usage snippets (incidental) | Conceptual or comparative |

A field description in a reference page may carry a single sentence of context. If it carries a paragraph, it has become explanation and should be moved.

## Sources

Adapted from <https://diataxis.fr/tutorials/>, <https://diataxis.fr/how-to-guides/>, <https://diataxis.fr/reference/>, <https://diataxis.fr/explanation/>, and <https://diataxis.fr/tutorials-how-to/>.
