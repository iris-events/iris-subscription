package id.global.iris.subscription;

import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import id.global.iris.subscription.collection.SubscriptionCollection;
import id.global.iris.subscription.model.Resource;
import id.global.iris.subscription.model.Subscription;
import id.global.iris.subscription.validation.SubscriptionValidator;

@ApplicationScoped
public class SubscriptionManager {
    private final SubscriptionCollection subscriptionCollection;

    @Inject
    public SubscriptionManager() {
        subscriptionCollection = new SubscriptionCollection();
    }

    SubscriptionManager(SubscriptionCollection collection) {
        this.subscriptionCollection = collection;
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
