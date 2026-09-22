package spellcheck;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Aho-Corasick automaton.
 * Builds one automaton over the whole dictionary (m words) so that a
 * single left-to-right pass over the input text (length n) finds every
 * dictionary word that occurs in it, in O(n + m + z) time
 * (z = number of matches). Used here to demonstrate multi-pattern
 * matching; the Trie is still used for single-word exact lookups.
 */
public class AhoCorasick {

    private static class Node {
        Map<Character, Node> children = new HashMap<>();
        Node fail;
        List<String> outputs = new ArrayList<>();
    }

    public static class Match {
        public final int endIndex;
        public final String word;

        public Match(int endIndex, String word) {
            this.endIndex = endIndex;
            this.word = word;
        }

        @Override
        public String toString() {
            return "(" + endIndex + ", " + word + ")";
        }
    }

    private final Node root = new Node();
    private boolean built = false;

    public void addWord(String word) {
        Node node = root;
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            node = node.children.computeIfAbsent(ch, c -> new Node());
        }
        node.outputs.add(word);
    }

    /** Builds failure links with a BFS. */
    public void build() {
        root.fail = root;
        Queue<Node> queue = new ArrayDeque<>();
        for (Node child : root.children.values()) {
            child.fail = root;
            queue.add(child);
        }

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            for (Map.Entry<Character, Node> entry : current.children.entrySet()) {
                char ch = entry.getKey();
                Node child = entry.getValue();
                queue.add(child);

                Node failNode = current.fail;
                while (failNode != root && !failNode.children.containsKey(ch)) {
                    failNode = failNode.fail;
                }
                Node candidate = failNode.children.getOrDefault(ch, root);
                child.fail = (candidate == child) ? root : candidate;
                child.outputs.addAll(child.fail.outputs);
            }
        }
        built = true;
    }

    /** Single pass over text. Returns list of (endIndex, matchedWord). */
    public List<Match> search(String text) {
        if (!built) {
            build();
        }
        Node node = root;
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            while (node != root && !node.children.containsKey(ch)) {
                node = node.fail;
            }
            node = node.children.getOrDefault(ch, root);
            for (String word : node.outputs) {
                matches.add(new Match(i, word));
            }
        }
        return matches;
    }
}
