package id.global.iris.subscription.exception;

public class SubscriptionException extends RuntimeException {
    public SubscriptionException(String message) {
        super(message);
    }

    public SubscriptionException(final String message, final Throwable e) {
        super(message, e);
    }
}
