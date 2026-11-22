package com.example.citecschedulingapp;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChatReplyTest {
    @Test public void quotedReplySurvivesTextOnlyTransport() {
        String wire = ChatReply.encode("Professor Cruz", "Please bring your proposal.", "I will.\nThank you!");
        ChatReply decoded = ChatReply.parse(wire);
        assertNotNull(decoded);
        assertEquals("Professor Cruz", decoded.author);
        assertEquals("Please bring your proposal.", decoded.excerpt);
        assertEquals("I will.\nThank you!", decoded.body);
        assertTrue(wire.contains("Please bring your proposal."));
    }

    @Test public void ordinaryAndIncompleteMessagesAreNotReplies() {
        for (String message : new String[]{null, "Hello", "> quoted\n\ntext", "↪ Reply to Pat",
                "↪ Reply to Pat\n“Hi”\nMissing separator", "↪ Reply to Pat\n“Hi”\n\n ",
                "↪ Reply to \n“Hi”\n\nHello"}) assertNull(ChatReply.parse(message));
    }

    @Test public void replyToReplyQuotesOnlyTheLatestMessage() {
        String first = ChatReply.encode("Student", "Original", "Can we meet tomorrow?");
        assertEquals("Can we meet tomorrow?", ChatReply.excerpt(first));
        ChatReply second = ChatReply.parse(ChatReply.encode("Professor", ChatReply.excerpt(first), "Yes."));
        assertEquals("Can we meet tomorrow?", second.excerpt);
        assertEquals("Yes.", second.body);
    }

    @Test public void longEmojiQuotesDoNotSplitSurrogatePairs() {
        StringBuilder longMessage = new StringBuilder();
        for (int i = 0; i < 180; i++) longMessage.append("📚");
        String quote = ChatReply.excerpt(longMessage.toString());
        assertEquals(160, quote.codePointCount(0, quote.length()));
        assertTrue(quote.endsWith("…"));
        assertEquals(quote, ChatReply.parse(ChatReply.encode("José", quote, "Salamat! 😊")).excerpt);
    }

    @Test public void multilineQuotesCannotBreakTheEnvelope() {
        ChatReply reply = ChatReply.parse(ChatReply.encode("Professor\nCruz", "Hello\n\nclass", "Reply\nwith another line"));
        assertEquals("Professor Cruz", reply.author);
        assertEquals("Hello class", reply.excerpt);
        assertEquals("Reply\nwith another line", reply.body);
    }
}
