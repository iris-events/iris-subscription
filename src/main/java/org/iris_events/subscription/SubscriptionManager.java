package org.iris_events.subscription;

import java.util.Set;

import org.iris_events.subscription.collection.SubscriptionCollection;
import org.iris_events.subscription.model.Resource;
import org.iris_events.subscription.model.Subscription;
import org.iris_events.subscription.validation.SubscriptionValidator;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SubscriptionManager {
    SubscriptionCollection subscriptionCollection;

    @Inject
    public SubscriptionManager(SubscriptionCollection subscriptionCollection) {
        this.subscriptionCollection = subscriptionCollection;
    }

    @Scheduled(every = "PT12H")
    public void cleanup() {
        this.subscriptionCollection.cleanUp();
    }

    public void addSubscription(Subscription subscription) {
        SubscriptionValidator.validate(subscription);
        subscriptionCollection.insert(subscription);
    }

    public Set<Subscription> getSubscriptions(String resourceType, String resourceId) {
        return subscriptionCollection.get(new Resource(resourceType, resourceId));
    }

    public void unsubscribe(String sessionId) {
        subscriptionCollection.remove(sessionId);
    }

    public void unsubscribe(String sessionId, String resourceType, String resourceId) {
        subscriptionCollection.remove(sessionId, resourceType, resourceId);
    }
}
