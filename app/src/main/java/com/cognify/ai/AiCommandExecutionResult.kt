package com.cognify.ai

data class AiCommandExecutionResult(
    val status: Status,
    val message: String,
    val confirmationDetails: ConfirmationDetails? = null
) {
    enum class Status {
        SUCCESS,
        VALIDATION_ERROR,
        NOT_FOUND,
        AMBIGUOUS_TARGET,
        UNSUPPORTED_ACTION,
        REQUIRES_CONFIRMATION,
        FAILURE
    }
}

sealed class ConfirmationDetails {
    data class Delete(
        val itemType: String,
        val itemTitle: String,
        val additionalInfo: String? = null
    ) : ConfirmationDetails()

    data class Update(
        val itemType: String,
        val itemTitle: String,
        val changes: List<Change>
    ) : ConfirmationDetails() {
        data class Change(
            val fieldName: String,
            val oldValue: String?,
            val newValue: String?
        )
    }
}
