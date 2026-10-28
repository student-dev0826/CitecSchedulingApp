package com.example.citecschedulingapp;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class PendingRequestsTest {
    @Test public void concurrentReadsShareRequestAndDeliverToEveryListener() {
        PendingRequests<String> requests = new PendingRequests<>();
        assertTrue(requests.join("student", "badge"));
        assertFalse(requests.join("student", "list"));
        assertEquals(Arrays.asList("badge", "list"), requests.finish("student"));
        assertTrue(requests.join("student", "refresh"));
    }

    @Test public void usersRemainIndependentAndCompletionReleasesListeners() {
        PendingRequests<String> requests = new PendingRequests<>();
        assertTrue(requests.join("student", "student view"));
        assertTrue(requests.join("professor", "professor view"));
        assertEquals(Arrays.asList("student view"), requests.finish("student"));
        assertTrue(requests.finish("student").isEmpty());
        assertEquals(Arrays.asList("professor view"), requests.finish("professor"));
    }
}
