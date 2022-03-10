package id.global.iris.subscription.collection;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import id.global.iris.subscription.model.Resource;
import id.global.iris.subscription.model.Subscription;

class SubscriptionCollectionTest {
    private SubscriptionCollection collection;

    @BeforeEach
    public void setup() {
        this.collection = new SubscriptionCollection();
    }

    @Test
    void insertSubscription() {
        String resourceId = "resourceId1";
        String resourceType = "resourceType1";
        String sessionId = "sessionId1";

        Subscription subscription1 = new Subscription(resourceType, resourceId, sessionId);
        collection.insert(subscription1);

        assertThat(collection.size(), is(1));
    }

    @Test
    void getByResource() {
        String resourceId = "resourceId";
        String resourceType = "resourceType";

        Subscription subscription1 = new Subscription(resourceType, resourceId, "sessionId");
        Subscription subscription2 = new Subscription(resourceType, resourceId, "sessionId2");
        Subscription subscription3 = new Subscription("otherResourceType", "otherResourceId", "sessionId");

        collection.insert(subscription1);
        collection.insert(subscription2);
        collection.insert(subscription3);

        Set<Subscription> byResource = collection.get(new Resource(resourceType, resourceId));

        assertThat(collection.size(), is(3));
        assertThat(byResource.size(), is(2));
        Optional<Subscription> optionalSubscription1 = byResource.stream().filter(sub -> sub.equals(subscription1)).findFirst();
        Optional<Subscription> optionalSubscription2 = byResource.stream().filter(sub -> sub.equals(subscription2)).findFirst();
        assertThat(optionalSubscription1.isPresent(), is(true));
        assertThat(optionalSubscription2.isPresent(), is(true));
        assertThat(optionalSubscription1.get(), is(subscription1));
        assertThat(optionalSubscription2.get(), is(subscription2));
    }

    @Test
    void getBySessionId() {
        String sessionId = "sessionId";
        Subscription subscription1 = new Subscription("resourceType1", "resourceId1", sessionId);
        Subscription subscription2 = new Subscription("resourceType2", "resourceId2", sessionId);
        Subscription subscription3 = new Subscription("resourceType3", "resourceId3", "sessionId3");

        collection.insert(subscription1);
        collection.insert(subscription2);
        collection.insert(subscription3);

        Set<Subscription> bySessionId = collection.get(sessionId);

        assertThat(collection.size(), is(3));
        assertThat(bySessionId.size(), is(2));

        Optional<Subscription> optionalSubscription1 = bySessionId.stream().filter(sub -> sub.equals(subscription1))
                .findFirst();
        Optional<Subscription> optionalSubscription2 = bySessionId.stream().filter(sub -> sub.equals(subscription2))
                .findFirst();
        assertThat(optionalSubscription1.isPresent(), is(true));
        assertThat(optionalSubscription2.isPresent(), is(true));
        assertThat(optionalSubscription1.get(), is(subscription1));
        assertThat(optionalSubscription2.get(), is(subscription2));
    }

    @Test
    void removeBySessionId() {
        String sessionId = "sessionId";
        String otherSessionId = "otherSessionId";
        Subscription subscription1 = new Subscription("resourceType", "resourceId", sessionId);
        Subscription subscription2 = new Subscription("resourceType", "resourceId", otherSessionId);

        collection.insert(subscription1);
        collection.insert(subscription2);
        collection.remove(sessionId);

        Set<Subscription> bySessionId = collection.get(sessionId);
        Set<Subscription> byOtherSessionId = collection.get(otherSessionId);
        Optional<Subscription> optionalByOtherSessionIdSubscription = byOtherSessionId.stream().findFirst();

        assertThat(collection.size(), is(1));
        assertThat(bySessionId.size(), is(0));
        assertThat(byOtherSessionId.size(), is(1));
        assertThat(optionalByOtherSessionIdSubscription.isPresent(), is(true));
        assertThat(optionalByOtherSessionIdSubscription.get(), is(subscription2));
    }

    @Test
    void remove() {
        String sessionId = "sessionId";
        String otherSessionId = "otherSessionId";
        Subscription subscription1 = new Subscription("resourceType", "resourceId", sessionId);
        Subscription subscription2 = new Subscription("resourceType", "resourceId", otherSessionId);

        collection.insert(subscription1);
        collection.insert(subscription2);
        collection.remove(sessionId);

        Set<Subscription> bySessionId = collection.get(sessionId);
        Set<Subscription> byOtherSessionId = collection.get(otherSessionId);
        Optional<Subscription> optionalByOtherSessionIdSubscription = byOtherSessionId.stream().findFirst();

        assertThat(collection.size(), is(1));
        assertThat(bySessionId.size(), is(0));
        assertThat(byOtherSessionId.size(), is(1));
    }

    @Test
    void userAndSessionSubscriptionCount() {
        Subscription subscription1 = new Subscription("resourceType", "resourceId", null);
        Subscription subscription2 = new Subscription("resourceType", "resourceId", "otherSessionId");

        collection.insert(subscription1);
        collection.insert(subscription2);

        assertThat(collection.sessionSubscriptionCount(), is(1));
    }

    @Test
    void insertMultipleIdenticalSubscriptions() {
        Subscription subscription1 = new Subscription("resourceType", "resourceId", null);
        Subscription subscription2 = new Subscription("resourceType", "resourceId", null);
        Subscription subscription3 = new Subscription("resourceType", "resourceId", "sessionId");
        Subscription subscription4 = new Subscription("resourceType", "resourceId", "sessionId");

        collection.insert(subscription1);
        collection.insert(subscription2);
        collection.insert(subscription3);
        collection.insert(subscription4);

        assertThat(collection.size(), is(2));
        assertThat(collection.sessionSubscriptionCount(), is(1));
    }

    @Test
    void multithreadedInsertRemoveTest() throws InterruptedException {
        ExecutorService exeService = Executors.newFixedThreadPool(16);
        String sessionId = "sessionId";
        Subscription sub = new Subscription("resourceType", "resourceId", sessionId);

        List<Callable<Void>> tasks = new ArrayList<>();
        IntStream.range(0, 1000).forEach(i -> {
            Callable<Void> task = () -> {
                collection.insert(sub);
                Thread.sleep(1);
                collection.remove(sessionId);
                return null;
            };
            tasks.add(task);
        });

        exeService.invokeAll(tasks);
        exeService.shutdown();
        assertThat(collection.size(), is(0));
    }

    @Test
    void twoThreadsInsertRemove() {
        String sessionId = "sessionId";
        Subscription sub = new Subscription("resourceType", "resourceId", sessionId);

        List<Thread> threadList = new ArrayList<>();

        IntStream.range(0, 10).forEach(i -> {
            Thread threadInsert = new Thread(() -> {
                try {
                    Thread.sleep((long) (Math.random() * 5));
                    collection.insert(sub);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            Thread threadRemove = new Thread(() -> {
                try {
                    Thread.sleep((long) (Math.random() * 5));
                    collection.remove(sessionId);
                    assertThat(collection.get(sessionId).size(), is(0));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            threadList.add(threadInsert);
            threadList.add(threadRemove);
        });

        threadList.forEach(Thread::start);
    }
}
