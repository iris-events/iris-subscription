package org.iris_events.subscription.collection;

import java.util.Set;

import org.iris_events.subscription.model.Resource;
import org.iris_events.subscription.model.Subscription;

public interface SubscriptionCollection {
    void insert(Subscription subscription);

    Set<Subscription> get(Resource resource);

    Set<Subscription> get(String sessionId);

    void remove(String sessionId);

    void remove(String sessionId, String resourceType, String resourceId);

    int size();

    int sessionSubscriptionCount();

    void cleanUp();
}
