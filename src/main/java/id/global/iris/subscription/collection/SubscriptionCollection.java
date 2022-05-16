package id.global.iris.subscription.collection;

import java.util.Set;

import id.global.iris.subscription.model.Resource;
import id.global.iris.subscription.model.Subscription;

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
