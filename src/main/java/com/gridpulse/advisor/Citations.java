package com.gridpulse.advisor;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts source references like [S1] or [S2, S3] from a model answer, keeping only ones that were provided. */
public final class Citations {
    private static final Pattern BRACKET = Pattern.compile("\\[(S\\d+(?:\\s*,\\s*S\\d+)*)]");

    private Citations() {
    }

    public static Set<Integer> referenced(String answer, int sourceCount) {
        Set<Integer> refs = new LinkedHashSet<>();
        Matcher m = BRACKET.matcher(answer == null ? "" : answer);
        while (m.find()) {
            for (String part : m.group(1).split(",")) {
                int n = Integer.parseInt(part.strip().substring(1));
                if (n >= 1 && n <= sourceCount) refs.add(n);
            }
        }
        return refs;
    }
}
