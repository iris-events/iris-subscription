package org.iris_events.subscription.collection;

import org.iris_events.subscription.model.Subscription;

public class Utils {

    public static final String SUB_TEMPLATE = "subscription|%s";
    public static final String SESSION_SUB_TEMPLATE = "sessionIdSub|%s";
    public static final String RESOURCE_SNAP_TEMPLATE = "resTypeResIdSnap|%s";
    public static final String RESOURCE_SUB_TEMPLATE = "resTypeResIdSub|%s";
    public static final String PIPE = "|";
    public static final String SUBSCRIPTION_ID_DELIMITER = "\\|";
    public static final int MIN_SUBSCRIPTION_PARTS = 4;
    public static final int RESOURCE_TYPE_INDEX = 2;
    public static final int RESOURCE_ID_INDEX = 3;


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

    public static boolean isValidSubscriptionId(String subscriptionId) {
        return subscriptionId != null &&
                subscriptionId.split(SUBSCRIPTION_ID_DELIMITER).length >= MIN_SUBSCRIPTION_PARTS;
    }

    public static String getResourceSetKey(String subscriptionId) {
        String[] parts = subscriptionId.split(SUBSCRIPTION_ID_DELIMITER);
        return Utils.getResourceSubscriptionsSetId(parts[RESOURCE_TYPE_INDEX], parts[RESOURCE_ID_INDEX]);
    }
}
