# Dictionary-Based Misspelled Word Detection System (Java)

Same pipeline as the slides: **Trie → Aho-Corasick → Error Detection →
Damerau-Levenshtein/Soundex Correction Ranking → Report Generation**,
benchmarked against naive scanning. Pure Java, no external libraries.

## Files
```
src/main/java/spellcheck/
  Trie.java           - Trie for O(L) dictionary lookup
  AhoCorasick.java    - Aho-Corasick automaton for single-pass multi-word matching
  Correction.java     - Damerau-Levenshtein edit distance + Soundex phonetic matching
  BatchAnalyzer.java  - line/word-position tracking for both input modes
  SpellChecker.java   - main pipeline (run this - has the main() method)
corpus/
  dictionary.txt      - 370,105-word English dictionary (dwyl/english-words)
  input_docs/         - drop .txt files here for Mode 2 (3 samples included)
run.command           - double-click this in Finder to run without a terminal
```

## Easiest way to run it: double-click run.command
No VS Code, no terminal commands. Just double-click `run.command` in
Finder. The first time, macOS will block it as "from an unidentified
developer" — right-click it, choose **Open**, then click **Open**
again in the popup. After that, every future double-click just works.

## Or run it in VS Code's terminal
```bash
cd <this folder>
javac -d out src/main/java/spellcheck/*.java
java -cp out spellcheck.SpellChecker
```
(The exact command for this project is also written as a comment at
the top of `SpellChecker.java`.)

## Two input modes
When you run it, it asks:
```
What would you like to do?
  1. Check text you paste or type
  2. Analyze up to 100 documents in a folder (with line/word numbers)
```

### Mode 1 — paste or type text
Paste or type your text. If it's more than one line, press Enter
after each line; when you're done, press Enter on a blank line to
submit. Just press Enter right away to use a built-in sample sentence.

Every error is reported with its exact line and word position, same
as Mode 2:
```
Line 2, Word 4: "erors"
   Suggestions: errors, eros, errs
```

**Tip:** for a very long *single-line* paste (one huge unbroken line
with no line breaks in it), terminals have a hard length limit on
how much text they'll accept on one line before silently cutting it
off — this is an operating-system limitation, not something this
program can work around. If you have a big block of text like that,
save it as a `.txt` file and use Mode 2 instead, which reads files
directly with no such limit.

### Mode 2 — batch document analysis
1. Put up to 100 `.txt` files in a folder — the included
   `corpus/input_docs/` folder already has 3 samples so you can try
   it immediately.
2. Choose option 2, then press Enter to use `corpus/input_docs`, or
   type a different folder path.
3. Every document gets its own report (line/word positions, same as
   above), followed by an overall summary across all documents, and
   a full machine-readable report written to `output/batch_report.json`.

**Re-running with a new batch:** the program just re-reads whatever
`.txt` files are currently in the folder each time you run it. If you
don't want an old batch mixed into a new report, either delete the
old files first, or use a different folder path for each batch.

## Notes
- `corpus/dictionary.txt` must stay in the `corpus/` folder relative
  to wherever you run the program from.
- Correction ranking narrows candidates by word length (±2 chars)
  before scoring, so it stays fast without needing frequency data. As
  a side effect, when two corrections tie on edit distance and
  phonetics (e.g. "beleive" → "beleave" vs. "believe"), it isn't
  guaranteed to pick the more common English word — a word-frequency
  table built from a real text corpus would fix that (see below).
- `benchmark()` and the Aho-Corasick demo scan are still in the code
  (unused by `main()` now that the menu is in place) if you want to
  show that part of the pipeline separately.

## About "corpus" vs. "dictionary"
`corpus/dictionary.txt` is a **word list** — every valid word appears
exactly once, so it can only tell you whether a word exists, not how
common it is. A true **corpus** is real running text (articles,
books) where common words like "the" appear far more often than rare
ones — that repetition is what lets you rank corrections by real-world
frequency instead of just edit distance. If frequency-based ranking is
required, that would need actual sample text counted for word
frequency, layered on top of the existing dictionary lookup.
