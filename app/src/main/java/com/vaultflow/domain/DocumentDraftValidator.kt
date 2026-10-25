package com.vaultflow.domain

import com.vaultflow.data.repository.DocumentDraft

object DocumentDraftValidator {
    fun isValid(draft: DocumentDraft): Boolean {
        val title = draft.title.trim()
        return title.isNotEmpty() && title.length <= 120 &&
            draft.tags.size <= 20 && draft.tags.all { it.trim().length <= 40 } &&
            (draft.documentDate == null || draft.expirationDate == null || draft.expirationDate >= draft.documentDate)
    }
}
