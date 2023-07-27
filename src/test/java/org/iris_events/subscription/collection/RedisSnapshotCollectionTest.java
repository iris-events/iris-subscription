package org.iris_events.subscription.collection;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class RedisSnapshotCollectionTest {

    @Inject
    RedisDataSource dataSource;
    @Inject
    RedisSnapshotCollection snapshotCollection;

    @BeforeEach
    public void setup() {
        dataSource.flushall();

        snapshotCollection.insert("1", "1", new Snapshot("one", "route-one", "message-one".getBytes()), 10);
        snapshotCollection.insert("2", "2", new Snapshot("two", "route-two", "message-two".getBytes()), 10);
        snapshotCollection.insert("3", "3", new Snapshot("three", "route-three", "message-three".getBytes()), 10);
        snapshotCollection.insert("3", "4", new Snapshot("three-four", "route-three", "message-three-four".getBytes()), 10);
    }

    @Test
    void insert() {
        final var sizeBefore = dataSource.execute("DBSIZE").toInteger();
        final var snapshot = new Snapshot("temp", "route-temp", "message-temp".getBytes());
        snapshotCollection.insert("temp", "temp", snapshot, 10);
        final var sizeAfter = dataSource.execute("DBSIZE").toInteger();
        snapshotCollection.insert("temp", "temp", snapshot, 10);
        final var sizeAfterDuplicate = dataSource.execute("DBSIZE").toInteger();

        assertThat(sizeBefore, is(4));
        assertThat(sizeAfter, is(5));
        assertThat(sizeAfterDuplicate, is(5));
    }

    @Test
    void expire() {
        final var sizeBefore = dataSource.execute("DBSIZE").toInteger();
        final var snapshot = new Snapshot("temp", "route-temp", "message-temp".getBytes());
        snapshotCollection.insert("temp", "temp", snapshot, 1);
        final var sizeAfter = dataSource.execute("DBSIZE").toInteger();

        assertThat(sizeBefore, is(4));
        assertThat(sizeAfter, is(5));

        await().atMost(2, SECONDS).until(() -> dataSource.execute("DBSIZE").toInteger() == 4);
    }

}
