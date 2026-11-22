package com.example.citecschedulingapp;

/** Readable quoted replies carried by the existing message-text API. */
public final class ChatReply {
    private static final String PREFIX = "↪ Reply to ";
    public final String author;
    public final String excerpt;
    public final String body;

    private ChatReply(String author, String excerpt, String body) {
        this.author = author;
        this.excerpt = excerpt;
        this.body = body;
    }

    public static String excerpt(String text) {
        ChatReply reply = parse(text);
        return singleLine(reply == null ? text : reply.body, 160);
    }

    public static String body(String text) {
        ChatReply reply = parse(text);
        return reply == null ? text : reply.body;
    }

    public static String encode(String author, String excerpt, String body) {
        return PREFIX + singleLine(author, 80) + "\n“" + singleLine(excerpt, 160) + "”\n\n" + body;
    }

    public static ChatReply parse(String text) {
        if (text == null || !text.startsWith(PREFIX)) return null;
        int first = text.indexOf('\n');
        int second = first < 0 ? -1 : text.indexOf('\n', first + 1);
        if (first <= PREFIX.length() || second < 0 || second + 2 >= text.length()
                || text.charAt(second + 1) != '\n') return null;
        String quote = text.substring(first + 1, second);
        String body = text.substring(second + 2);
        if (!quote.startsWith("“") || !quote.endsWith("”") || quote.length() <= 2
                || body.trim().isEmpty()) return null;
        return new ChatReply(text.substring(PREFIX.length(), first),
                quote.substring(1, quote.length() - 1), body);
    }

    private static String singleLine(String text, int limit) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        int count = value.codePointCount(0, value.length());
        return count <= limit ? value : value.substring(0, value.offsetByCodePoints(0, limit - 1)) + "…";
    }
}
