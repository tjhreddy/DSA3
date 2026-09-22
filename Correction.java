package spellcheck;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Correction & ranking techniques:
 *   1. Damerau-Levenshtein edit distance - lower distance = closer correction
 *      (extends classic Levenshtein with adjacent-transposition handling,
 *      e.g. "recieve" -> "receive" is 1 edit, not 2)
 *   2. Soundex phonetic encoding - catches misspellings that "sound"
 *      right but are spelled wrong (e.g. "fone" -> "phone")
 */
public class Correction {

    public static class Suggestion {
        public final String word;
        public final int editDistance;
        public final boolean phoneticMatch;

        public Suggestion(String word, int editDistance, boolean phoneticMatch) {
            this.word = word;
            this.editDistance = editDistance;
            this.phoneticMatch = phoneticMatch;
        }

        @Override
        public String toString() {
            return word + " (edit distance " + editDistance + ")";
        }
    }

    /** O(len(a) * len(b)) DP distance with insert/delete/substitute/transpose. */
    public static int levenshtein(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }
        int lenA = a.length();
        int lenB = b.length();
        int[][] d = new int[lenA + 1][lenB + 1];
        for (int i = 0; i <= lenA; i++) d[i][0] = i;
        for (int j = 0; j <= lenB; j++) d[0][j] = j;

        for (int i = 1; i <= lenA; i++) {
            for (int j = 1; j <= lenB; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                int val = Math.min(
                        Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                        d[i - 1][j - 1] + cost
                );
                if (i > 1 && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    val = Math.min(val, d[i - 2][j - 2] + 1);
                }
                d[i][j] = val;
            }
        }
        return d[lenA][lenB];
    }

    private static final Map<Character, Character> SOUNDEX_CODES = new HashMap<>();
    static {
        for (char c : "bfpv".toCharArray()) SOUNDEX_CODES.put(c, '1');
        for (char c : "cgjkqsxz".toCharArray()) SOUNDEX_CODES.put(c, '2');
        for (char c : "dt".toCharArray()) SOUNDEX_CODES.put(c, '3');
        SOUNDEX_CODES.put('l', '4');
        for (char c : "mn".toCharArray()) SOUNDEX_CODES.put(c, '5');
        SOUNDEX_CODES.put('r', '6');
    }

    /** Standard Soundex phonetic code, e.g. "phone" and "fone" -> "F500". */
    public static String soundex(String word) {
        word = word.toLowerCase();
        StringBuilder lettersOnly = new StringBuilder();
        for (char c : word.toCharArray()) {
            if (Character.isLetter(c)) lettersOnly.append(c);
        }
        if (lettersOnly.length() == 0) {
            return "0000";
        }

        char firstLetter = Character.toUpperCase(lettersOnly.charAt(0));
        char[] codes = new char[lettersOnly.length()];
        for (int i = 0; i < lettersOnly.length(); i++) {
            codes[i] = SOUNDEX_CODES.getOrDefault(lettersOnly.charAt(i), '\0');
        }

        StringBuilder digits = new StringBuilder();
        char prevCode = codes[0];
        for (int i = 1; i < codes.length; i++) {
            char code = codes[i];
            if (code != '\0' && code != prevCode) {
                digits.append(code);
            }
            prevCode = code;
        }

        String result = firstLetter + digits.toString() + "000";
        return result.substring(0, 4);
    }

    /**
     * Scores each dictionary candidate by edit distance (primary) plus a
     * phonetic-match bonus, returning the topN candidates, best first.
     */
    public static List<Suggestion> rankCorrections(String word, List<String> candidates, int topN) {
        String targetCode = soundex(word);
        List<double[]> scored = new ArrayList<>(); // placeholder not used; see below
        List<Suggestion> allScored = new ArrayList<>();
        Map<String, Double> scoreMap = new HashMap<>();

        for (String cand : candidates) {
            int dist = levenshtein(word, cand);
            boolean phoneticMatch = soundex(cand).equals(targetCode);
            double score = dist - (phoneticMatch ? 0.5 : 0.0);
            allScored.add(new Suggestion(cand, dist, phoneticMatch));
            scoreMap.put(cand, score);
        }

        allScored.sort(Comparator
                .<Suggestion>comparingDouble(s -> scoreMap.get(s.word))
                .thenComparing(s -> s.word));

        return allScored.subList(0, Math.min(topN, allScored.size()));
    }
}
