package org.iris_events.subscription.collection;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.iris_events.subscription.model.Subscription;
import io.quarkus.redis.client.RedisClient;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class RedisSubscriptionCollectionMultithreadedTest {
    @Inject
    RedisSubscriptionCollection collection;

    @Inject
    RedisClient redisClient;

    @BeforeEach
    public void setup() {
        redisClient.flushdb(List.of());
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
