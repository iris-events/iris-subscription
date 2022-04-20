package id.global.iris.subscription.collection;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import id.global.iris.subscription.model.Resource;
import id.global.iris.subscription.model.Subscription;

class SubscriptionCollectionTest {
    private SubscriptionCollection collection;
    private final static String RES_ID_TEMPLATE = "resourceId_%s";
    private final static String RES_TYPE_TEMPLATE = "resourceType_%s";
    private final static String SESSION_ID_TEMPLATE = "sessionId_%s";

    @BeforeEach
    public void setup() {
        this.collection = new SubscriptionCollection();

        collection.insert(getSubscription("1", "1", "1"));
        collection.insert(getSubscription("1", "1", "2"));
        collection.insert(getSubscription("2", "2", "1"));
        collection.insert(getSubscription("3", "3", "3"));
    }

    private Subscription getSubscription(String resourceType, String resourceId, String sessionId) {
        return new Subscription(
                getResourceTypeId(resourceType),
                getResourceId(resourceId),
                getSessionId(sessionId));
    }

    private String getSessionId(String sessionId) {
        return String.format(SESSION_ID_TEMPLATE, sessionId);
    }

    private String getResourceId(String resourceId) {
        return String.format(RES_ID_TEMPLATE, resourceId);
    }

    private String getResourceTypeId(String resourceType) {
        return String.format(RES_TYPE_TEMPLATE, resourceType);
    }

    @Test
    void insertSubscription() {
        final var subscription = getSubscription("temp", "temp", "temp");
        final var sizeBefore = collection.size();
        collection.insert(subscription);
        final var sizeAfter = collection.size();

        assertThat(sizeBefore, is(4));
        assertThat(sizeAfter, is(5));
    }

    @Test
    void getByResource() {
        String resourceId = getResourceId("1");
        String resourceType = getResourceTypeId("1");

        Set<Subscription> byResource = collection.get(new Resource(resourceType, resourceId));

        assertThat(byResource.size(), is(2));
        Optional<Subscription> optionalSubscription1 = byResource.stream()
                .filter(sub -> sub.sessionId().equals(getSessionId("1"))).findFirst();
        Optional<Subscription> optionalSubscription2 = byResource.stream()
                .filter(sub -> sub.sessionId().equals(getSessionId("2"))).findFirst();
        assertThat(optionalSubscription1.isPresent(), is(true));
        assertThat(optionalSubscription2.isPresent(), is(true));
        assertThat(optionalSubscription1.get(),
                is(new Subscription(getResourceTypeId("1"), getResourceId("1"), getSessionId("1"))));
        assertThat(optionalSubscription2.get(),
                is(new Subscription(getResourceTypeId("1"), getResourceId("1"), getSessionId("2"))));
    }

    @Test
    void getBySessionId() {
        final var sessionId = getSessionId("1");
        final var resourceId1 = getResourceId("1");
        final var resourceId2 = getResourceId("2");
        final var resourceTypeId1 = getResourceTypeId("1");
        final var resourceTypeId2 = getResourceTypeId("2");

        Set<Subscription> bySessionId = collection.get(sessionId);

        assertThat(bySessionId.size(), is(2));
        Optional<Subscription> optionalSubscription1 = bySessionId.stream()
                .filter(sub -> sub.resourceId().equals(resourceId1) && sub.resourceType().equals(resourceTypeId1))
                .findFirst();
        Optional<Subscription> optionalSubscription2 = bySessionId.stream()
                .filter(sub -> sub.resourceId().equals(resourceId2) && sub.resourceType().equals(resourceTypeId2))
                .findFirst();
        assertThat(optionalSubscription1.isPresent(), is(true));
        assertThat(optionalSubscription2.isPresent(), is(true));
        assertThat(optionalSubscription1.get(), is(new Subscription(resourceTypeId1, resourceId1, sessionId)));
        assertThat(optionalSubscription2.get(), is(new Subscription(resourceTypeId2, resourceId2, sessionId)));
    }

    @Test
    void removeBySessionIdResTypeResId() {
        final var sessionId1 = getSessionId("1");
        final var sessionId2 = getSessionId("2");
        final var sessionId3 = getSessionId("3");
        final var resourceId = getResourceId("1");
        final var resourceType = getResourceTypeId("1");

        collection.remove(sessionId1, resourceType, resourceId);
        collection.remove(sessionId2, resourceType, resourceId);

        final var subsBySessionId1 = collection.get(sessionId1);
        final var subsBySessionId2 = collection.get(sessionId2);
        final var subsBySessionId3 = collection.get(sessionId3);

        final var subBySessionId1 = subsBySessionId1.stream().findFirst();
        final var subBySessionId3 = subsBySessionId3.stream().findFirst();

        assertThat(collection.size(), is(2));
        assertThat(subsBySessionId1.size(), is(1));
        assertThat(subsBySessionId2.size(), is(0));
        assertThat(subsBySessionId3.size(), is(1));

        assertThat(subBySessionId1.isPresent(), is(true));
        assertThat(subBySessionId3.isPresent(), is(true));
    }

    @Test
    void removeBySessionId() {
        final var sessionId = getSessionId("1");
        collection.remove(sessionId);

        Set<Subscription> bySessionId = collection.get(sessionId);
        final var size = collection.size();

        assertThat(bySessionId.size(), is(0));
        assertThat(size, is(2));
    }

    @Test
    void userAndSessionSubscriptionCount() {
        assertThat(collection.sessionSubscriptionCount(), is(3));
    }

    @Test
    void insertDuplicateSubscription() {
        final var duplicateSubscription = getSubscription("1", "1", "1");
        final var sizeBeforeDuplicateInsert = collection.size();
        collection.insert(duplicateSubscription);

        assertThat(collection.size(), is(sizeBeforeDuplicateInsert));
    }
}
