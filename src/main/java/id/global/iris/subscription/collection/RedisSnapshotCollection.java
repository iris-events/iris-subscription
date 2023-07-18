package id.global.iris.subscription.collection;

import static id.global.iris.subscription.collection.Utils.getResourceSnapshotKey;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.iris.subscription.exception.SubscriptionException;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RedisSnapshotCollection {
    private static final Logger log = LoggerFactory.getLogger(RedisSnapshotCollection.class);
    RedisDataSource dataSource;
    ObjectMapper objectMapper;
    private final ValueCommands<String, byte[]> commands;

    public RedisSnapshotCollection(final RedisDataSource dataSource, final ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.commands = dataSource.value(byte[].class);
    }

    public void insert(final String resourceType, final String resourceId, final Snapshot value, final Integer ttl) {
        if (ttl <= 0) {
            log.warn("Trying to cache snapshot with non positive ttl. resourceType={}, resourceId={}, ttl={}", resourceType,
                    resourceId, ttl);
            return;
        }
        final var key = getResourceSnapshotKey(resourceType, resourceId);
        log.debug("Inserting snapshot. resourceType={}, resourceId={}, key={}", resourceType, resourceId, key);
        try {
            commands.setex(key, ttl, objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException e) {
            throw new SubscriptionException(String.format("Could not process snapshot into json. resourceKey=%s", key), e);
        }
    }

    public Optional<Snapshot> get(final String resourceType, final String resourceId) {
        final var key = getResourceSnapshotKey(resourceType, resourceId);
        final var bytes = commands.get(key);
        if (bytes == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(bytes, Snapshot.class));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
