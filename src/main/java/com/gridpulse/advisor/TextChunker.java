package com.gridpulse.advisor;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a document into overlapping chunks for embedding.
 * <ul>
 *   <li>Paragraphs (blank-line separated) are packed into chunks of up to {@code maxChars}.</li>
 *   <li>A paragraph longer than {@code maxChars} is split at sentence ends, then at spaces, never mid-word.</li>
 *   <li>Each chunk after the first starts with the last ~{@code overlapChars} of the previous one (whole words),
 *       so a fact that straddles a boundary is still retrievable from one chunk.</li>
 *   <li>Markdown headings are carried forward: each chunk is prefixed with the nearest heading above it.</li>
 * </ul>
 */
public final class TextChunker {
    private final int maxChars;
    private final int overlapChars;

    public TextChunker(int maxChars, int overlapChars) {
        if (maxChars < 100) throw new IllegalArgumentException("maxChars must be >= 100");
        if (overlapChars < 0 || overlapChars >= maxChars / 2) throw new IllegalArgumentException("overlap must be in [0, maxChars/2)");
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
    }

    public List<String> chunk(String text) {
        String normalized = text.replace("\r\n", "\n").strip();
        List<String> pieces = new ArrayList<>();
        List<String> headings = new ArrayList<>();
        String heading = "";
        for (String para : normalized.split("\n\\s*\n")) {
            String p = para.strip();
            if (p.isEmpty()) continue;
            if (p.startsWith("#")) {
                heading = p.lines().findFirst().orElse("").replaceAll("^#+\\s*", "").strip();
                String rest = p.lines().skip(1).reduce("", (a, b) -> a + "\n" + b).strip();
                if (rest.isEmpty()) continue;
                p = rest;
            }
            for (String piece : splitLong(p.replaceAll("\\s+", " "))) {
                pieces.add(piece);
                headings.add(heading);
            }
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentHeading = "";
        for (int i = 0; i < pieces.size(); i++) {
            String piece = pieces.get(i);
            int budget = maxChars - prefix(headings.get(i)).length();
            if (current.length() > 0 && (current.length() + 1 + piece.length() > budget || !headings.get(i).equals(currentHeading))) {
                chunks.add(prefix(currentHeading) + current.toString().strip());
                String tail = tail(current.toString(), overlapChars);
                current = new StringBuilder(headings.get(i).equals(currentHeading) ? tail : "");
            }
            currentHeading = headings.get(i);
            if (current.length() > 0) current.append(' ');
            current.append(piece);
        }
        if (current.length() > 0) chunks.add(prefix(currentHeading) + current.toString().strip());
        return chunks;
    }

    private static String prefix(String heading) {
        return heading.isEmpty() ? "" : heading + ": ";
    }

    /** Pieces of at most maxChars/ (leaving room for overlap and heading), cut at sentence ends or spaces. */
    private List<String> splitLong(String p) {
        int limit = maxChars - overlapChars - 80;
        List<String> out = new ArrayList<>();
        String rest = p;
        while (rest.length() > limit) {
            int cut = lastBoundary(rest, limit, ". ");
            if (cut < limit / 2) cut = lastBoundary(rest, limit, " ");
            if (cut <= 0) cut = limit;
            out.add(rest.substring(0, cut).strip());
            rest = rest.substring(cut).strip();
        }
        if (!rest.isEmpty()) out.add(rest);
        return out;
    }

    private static int lastBoundary(String s, int limit, String boundary) {
        int idx = s.lastIndexOf(boundary, limit);
        return idx < 0 ? -1 : idx + boundary.length() - (boundary.equals(" ") ? 1 : 0);
    }

    private static String tail(String s, int chars) {
        if (chars == 0 || s.length() <= chars) return chars == 0 ? "" : s.strip();
        String t = s.substring(s.length() - chars);
        int space = t.indexOf(' ');
        return space < 0 ? t.strip() : t.substring(space + 1).strip();
    }
}
