package org.iris_events.subscription.collection;

import org.iris_events.subscription.model.Subscription;

public class Utils {

    public static final String SUB_TEMPLATE = "subscription|%s";
    public static final String SESSION_SUB_TEMPLATE = "sessionIdSub|%s";
    public static final String RESOURCE_SNAP_TEMPLATE = "resTypeResIdSnap|%s";
    public static final String RESOURCE_SUB_TEMPLATE = "resTypeResIdSub|%s";
    public static final String PIPE = "|";

    public static String getResourceSubscriptionsSetId(final String resourceType, final String resourceId) {
        final var uniqueResId = getUniqueResId(resourceType, resourceId);
        return String.format(RESOURCE_SUB_TEMPLATE, uniqueResId);
    }

    public static String getResourceSnapshotKey(final String resourceType, final String resourceId) {
        final var uniqueResId = getUniqueResId(resourceType, resourceId);
        return String.format(RESOURCE_SNAP_TEMPLATE, uniqueResId);
    }

    public static String getUniqueResId(String resourceType, String resourceId) {
        return String.format("%s|%s", resourceType, resourceId);
    }

    public static String getSessionSubscriptionsSetId(final String sessionId) {
        return String.format(SESSION_SUB_TEMPLATE, sessionId);
    }

    public static String generateSubscriptionId(Subscription subscription) {
        final var subId = subscription.sessionId() + PIPE + subscription.resourceType() + PIPE + subscription.resourceId();
        return String.format(SUB_TEMPLATE, subId);
    }
}
