package io.freshwater.banx.velocity.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing and formatting of human-friendly durations such as {@code 1d2h30m15s}. */
public final class Durations {

    private Durations() {
    }

    private static final Pattern TOKEN = Pattern.compile("(\\d+)\\s*([smhdw])", Pattern.CASE_INSENSITIVE);

    /**
     * Parses a duration string like {@code 30m}, {@code 1d12h}, {@code 2w} into milliseconds.
     *
     * @return milliseconds, or -1 if the input is not a valid duration
     */
    public static long parse(String input) {
        if (input == null || input.isBlank()) {
            return -1L;
        }
        String trimmed = input.trim().toLowerCase();
        if (trimmed.equals("perm") || trimmed.equals("permanent") || trimmed.equals("forever")) {
            return Long.MAX_VALUE;
        }
        Matcher m = TOKEN.matcher(trimmed);
        long total = 0L;
        int matchedChars = 0;
        while (m.find()) {
            matchedChars += m.group(0).length();
            long value = Long.parseLong(m.group(1));
            switch (m.group(2).toLowerCase()) {
                case "s" -> total += value * 1000L;
                case "m" -> total += value * 60_000L;
                case "h" -> total += value * 3_600_000L;
                case "d" -> total += value * 86_400_000L;
                case "w" -> total += value * 604_800_000L;
                default -> {
                    return -1L;
                }
            }
        }
        // Reject strings containing junk we didn't parse (ignoring whitespace).
        if (matchedChars != trimmed.replaceAll("\\s", "").length() || total <= 0L) {
            return -1L;
        }
        return total;
    }

    /** Formats a millisecond duration into a compact human string like {@code 1d 2h 30m}. */
    public static String format(long millis) {
        if (millis <= 0L) {
            return "0s";
        }
        if (millis == Long.MAX_VALUE) {
            return "permanent";
        }
        long seconds = millis / 1000L;
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 && days == 0 && hours == 0) {
            sb.append(seconds).append("s ");
        }
        return sb.toString().trim();
    }
}
