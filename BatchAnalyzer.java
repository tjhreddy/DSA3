package spellcheck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Batch document analysis (up to 100 .txt documents at once).
 * For each document, every misspelled word is reported with its
 * exact LINE number and WORD number (position within that line),
 * plus ranked corrections - so an error can be located immediately
 * without re-reading the whole document.
 */
public class BatchAnalyzer {

    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}]+");
    private final SpellChecker checker;

    public BatchAnalyzer(SpellChecker checker) {
        this.checker = checker;
    }

    public static class WordError {
        public final int line;
        public final int wordNumber;
        public final String word;
        public final List<Correction.Suggestion> suggestions;

        public WordError(int line, int wordNumber, String word, List<Correction.Suggestion> suggestions) {
            this.line = line;
            this.wordNumber = wordNumber;
            this.word = word;
            this.suggestions = suggestions;
        }
    }

    public static class DocumentResult {
        public final String fileName;
        public final int totalWords;
        public final List<WordError> errors;

        public DocumentResult(String fileName, int totalWords, List<WordError> errors) {
            this.fileName = fileName;
            this.totalWords = totalWords;
            this.errors = errors;
        }

        public double accuracyPercent() {
            if (totalWords == 0) return 0.0;
            return Math.round((totalWords - errors.size()) / (double) totalWords * 1000.0) / 10.0;
        }
    }

    /** Analyzes a block of text already split into lines - shared by both file and pasted-text input. */
    public DocumentResult analyzeLines(String label, List<String> lines) {
        List<WordError> errors = new ArrayList<>();
        int totalWords = 0;

        for (int lineNum = 1; lineNum <= lines.size(); lineNum++) {
            String line = lines.get(lineNum - 1);
            Matcher m = WORD_PATTERN.matcher(line);
            int wordNum = 0;
            while (m.find()) {
                wordNum++;
                totalWords++;
                String rawWord = m.group();
                String normalized = Normalizer.normalize(rawWord.toLowerCase(), Normalizer.Form.NFC);
                if (!checker.isCorrect(normalized)) {
                    List<Correction.Suggestion> suggestions = checker.suggestCorrections(normalized, 3);
                    errors.add(new WordError(lineNum, wordNum, rawWord, suggestions));
                }
            }
        }
        return new DocumentResult(label, totalWords, errors);
    }

    /** Analyzes one file, tracking line number and in-line word number for every error. */
    public DocumentResult analyzeFile(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        return analyzeLines(path.getFileName().toString(), lines);
    }

    /** Analyzes every .txt file in a folder, capped at maxDocuments. */
    public List<DocumentResult> analyzeFolder(String folderPath, int maxDocuments) throws IOException {
        Path dir = Paths.get(folderPath);
        if (!Files.isDirectory(dir)) {
            throw new IOException("Folder not found: " + folderPath);
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".txt"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .collect(Collectors.toList());
        }

        if (files.isEmpty()) {
            System.out.println("No .txt files found in '" + folderPath + "'.");
            return new ArrayList<>();
        }

        if (files.size() > maxDocuments) {
            System.out.println("Found " + files.size() + " .txt files - only processing the first "
                    + maxDocuments + " (alphabetically).");
            files = files.subList(0, maxDocuments);
        } else {
            System.out.println("Found " + files.size() + " .txt file(s) to analyze.");
        }

        List<DocumentResult> results = new ArrayList<>();
        for (Path f : files) {
            results.add(analyzeFile(f));
        }
        return results;
    }
}
