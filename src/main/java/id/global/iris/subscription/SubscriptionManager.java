package id.global.iris.subscription;

import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import id.global.iris.subscription.collection.SubscriptionCollection;
import id.global.iris.subscription.model.Resource;
import id.global.iris.subscription.model.Subscription;
import id.global.iris.subscription.validation.SubscriptionValidator;
import io.quarkus.scheduler.Scheduled;

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
