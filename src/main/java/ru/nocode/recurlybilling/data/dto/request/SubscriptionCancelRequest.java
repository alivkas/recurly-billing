package ru.nocode.recurlybilling.data.dto.request;

public record SubscriptionCancelRequest(
        Boolean cancelImmediately
) {
    public Boolean cancelImmediately() {
        return Boolean.TRUE.equals(cancelImmediately);
    }
}
