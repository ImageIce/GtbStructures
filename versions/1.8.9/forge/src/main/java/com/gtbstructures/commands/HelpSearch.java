package com.gtbstructures.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Read-only ranked search over the same command usages shown by help. */
final class HelpSearch {
    static final class Entry {
        final String usage;
        final String description;
        final String category;
        private final List<String> commandWords;
        private final List<String> descriptionWords;
        private final List<String> categoryWords;

        Entry(String usage, String description, String category, String categoryText) {
            this.usage = usage;
            this.description = description;
            this.category = category;
            this.commandWords = words(usage.replaceAll("[<\\[].*", ""));
            this.descriptionWords = words(description);
            this.categoryWords = words(category + " " + categoryText);
        }
    }

    private static final class Match {
        final Entry entry;
        final int score;

        Match(Entry entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }

    private final List<Entry> entries;

    HelpSearch(List<Entry> entries) {
        this.entries = new ArrayList<Entry>(entries);
    }

    List<Entry> search(String query) {
        List<String> terms = words(query);
        if (terms.isEmpty()) return Collections.emptyList();
        List<Match> matches = new ArrayList<Match>();
        String compactQuery = join(terms);
        for (Entry entry : entries) {
            int score = 0;
            boolean complete = true;
            for (String term : terms) {
                int commandScore = bestWordScore(term, entry.commandWords) * 12;
                int descriptionScore = bestWordScore(term, entry.descriptionWords) * 4;
                int categoryScore = bestWordScore(term, entry.categoryWords) * 2;
                int termScore = Math.max(commandScore, Math.max(descriptionScore, categoryScore));
                if (termScore == 0) {
                    complete = false;
                    break;
                }
                score += termScore;
            }
            if (!complete) continue;
            String compactPath = join(entry.commandWords);
            if (compactPath.equals(compactQuery)) score += 5000;
            else if (compactPath.startsWith(compactQuery)) score += 1000;
            else if (compactPath.contains(compactQuery)) score += 500;
            matches.add(new Match(entry, score));
        }
        Collections.sort(matches, new Comparator<Match>() {
            @Override
            public int compare(Match a, Match b) {
                int scoreOrder = Integer.compare(b.score, a.score);
                if (scoreOrder != 0) return scoreOrder;
                int pathOrder = Integer.compare(a.entry.commandWords.size(), b.entry.commandWords.size());
                if (pathOrder != 0) return pathOrder;
                return a.entry.usage.compareToIgnoreCase(b.entry.usage);
            }
        });
        List<Entry> result = new ArrayList<Entry>();
        Set<String> seen = new LinkedHashSet<String>();
        for (Match match : matches) {
            if (seen.add(match.entry.usage)) result.add(match.entry);
        }
        return result;
    }

    private static int bestWordScore(String query, List<String> candidates) {
        int best = 0;
        for (String candidate : candidates) {
            if (candidate.equals(query)) return 100;
            if (query.length() >= 2 && candidate.startsWith(query)) {
                best = Math.max(best, 80);
            } else if (query.length() >= 3 && candidate.contains(query)) {
                best = Math.max(best, 65);
            } else if (query.length() >= 3 && candidate.length() >= 3) {
                int threshold = Math.min(3, Math.max(1, query.length() / 4));
                if (Math.abs(query.length() - candidate.length()) <= threshold) {
                    int distance = editDistance(query, candidate);
                    if (distance <= threshold) best = Math.max(best, 75 - distance * 10);
                }
            }
        }
        return best;
    }

    /** Restricted Damerau-Levenshtein also accepts adjacent keyboard transpositions. */
    private static int editDistance(String a, String b) {
        int[] beforePrevious = new int[b.length() + 1];
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    current[j] = Math.min(current[j], beforePrevious[j - 2] + 1);
                }
            }
            int[] swap = beforePrevious;
            beforePrevious = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static List<String> words(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) return Collections.emptyList();
        Set<String> result = new LinkedHashSet<String>();
        for (String word : normalized.split(" ")) {
            if (!word.isEmpty() && !isStopWord(word)) result.add(alias(word));
        }
        return new ArrayList<String>(result);
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.trim().toLowerCase(Locale.ROOT).replaceFirst("^/?(?:gtbs|gtbstructures)\\s+", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static boolean isStopWord(String word) {
        return word.equals("the") || word.equals("a") || word.equals("an")
                || word.equals("to") || word.equals("of") || word.equals("for")
                || word.equals("and") || word.equals("with") || word.equals("in");
    }

    private static String alias(String word) {
        if (word.equals("cd")) return "cooldown";
        if (word.equals("sfx") || word.equals("audio") || word.equals("sounds")) return "sound";
        if (word.equals("notify") || word.equals("notification") || word.equals("notifications")) return "notice";
        if (word.equals("translate") || word.equals("translations")) return "translation";
        if (word.equals("copyt")) return "copytranslation";
        if (word.equals("hs")) return "hintstructure";
        if (word.equals("sb")) return "scoreboard";
        if (word.equals("env") || word.equals("world")) return "environment";
        if (word.equals("firework")) return "fireworks";
        return word;
    }

    private static String join(List<String> words) {
        StringBuilder result = new StringBuilder();
        for (String word : words) result.append(word);
        return result.toString();
    }
}
