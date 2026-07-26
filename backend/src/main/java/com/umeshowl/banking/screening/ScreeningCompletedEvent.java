package com.umeshowl.banking.screening;

public record ScreeningCompletedEvent(
        TransactionScreeningResult screeningResult
) {
}
