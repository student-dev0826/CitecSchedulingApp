package com.example.citecschedulingapp;

import org.junit.Test;
import static com.example.citecschedulingapp.NotificationSummary.Kind.*;
import static org.junit.Assert.assertEquals;

public class NotificationSummaryTest {
    @Test public void existingApprovalAndDeclineMessagesAreRecognized() {
        assertEquals(ACCEPTED, NotificationSummary.classify("Your Academic Advising appointment with Prof. Cruz on Nov 24 has been ACCEPTED!"));
        assertEquals(ACCEPTED, NotificationSummary.classify("Your consultation appointment with Cruz on Nov 24 was approved."));
        assertEquals(DECLINED, NotificationSummary.classify("Your consultation request with Cruz on Nov 24 has been DECLINED. Reason: Meeting."));
        assertEquals(DECLINED, NotificationSummary.classify("Your consultation request was declined. You can book another slot."));
    }

    @Test public void pendingSlotRequestsAreNotCalledConfirmedBookings() {
        assertEquals(SLOT_REQUESTED, NotificationSummary.classify("Alex requested Academic Advising on Nov 24. Open Schedule to accept or decline."));
        assertEquals(CUSTOM_REQUEST, NotificationSummary.classify("New appointment request from Alex for Nov 24. Open Schedule to accept or decline."));
        assertEquals(SLOT_BOOKED, NotificationSummary.classify("Alex booked your Academic Advising slot on Nov 24."));
    }

    @Test public void cancellationsTransfersAndExpiryKeepTheirMeaning() {
        assertEquals(CANCELLED, NotificationSummary.classify("Alex cancelled the consultation appointment on Nov 24."));
        assertEquals(CANCELLED, NotificationSummary.classify("Prof. Cruz cancelled your appointment on Nov 24. Reason: Meeting."));
        assertEquals(WITHDRAWN, NotificationSummary.classify("Alex withdrew the request for Academic Advising on Nov 24."));
        assertEquals(TRANSFERRED, NotificationSummary.classify("Your appointment on Nov 24 was transferred from Cruz to Santos."));
        assertEquals(TRANSFERRED, NotificationSummary.classify("Cruz transferred Alex's consultation appointment on Nov 24 to you."));
        assertEquals(EXPIRED, NotificationSummary.classify("Your request on Nov 24 expired because it was not approved in time."));
    }

    @Test public void reasonsCannotChangeTheNotificationType() {
        assertEquals(DECLINED, NotificationSummary.classify("Your request was declined. Reason: Your last appointment was approved."));
        assertEquals(CANCELLED, NotificationSummary.classify("Cruz cancelled your consultation. Reason: The request has been ACCEPTED by mistake."));
        assertEquals(ACCEPTED, NotificationSummary.classify("  YOUR REQUEST HAS BEEN ACCEPTED!  "));
    }

    @Test public void unfamiliarOrEmptyMessagesUseANeutralSummary() {
        assertEquals(UPDATE, NotificationSummary.classify(null));
        assertEquals(UPDATE, NotificationSummary.classify(""));
        assertEquals(UPDATE, NotificationSummary.classify("Please contact the office for more information."));
        assertEquals(UPDATE, NotificationSummary.classify("The request has not been accepted."));
    }
}
