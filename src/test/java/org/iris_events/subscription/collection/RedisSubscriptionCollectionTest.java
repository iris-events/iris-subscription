package org.iris_events.subscription.collection;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.vertx.redis.client.Response;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.iris_events.subscription.model.Resource;
import org.iris_events.subscription.model.Subscription;

import io.quarkus.redis.client.RedisClient;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class RedisSubscriptionCollectionTest {

    private final static String RES_ID_TEMPLATE = "resourceId%s";
    private final static String RES_TYPE_TEMPLATE = "resourceType%s";
    private final static String SESSION_ID_TEMPLATE = "sessionId%s";

    @Inject
    RedisClient redisClient;

    @Inject
    SubscriptionCollection subscriptionCollection;

    @BeforeEach
    public void setup() {
        redisClient.flushdb(List.of());

        subscriptionCollection.insert(getSubscription("1", "1", "1"));
        subscriptionCollection.insert(getSubscription("1", "1", "2"));
        subscriptionCollection.insert(getSubscription("2", "2", "1"));
        subscriptionCollection.insert(getSubscription("3", "3", "3"));
    }

    @Test
    void insert() {
        final var subscription = getSubscription("temp", "temp", "temp");

        final var sizeBefore = subscriptionCollection.size();
        subscriptionCollection.insert(subscription);
        final var sizeAfter = subscriptionCollection.size();
        subscriptionCollection.insert(subscription);
        final var sizeAfterDuplicate = subscriptionCollection.size();

        assertThat(sizeBefore, is(4));
        assertThat(sizeAfter, is(5));
        assertThat(sizeAfterDuplicate, is(5));
    }

    @Test
    void getByResource() {
        String resourceId = getResourceId("1");
        String resourceType = getResourceTypeId("1");

        Set<Subscription> byResource = subscriptionCollection.get(new Resource(resourceType, resourceId));

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

        Set<Subscription> bySessionId = subscriptionCollection.get(sessionId);

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
    void removeBySessionId() {
        final var sessionId = getSessionId("1");
        subscriptionCollection.remove(sessionId);

        Set<Subscription> bySessionId = subscriptionCollection.get(sessionId);
        final var size = subscriptionCollection.size();

        assertThat(bySessionId.size(), is(0));
        assertThat(size, is(2));
    }

    @Test
    void testBatchedRemovalByResourceSet() {
        final var resourceType = "sharedResourceType";
        final var resourceId = "sharedResourceId";

        subscriptionCollection.insert(new Subscription(resourceType, resourceId, "session1"));
        subscriptionCollection.insert(new Subscription(resourceType, resourceId, "session2"));
        subscriptionCollection.insert(new Subscription(resourceType, resourceId, "session3"));

        subscriptionCollection.insert(new Subscription("otherType", "otherId", "session1"));

        assertThat(subscriptionCollection.size(), is(8)); // 4 original + 4 new

        // Remove session1 (should remove 2 subscriptions)
        subscriptionCollection.remove("session1");

        // Verify results
        assertThat(subscriptionCollection.size(), is(6)); // 8 - 2 = 6
        assertThat(subscriptionCollection.get("session1").size(), is(0));
        assertThat(subscriptionCollection.get(new Resource(resourceType, resourceId)).size(), is(2)); // session2, session3
    }

    @Test
    void testRemovalFromResourceSets() {
        // Setup: Create subscriptions
        final var sessionId = getSessionId("1");
        final var resourceType = getResourceTypeId("1");
        final var resourceId = getResourceId("1");

        subscriptionCollection.insert(getSubscription("1", "1", "1"));
        subscriptionCollection.insert(getSubscription("1", "1", "2")); // Same resource, different session

        // Verify setup: Both subscriptions should be in the resource set
        final var resourceSetKey = Utils.getResourceSubscriptionsSetId(resourceType, resourceId);
        final var subscriptionsInSet = redisClient.smembers(resourceSetKey);
        assertThat(subscriptionsInSet.size(), is(2));

        // Remove session1
        subscriptionCollection.remove(sessionId);

        // Verify: Only session1's subscription should be removed from resource set
        final var subscriptionsAfterRemoval = redisClient.smembers(resourceSetKey);
        assertThat(subscriptionsAfterRemoval.size(), is(1));

        // Verify the remaining subscription belongs to session2
        final var remainingSubscription = subscriptionsAfterRemoval.stream()
                .map(Response::toString)
                .findFirst()
                .orElse("");
        assertThat(remainingSubscription, containsString("sessionId2"));
    }

    @Test
    void testRemovalFromMultipleResourceSets() {
        final var sessionId = getSessionId("1");

        // Session1 has subscriptions to 2 different resources
        subscriptionCollection.insert(getSubscription("1", "1", "1")); // resource1
        subscriptionCollection.insert(getSubscription("2", "2", "1")); // resource2

        // Other sessions also have subscriptions to these resources
        subscriptionCollection.insert(getSubscription("1", "1", "2")); // resource1
        subscriptionCollection.insert(getSubscription("2", "2", "3")); // resource2

        // Get resource set keys
        final var resourceSet1Key = Utils.getResourceSubscriptionsSetId(getResourceTypeId("1"), getResourceId("1"));
        final var resourceSet2Key = Utils.getResourceSubscriptionsSetId(getResourceTypeId("2"), getResourceId("2"));

        // Verify setup: Each resource set should have 2 subscriptions
        assertThat(redisClient.smembers(resourceSet1Key).size(), is(2));
        assertThat(redisClient.smembers(resourceSet2Key).size(), is(2));

        // Remove session1
        subscriptionCollection.remove(sessionId);

        // Verify: Session1's subscriptions removed from both resource sets
        assertThat(redisClient.smembers(resourceSet1Key).size(), is(1));
        assertThat(redisClient.smembers(resourceSet2Key).size(), is(1));

        // Verify remaining subscriptions don't belong to session1
        final var remaining1 = redisClient.smembers(resourceSet1Key).stream()
                .map(Response::toString)
                .findFirst().orElse("");
        final var remaining2 = redisClient.smembers(resourceSet2Key).stream()
                .map(Response::toString)
                .findFirst().orElse("");

        assertThat(remaining1, not(containsString("sessionId1")));
        assertThat(remaining2, not(containsString("sessionId1")));
    }

    @Test
    void removeBySessionIdResTypeResId() {
        final var sessionId1 = getSessionId("1");
        final var sessionId2 = getSessionId("2");
        final var sessionId3 = getSessionId("3");
        final var resourceId1 = getResourceId("1");
        final var resourceType = getResourceTypeId("1");

        subscriptionCollection.insert(getSubscription("1", "2", "1"));
        subscriptionCollection.insert(getSubscription("1", "3", "1"));

        subscriptionCollection.remove(sessionId1, resourceType, null);
        subscriptionCollection.remove(sessionId2, resourceType, resourceId1);

        final var subsBySessionId1 = subscriptionCollection.get(sessionId1);
        final var subsBySessionId2 = subscriptionCollection.get(sessionId2);
        final var subsBySessionId3 = subscriptionCollection.get(sessionId3);

        final var subBySessionId1 = subsBySessionId1.stream().findFirst();
        final var subBySessionId3 = subsBySessionId3.stream().findFirst();

        subscriptionCollection.cleanUp();
        assertThat(subscriptionCollection.size(), is(2));
        assertThat(subsBySessionId1.size(), is(1));
        assertThat(subsBySessionId2.size(), is(0));
        assertThat(subsBySessionId3.size(), is(1));

        assertThat(subBySessionId1.isPresent(), is(true));
        assertThat(subBySessionId3.isPresent(), is(true));
    }

    @Test
    void size() {
        assertThat(subscriptionCollection.size(), is(4));
    }

    @Test
    void userAndSessionSubscriptionCount() {
        assertThat(subscriptionCollection.sessionSubscriptionCount(), is(3));
    }

    @Test
    void insertDuplicateSubscription() {
        final var duplicateSubscription = getSubscription("1", "1", "1");
        final var sizeBeforeDuplicateInsert = subscriptionCollection.size();
        subscriptionCollection.insert(duplicateSubscription);

        assertThat(subscriptionCollection.size(), CoreMatchers.is(sizeBeforeDuplicateInsert));
    }

    @Test
    public void testCleanup() {
        final var sessionSubscriptionCountBefore = subscriptionCollection.sessionSubscriptionCount();
        final var sizeBefore = subscriptionCollection.size();

        redisClient.del(List.of("subscription|sessionId1|resourceType1|resourceId1"));
        redisClient.del(List.of("subscription|sessionId2|resourceType1|resourceId1"));

        final var sessionSubscriptionCountAfterDel = subscriptionCollection.sessionSubscriptionCount();
        final var sizeAfterDel = subscriptionCollection.size();

        subscriptionCollection.cleanUp();

        final var sessionSubscriptionCountAfterCleanup = subscriptionCollection.sessionSubscriptionCount();
        final var sizeAfterCleanup = subscriptionCollection.size();

        assertThat(sizeBefore, is(4));
        assertThat(sizeAfterDel, is(2));
        assertThat(sizeAfterCleanup, is(2));

        assertThat(sessionSubscriptionCountBefore, is(3));
        assertThat(sessionSubscriptionCountAfterDel, is(3));
        assertThat(sessionSubscriptionCountAfterCleanup, is(2));
    }

    @Test
    public void invalidSubscription() {
        final var subscriptionId = "subscription|session123123|resourceType123123|resourceId123123";
        final var sessionSubId = "sessionIdSub|session123123";
        final var resSubscriptionsId = "resTypeResIdSub|resourceType123123|resourceId123123";
        final var notAJson = "someText,not a json";
        final var resource = new Resource("resourceType123123", "resourceId123123");

        redisClient.set(List.of(subscriptionId, notAJson));
        redisClient.sadd(List.of(sessionSubId, subscriptionId));
        redisClient.sadd(List.of(resSubscriptionsId, subscriptionId));

        final var subscriptions = subscriptionCollection.get(resource);
        assertThat(subscriptions, is(Collections.emptySet()));
    }

    @Test
    public void nonExistentSubscription() {
        final var subscriptionId = "subscription|session123123|resourceType123123|resourceId123123";
        final var resSubscriptionsId = "resTypeResIdSub|resourceType123123|resourceId123123";
        final var resource = new Resource("resourceType123123", "resourceId123123");
        redisClient.sadd(List.of(resSubscriptionsId, subscriptionId));

        final var subscriptions = subscriptionCollection.get(resource);
        assertThat(subscriptions, is(Collections.emptySet()));
    }

    protected Subscription getSubscription(String resourceType, String resourceId, String sessionId) {
        return new Subscription(
                getResourceTypeId(resourceType),
                getResourceId(resourceId),
                getSessionId(sessionId));
    }

    protected String getSessionId(String sessionId) {
        return String.format(SESSION_ID_TEMPLATE, sessionId);
    }

    protected String getResourceId(String resourceId) {
        return String.format(RES_ID_TEMPLATE, resourceId);
    }

    protected String getResourceTypeId(String resourceType) {
        return String.format(RES_TYPE_TEMPLATE, resourceType);
    }
}
