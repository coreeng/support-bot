---
name: Diátaxis compass
description: Two-question decision tool to classify a documentation page into one of the four Diátaxis types. Load first when classifying any page.
---

# Compass

This is the first reference to consult when classifying a page. It returns a single Diátaxis type from two binary-choice questions. If it returns one type confidently, classify and move on. If it returns two plausible types, or none, escalate to `types.md` for signal detail and `decision-rubric.md` for the disambiguation procedure.

## The two questions

For the page (or self-contained section) under review, answer both:

1. **Does this content inform ACTION or COGNITION?**
   - *Action* — the reader is meant to do something (run a command, follow steps, perform an operation).
   - *Cognition* — the reader is meant to know something (a fact, a definition, a rationale, a model).

2. **Does this content serve ACQUISITION or APPLICATION?**
   - *Acquisition* — the reader is in study mode: building competence they do not yet have.
   - *Application* — the reader is in work mode: applying competence they already have.

## Truth table

|                              | Acquisition (study)   | Application (work)   |
| ---------------------------- | --------------------- | -------------------- |
| **Action** (practical doing) | Tutorial              | How-to guide         |
| **Cognition** (knowing)      | Explanation           | Reference            |

## Reading the answers

The four types map to four distinct reader questions:

| Type         | Reader is asking…       |
| ------------ | ----------------------- |
| Tutorial     | "Can you teach me to…?" |
| How-to guide | "How do I…?"            |
| Reference    | "What is…?"             |
| Explanation  | "Why…?"                 |

If the page answers more than one of these questions in substantial measure, the page is mixed → see `decision-rubric.md` for the disambiguation procedure.

## Granularity

The compass can be applied at any level: a paragraph, a section, or a whole page. When classifying a long page, sample three or four sections and apply the compass to each. If sections disagree, the page is a candidate to split.

## When the compass is enough

The compass alone is sufficient when the page is **single-intent and well-shaped** — for example, a page that is plainly imperative steps for a known goal (how-to), or plainly an API table (reference). Legacy documentation often drifts between intents; for those pages the compass narrows the candidates to two, and the rest of the references resolve the call.

## When the compass is not enough

Load `types.md` next if:

- Two answers feel equally plausible on either axis.
- The page has a title that suggests one type but content that suggests another.
- The reader-question test returns two clear answers (e.g. "How do I…?" *and* "Why…?").

Load `decision-rubric.md` if `types.md` does not resolve it.

## Sources

Adapted from <https://diataxis.fr/compass/>, <https://diataxis.fr/foundations/>, and <https://diataxis.fr/map/>.
