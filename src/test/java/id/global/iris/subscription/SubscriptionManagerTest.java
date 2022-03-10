package id.global.iris.subscription;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import id.global.iris.subscription.collection.SubscriptionCollection;
import id.global.iris.subscription.model.Resource;
import id.global.iris.subscription.model.Subscription;

class SubscriptionManagerTest {
    private SubscriptionManager manager;
    private SubscriptionCollection collection;

    @BeforeEach
    public void setup() {
        collection = Mockito.mock(SubscriptionCollection.class);
        manager = new SubscriptionManager(collection);
    }

    @Test
    void addSubscription() {
        String sessionId = "sessionId";
        String resourceType = "resourceType";
        String resourceId = "resourceId";

        Subscription subscription = new Subscription(resourceType, resourceId, sessionId);

        manager.addSubscription(subscription);

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        Mockito.verify(collection).insert(subscriptionCaptor.capture());
        Subscription value = subscriptionCaptor.getValue();

        assertThat(value, is(notNullValue()));
        assertThat(value.sessionId(), is(sessionId));
        assertThat(value.resourceId(), is(resourceId));
        assertThat(value.resourceType(), is(resourceType));
    }

    @Test
    void getSubscriptions() {
        String resourceType = "resourceType";
        String resourceId = "resourceId";
        manager.getSubscriptions(resourceType, resourceId);

        ArgumentCaptor<Resource> resourceCaptor = ArgumentCaptor.forClass(Resource.class);
        Mockito.verify(collection).get(resourceCaptor.capture());
        Resource value = resourceCaptor.getValue();

        assertThat(value, is(notNullValue()));
        assertThat(value.resourceId(), is(resourceId));
        assertThat(value.resourceType(), is(resourceType));
    }

    @Test
    void unsubscribeSession() {
        String sessionId = "sessionId";
        manager.unsubscribe(sessionId);

        ArgumentCaptor<String> sessionIdCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(collection).remove(sessionIdCaptor.capture());
        String value = sessionIdCaptor.getValue();

        assertThat(value, is(notNullValue()));
        assertThat(value, is(sessionId));
    }
}