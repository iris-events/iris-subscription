package org.iris_events.subscription.validation;

import java.util.regex.Pattern;

import org.iris_events.subscription.exception.SubscriptionException;
import org.iris_events.subscription.model.Subscription;

public class SubscriptionValidator {
    private static final Pattern KEBAB_CASE_PATTERN = Pattern.compile("^([a-z][a-z0-9]*)(-[a-z0-9]+)*$");

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

        if (!KEBAB_CASE_PATTERN.matcher(resourceType).matches()) {
            throw new SubscriptionException("Subscription must have resourceType in kebab case pattern");
        }

    }
}
