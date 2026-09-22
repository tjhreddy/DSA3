// ============================================================
// TO RUN THIS FILE:
// 1. Open a terminal in VS Code (Terminal -> New Terminal)
// 2. Paste this whole line and press Enter:
//
// cd "/Users/7rainreddy7/Downloads/PROJECTS/DSA/spellcheck_java" && javac -d out src/main/java/spellcheck/*.java && java -cp out spellcheck.SpellChecker
// (Or just double-click run.command in Finder instead - no terminal needed.)
// ============================================================

package spellcheck;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dictionary-Based Misspelled Word Detection System
 * --------------------------------------------------
 * Pipeline (matches the "System Architecture" slide):
 *   1. Input Text        -> read raw text
 *   2. Tokenization       -> Unicode-aware word splitting
 *   3. Dictionary Lookup  -> Trie (O(L) per word) + Aho-Corasick (single
 *                            pass multi-pattern scan, used for the
 *                            benchmark / bulk-matching demo)
 *   4. Error Detection    -> words missing from the Trie are flagged
 *   5. Correction Ranking -> Damerau-Levenshtein + Soundex
 *   6. Report Generation  -> structured JSON + console summary
 */
public class SpellChecker {

    // Unicode-aware tokenizer: keeps letters from any language.
    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}]+");

    private final Trie trie = new Trie();
    private final Map<Integer, List<String>> byLength = new HashMap<>();
    private int dictionarySize;

    public SpellChecker(String dictionaryPath) throws IOException {
        System.out.println("Loading dictionary from '" + dictionaryPath + "' ...");
        List<String> words = loadDictionary(dictionaryPath);

        for (String w : words) {
            trie.insert(w);
            byLength.computeIfAbsent(w.length(), k -> new ArrayList<>()).add(w);
        }
        dictionarySize = trie.getWordCount();
        System.out.printf("Dictionary loaded: %,d words.%n%n", dictionarySize);
    }

    public static List<String> loadDictionary(String path) throws IOException {
        List<String> words = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String w = Normalizer.normalize(line.trim().toLowerCase(), Normalizer.Form.NFC);
                if (!w.isEmpty()) {
                    words.add(w);
                }
            }
        }
        return words;
    }

    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher m = WORD_PATTERN.matcher(text);
        while (m.find()) {
            tokens.add(Normalizer.normalize(m.group().toLowerCase(), Normalizer.Form.NFC));
        }
        return tokens;
    }

    // ---- Step 3 & 4: Dictionary Lookup + Error Detection --------------
    public boolean isCorrect(String word) {
        return trie.search(word);
    }

    public List<String> findMisspellings(List<String> tokens) {
        List<String> misspelled = new ArrayList<>();
        for (String t : tokens) {
            if (!isCorrect(t)) {
                misspelled.add(t);
            }
        }
        return misspelled;
    }

    // ---- Step 5: Correction Ranking ------------------------------------
    // Narrows ~370k dictionary words down to a plausible candidate set
    // before running edit-distance scoring, using length as a cheap
    // prefilter (candidates within +-2 characters of the misspelled word).
    private List<String> candidatePool(String word) {
        List<String> pool = new ArrayList<>();
        for (int len = Math.max(1, word.length() - 2); len <= word.length() + 2; len++) {
            List<String> bucket = byLength.get(len);
            if (bucket != null) {
                pool.addAll(bucket);
            }
        }
        return pool;
    }

    public List<Correction.Suggestion> suggestCorrections(String word, int topN) {
        return Correction.rankCorrections(word, candidatePool(word), topN);
    }

    // ---- Step 6: Report Generation --------------------------------------
    public String generateReport(String text, String outputPath) throws IOException {
        List<String> tokens = tokenize(text);
        int totalWords = tokens.size();

        List<String> misspelled = findMisspellings(tokens);

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"total_words_scanned\": ").append(totalWords).append(",\n");
        json.append("  \"errors_found\": ").append(misspelled.size()).append(",\n");
        double accuracy = totalWords == 0 ? 0.0
                : Math.round((totalWords - misspelled.size()) / (double) totalWords * 1000.0) / 10.0;
        json.append("  \"accuracy_percent\": ").append(accuracy).append(",\n");
        json.append("  \"dictionary_size\": ").append(dictionarySize).append(",\n");
        json.append("  \"errors\": [\n");

        for (int i = 0; i < misspelled.size(); i++) {
            String word = misspelled.get(i);
            List<Correction.Suggestion> suggestions = suggestCorrections(word, 3);
            json.append("    {\n");
            json.append("      \"word\": \"").append(word).append("\",\n");
            json.append("      \"suggestions\": [\n");
            for (int j = 0; j < suggestions.size(); j++) {
                Correction.Suggestion s = suggestions.get(j);
                json.append("        {\"word\": \"").append(s.word)
                        .append("\", \"edit_distance\": ").append(s.editDistance)
                        .append(", \"phonetic_match\": ").append(s.phoneticMatch).append("}");
                json.append(j < suggestions.size() - 1 ? ",\n" : "\n");
            }
            json.append("      ]\n");
            json.append("    }");
            json.append(i < misspelled.size() - 1 ? ",\n" : "\n");
        }
        json.append("  ]\n");
        json.append("}\n");

        Files.createDirectories(Paths.get(outputPath).getParent());
        try (FileWriter writer = new FileWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write(json.toString());
        }

        return json.toString();
    }

    // ---- Benchmark: dictionary-based (Trie) vs naive scanning -----------
    public Map<String, Object> benchmark(List<String> tokens) {
        long start = System.nanoTime();
        for (String w : tokens) {
            isCorrect(w);
        }
        long trieTimeNs = System.nanoTime() - start;

        // Naive scanning: linear search through the raw dictionary list
        // for every token (O(n * m)) - the baseline being improved on.
        List<String> allWordsFlat = new ArrayList<>();
        for (List<String> bucket : byLength.values()) {
            allWordsFlat.addAll(bucket);
        }
        start = System.nanoTime();
        for (String w : tokens) {
            allWordsFlat.contains(w); // linear scan
        }
        long naiveTimeNs = System.nanoTime() - start;

        Map<String, Object> result = new HashMap<>();
        result.put("tokens_checked", tokens.size());
        result.put("trie_lookup_seconds", trieTimeNs / 1e9);
        result.put("naive_scan_seconds", naiveTimeNs / 1e9);
        result.put("speedup_x", trieTimeNs > 0 ? (naiveTimeNs / (double) trieTimeNs) : null);
        return result;
    }

    // Demo Aho-Corasick multi-pattern scan built from the same dictionary.
    public static List<AhoCorasick.Match> demoAhoCorasickScan(List<String> dictionaryWords, String text, int limit) {
        AhoCorasick ac = new AhoCorasick();
        for (int i = 0; i < Math.min(limit, dictionaryWords.size()); i++) {
            ac.addWord(dictionaryWords.get(i));
        }
        ac.build();
        return ac.search(text.toLowerCase());
    }

    public static void main(String[] args) throws IOException {
        // Force UTF-8 console output so special characters (em dash, etc.)
        // render correctly regardless of the OS default encoding.
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));

        SpellChecker checker = new SpellChecker("corpus/dictionary.txt");
        BatchAnalyzer analyzer = new BatchAnalyzer(checker);
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);

        System.out.println("What would you like to do?");
        System.out.println("  1. Check text you paste or type");
        System.out.println("  2. Analyze up to 100 documents in a folder (with line/word numbers)");
        System.out.print("> ");
        String choice = scanner.nextLine().trim();

        List<BatchAnalyzer.DocumentResult> results;
        if (choice.equals("2")) {
            results = runBatchMode(analyzer, scanner);
        } else {
            results = runPasteMode(analyzer, scanner);
        }

        printResults(results);
    }

    // ---- Mode 1: paste/type text -------------------------------------------
    private static List<BatchAnalyzer.DocumentResult> runPasteMode(BatchAnalyzer analyzer, Scanner scanner) {
        System.out.println("\nPaste or type your text below.");
        System.out.println("If it spans multiple lines, press Enter after each one; when you're");
        System.out.println("done, press Enter on an empty line to finish. (Just press Enter right");
        System.out.println("away to use a sample sentence instead.)");
        System.out.println("Tip: for a very long single-line paste, it's more reliable to save it");
        System.out.println("as a .txt file and use option 2 instead - very long single lines can");
        System.out.println("get cut off by the terminal itself.");
        System.out.print("> ");

        List<String> lines = new ArrayList<>();
        String firstLine = scanner.nextLine();
        if (firstLine.trim().isEmpty()) {
            lines.add("I recieve your mesage yesterday and i beleive it was importent. "
                    + "Please chek the fone number and definately confrim the adress.");
        } else {
            lines.add(firstLine);
            while (true) {
                String line = scanner.nextLine();
                if (line.trim().isEmpty()) {
                    break;
                }
                lines.add(line);
            }
        }

        List<BatchAnalyzer.DocumentResult> results = new ArrayList<>();
        results.add(analyzer.analyzeLines("Pasted Text", lines));
        return results;
    }

    // ---- Mode 2: batch document analysis ------------------------------------
    private static List<BatchAnalyzer.DocumentResult> runBatchMode(BatchAnalyzer analyzer, Scanner scanner) throws IOException {
        System.out.println("\nPut your .txt documents in a folder (up to 100 of them).");
        System.out.println("Enter the folder path (or press Enter to use './corpus/input_docs'):");
        System.out.print("> ");
        String folderPath = scanner.nextLine().trim();
        if (folderPath.isEmpty()) {
            folderPath = "corpus/input_docs";
        }

        try {
            return analyzer.analyzeFolder(folderPath, 100);
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            System.out.println("Create that folder, put your .txt files in it, and run again.");
            return new ArrayList<>();
        }
    }

    // ---- Shared reporting for both modes -------------------------------------
    private static void printResults(List<BatchAnalyzer.DocumentResult> results) throws IOException {
        if (results.isEmpty()) {
            return;
        }

        String divider = "=".repeat(60);
        int totalWordsAll = 0;
        int totalErrorsAll = 0;

        for (BatchAnalyzer.DocumentResult doc : results) {
            System.out.println("\n" + divider);
            System.out.println("DOCUMENT: " + doc.fileName);
            System.out.println(divider);
            System.out.println("Total words: " + doc.totalWords
                    + "   Correct: " + (doc.totalWords - doc.errors.size())
                    + "   Errors: " + doc.errors.size()
                    + "   Accuracy: " + doc.accuracyPercent() + "%");

            if (doc.errors.isEmpty()) {
                System.out.println("\nNo spelling errors found.");
            } else {
                System.out.println("\n" + "-".repeat(60));
                System.out.println("DETECTED ERRORS (Line, Word position)");
                System.out.println("-".repeat(60));
                for (BatchAnalyzer.WordError err : doc.errors) {
                    System.out.println("Line " + err.line + ", Word " + err.wordNumber
                            + ": \"" + err.word + "\"");
                    if (err.suggestions.isEmpty()) {
                        System.out.println("   Suggestions: (none found)");
                    } else {
                        StringBuilder sb = new StringBuilder("   Suggestions: ");
                        for (int j = 0; j < err.suggestions.size(); j++) {
                            sb.append(err.suggestions.get(j).word);
                            if (j < err.suggestions.size() - 1) sb.append(", ");
                        }
                        System.out.println(sb);
                    }
                    System.out.println();
                }
            }

            totalWordsAll += doc.totalWords;
            totalErrorsAll += doc.errors.size();
        }

        double overallAccuracy = totalWordsAll == 0 ? 0.0
                : Math.round((totalWordsAll - totalErrorsAll) / (double) totalWordsAll * 1000.0) / 10.0;

        System.out.println("\n" + divider);
        System.out.println("OVERALL SUMMARY");
        System.out.println(divider);
        System.out.println("Documents processed  : " + results.size());
        System.out.println("Total words scanned  : " + totalWordsAll);
        System.out.println("Total errors found   : " + totalErrorsAll);
        System.out.println("Overall accuracy     : " + overallAccuracy + "%");
        System.out.println(divider);

        String jsonPath = writeBatchReportJson(results, "output/batch_report.json");
        System.out.println("\nDetailed JSON report written to: " + jsonPath);
    }

    private static String writeBatchReportJson(List<BatchAnalyzer.DocumentResult> results, String outputPath) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("[\n");
        for (int d = 0; d < results.size(); d++) {
            BatchAnalyzer.DocumentResult doc = results.get(d);
            json.append("  {\n");
            json.append("    \"source\": \"").append(doc.fileName).append("\",\n");
            json.append("    \"total_words\": ").append(doc.totalWords).append(",\n");
            json.append("    \"errors_found\": ").append(doc.errors.size()).append(",\n");
            json.append("    \"accuracy_percent\": ").append(doc.accuracyPercent()).append(",\n");
            json.append("    \"errors\": [\n");
            for (int e = 0; e < doc.errors.size(); e++) {
                BatchAnalyzer.WordError err = doc.errors.get(e);
                json.append("      {\n");
                json.append("        \"line\": ").append(err.line).append(",\n");
                json.append("        \"word_number\": ").append(err.wordNumber).append(",\n");
                json.append("        \"word\": \"").append(err.word).append("\",\n");
                json.append("        \"suggestions\": [");
                for (int s = 0; s < err.suggestions.size(); s++) {
                    Correction.Suggestion sug = err.suggestions.get(s);
                    json.append("\"").append(sug.word).append("\"");
                    if (s < err.suggestions.size() - 1) json.append(", ");
                }
                json.append("]\n");
                json.append("      }");
                json.append(e < doc.errors.size() - 1 ? ",\n" : "\n");
            }
            json.append("    ]\n");
            json.append("  }");
            json.append(d < results.size() - 1 ? ",\n" : "\n");
        }
        json.append("]\n");

        Files.createDirectories(Paths.get(outputPath).getParent());
        try (FileWriter writer = new FileWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write(json.toString());
        }
        return outputPath;
    }
}
