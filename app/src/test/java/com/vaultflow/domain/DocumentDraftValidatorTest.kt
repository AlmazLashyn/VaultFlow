package com.vaultflow.domain

import com.vaultflow.data.repository.DocumentDraft
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentDraftValidatorTest {
    @Test fun acceptsNormalMetadata() {
        assertTrue(DocumentDraftValidator.isValid(DocumentDraft("Passport", "personal", listOf("travel"), 10, 20)))
    }

    @Test fun rejectsBlankOrOverlongNamesAndReversedDates() {
        assertFalse(DocumentDraftValidator.isValid(DocumentDraft("   ", "personal")))
        assertFalse(DocumentDraftValidator.isValid(DocumentDraft("x".repeat(121), "personal")))
        assertFalse(DocumentDraftValidator.isValid(DocumentDraft("Permit", "personal", documentDate = 20, expirationDate = 10)))
    }

    @Test fun enforcesTagLimits() {
        assertFalse(DocumentDraftValidator.isValid(DocumentDraft("Document", "other", tags = List(21) { "tag$it" })))
        assertFalse(DocumentDraftValidator.isValid(DocumentDraft("Document", "other", tags = listOf("x".repeat(41)))))
    }
}
