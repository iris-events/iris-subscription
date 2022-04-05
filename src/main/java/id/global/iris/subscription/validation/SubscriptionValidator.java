package id.global.iris.subscription.validation;

import id.global.iris.subscription.exception.SubscriptionException;
import id.global.iris.subscription.model.Subscription;

public class SubscriptionValidator {
    public static void validate(Subscription subscription) {
        String sessionId = subscription.sessionId();
        String resourceId = subscription.resourceId();
        String resourceType = subscription.resourceType();

        if (sessionId == null) {
            throw new SubscriptionException("Subscription must contain sessionId");
        }

        if (resourceType == null || resourceId == null) {
            throw new SubscriptionException("Subscription must contain resourceType and resourceId");
        }
    }
}
