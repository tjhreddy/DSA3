package spellcheck;

import java.util.HashMap;
import java.util.Map;

/**
 * Trie (prefix tree) for dictionary lookup.
 * Gives O(L) word lookup, where L = length of the word,
 * independent of how many words (m) are in the dictionary.
 */
public class Trie {

    private static class Node {
        Map<Character, Node> children = new HashMap<>();
        boolean isWord = false;
    }

    private final Node root = new Node();
    private int wordCount = 0;

    public void insert(String word) {
        Node node = root;
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            node = node.children.computeIfAbsent(ch, c -> new Node());
        }
        if (!node.isWord) {
            wordCount++;
        }
        node.isWord = true;
    }

    /** O(L) exact-word lookup. */
    public boolean search(String word) {
        Node node = root;
        for (int i = 0; i < word.length(); i++) {
            node = node.children.get(word.charAt(i));
            if (node == null) {
                return false;
            }
        }
        return node.isWord;
    }

    public boolean startsWith(String prefix) {
        Node node = root;
        for (int i = 0; i < prefix.length(); i++) {
            node = node.children.get(prefix.charAt(i));
            if (node == null) {
                return false;
            }
        }
        return true;
    }

    public int getWordCount() {
        return wordCount;
    }
}
