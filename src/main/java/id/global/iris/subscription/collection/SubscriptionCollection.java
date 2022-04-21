package id.global.iris.subscription.collection;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import id.global.iris.subscription.model.Resource;
import id.global.iris.subscription.model.Subscription;

public class SubscriptionCollection {
    private final ConcurrentHashMap<Integer, Subscription> subscriptions;
    private final ConcurrentHashMap<String, Set<Integer>> sessionIdSubscriptions;
    private final ConcurrentHashMap<String, Set<Integer>> resTypeResIdSubscriptions;

    public SubscriptionCollection() {
        subscriptions = new ConcurrentHashMap<>();
        sessionIdSubscriptions = new ConcurrentHashMap<>();
        resTypeResIdSubscriptions = new ConcurrentHashMap<>();
    }

    /**
     * Insert new subscription
     *
     * @param subscription subscription record
     */
    public void insert(Subscription subscription) {
        synchronized (this) {
            int subscriptionId = generateSubscriptionId(subscription);
            subscriptions.put(subscriptionId, subscription);
            addSessionSubscription(subscription.sessionId(), subscriptionId);
            addResTypeResIdSubscription(subscription.resourceType(), subscription.resourceId(), subscriptionId);
        }
    }

    /**
     * Get a set of subscriptions matching provided resourceType and resourceId
     *
     * @param resource A Resource wrapper containing resourceType and resourceId
     * @return a set of subscription records
     */
    public Set<Subscription> get(Resource resource) {
        Set<Integer> subscriptionKeys = resTypeResIdSubscriptions.get(
                getUniqueResId(resource.resourceType(), resource.resourceId()));
        if (subscriptionKeys == null) {
            return Set.of();
        }
        return subscriptionKeys.stream().map(this.subscriptions::get).collect(Collectors.toSet());
    }

    /**
     * Get a set of subscriptions for the provided sessionId
     *
     * @param sessionId SessionId
     * @return a set of subscription records
     */
    public Set<Subscription> get(String sessionId) {
        Set<Integer> sessionIDs = sessionIdSubscriptions.get(sessionId);
        if (sessionIDs == null) {
            return Set.of();
        }
        return sessionIDs.stream().map(subscriptions::get).collect(Collectors.toSet());
    }

    /**
     * Remove subscriptions for the provided sessionId
     *
     * @param sessionId SessionId
     */
    public void remove(String sessionId) {
        synchronized (this) {
            Set<Integer> removedIDs = sessionIdSubscriptions.remove(sessionId);
            if (removedIDs != null) {
                removeBySubscriptionIDs(removedIDs);
            }
        }
    }

    /**
     * Remove subscriptions for the provided sessionId, resourceType and resourceId combination
     *
     * @param sessionId
     * @param resourceType
     * @param resourceId
     */
    public void remove(String sessionId, String resourceType, String resourceId) {
        synchronized (this) {
            final var uniqueResId = getUniqueResId(resourceType, resourceId);
            final var sessionSubs = sessionIdSubscriptions.get(sessionId);
            final var resTypeIdSubs = resTypeResIdSubscriptions.get(uniqueResId);

            final var intersect = resTypeIdSubs.stream().distinct().filter(sessionSubs::contains)
                    .collect(Collectors.toSet());

            sessionSubs.removeAll(intersect);
            resTypeIdSubs.removeAll(intersect);

            intersect.forEach(subscriptions::remove);

            if (sessionSubs.isEmpty()) {
                remove(sessionId);
            }
            if (resTypeIdSubs.isEmpty()) {
                final var removed = resTypeResIdSubscriptions.remove(uniqueResId);
                removeBySubscriptionIDs(removed);
            }
        }
    }

    /**
     * Get the number of all current subscription records
     *
     * @return number of all subscription records
     */
    public int size() {
        return subscriptions.size();
    }

    /**
     * Get the count of all subscription records linked to sessionIds
     *
     * @return number of subscription records
     */
    public int sessionSubscriptionCount() {
        return sessionIdSubscriptions.size();
    }

    private void removeBySubscriptionIDs(Set<Integer> subscriptionIDs) {
        subscriptionIDs.forEach(subscriptions::remove);
        sessionIdSubscriptions.values().forEach(s -> s.removeAll(subscriptionIDs));
        resTypeResIdSubscriptions.values().forEach(s -> s.removeAll(subscriptionIDs));
    }

    private void addSessionSubscription(String sessionId, int subscriptionId) {
        if (sessionId == null) {
            return;
        }
        if (!sessionIdSubscriptions.containsKey(sessionId)) {
            sessionIdSubscriptions.put(sessionId, ConcurrentHashMap.newKeySet());
        }
        sessionIdSubscriptions.get(sessionId).add(subscriptionId);
    }

    private void addResTypeResIdSubscription(String resourceType, String resourceId, int subscriptionId) {
        String uniqueResId = getUniqueResId(resourceType, resourceId);
        if (!resTypeResIdSubscriptions.containsKey(uniqueResId)) {
            resTypeResIdSubscriptions.put(uniqueResId, ConcurrentHashMap.newKeySet());
        }
        resTypeResIdSubscriptions.get(uniqueResId).add(subscriptionId);
    }

    private String getUniqueResId(String resourceType, String resourceId) {
        return String.format("%s_%s", resourceType, resourceId);
    }

    private int generateSubscriptionId(Subscription subscription) {
        return subscription.hashCode();
    }
}
