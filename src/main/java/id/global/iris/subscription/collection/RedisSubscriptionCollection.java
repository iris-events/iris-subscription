package id.global.iris.subscription.collection;

import static id.global.iris.subscription.collection.Utils.RESOURCE_SUB_TEMPLATE;
import static id.global.iris.subscription.collection.Utils.SESSION_SUB_TEMPLATE;
import static id.global.iris.subscription.collection.Utils.SUB_TEMPLATE;
import static id.global.iris.subscription.collection.Utils.generateSubscriptionId;
import static id.global.iris.subscription.collection.Utils.getResourceSubscriptionsSetId;
import static id.global.iris.subscription.collection.Utils.getSessionSubscriptionsSetId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.iris.subscription.exception.SubscriptionException;
import id.global.iris.subscription.model.Resource;
import id.global.iris.subscription.model.Subscription;
import io.quarkus.redis.client.RedisClient;
import io.vertx.redis.client.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped()
public class RedisSubscriptionCollection implements SubscriptionCollection {
    private static final Logger log = LoggerFactory.getLogger(RedisSubscriptionCollection.class);

    @ConfigProperty(name = "subscription.collection.redis.ttl", defaultValue = "86400")
    String TTL;

    @Inject
    RedisClient redisClient;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void insert(final Subscription subscription) {
        final var sessionId = subscription.sessionId();

        if (sessionId == null) {
            return;
        }

        final var resourceType = subscription.resourceType();
        final var resourceId = subscription.resourceId();

        try {
            final var subscriptionJson = objectMapper.writeValueAsString(subscription);
            final var subscriptionId = generateSubscriptionId(subscription);
            final var sessionSubId = getSessionSubscriptionsSetId(sessionId);
            final var resSubscriptionsId = getResourceSubscriptionsSetId(resourceType, resourceId);

            redisClient.set(List.of(subscriptionId, subscriptionJson));
            redisClient.expire(subscriptionId, TTL);
            redisClient.sadd(List.of(sessionSubId, subscriptionId));
            redisClient.sadd(List.of(resSubscriptionsId, subscriptionId));
        } catch (JsonProcessingException e) {
            throw new SubscriptionException("Could not process subscription into json", e);
        }
    }

    @Override
    public Set<Subscription> get(final Resource resource) {
        final var resourceId = resource.resourceId();
        final var resourceType = resource.resourceType();
        final var resSubscriptionsId = getResourceSubscriptionsSetId(resourceType, resourceId);

        final var subscriptionIds = mapResponseToStringList(redisClient.smembers(resSubscriptionsId));
        return getSubscriptionsBySubscriptionIds(subscriptionIds);
    }

    @Override
    public Set<Subscription> get(final String sessionId) {
        final var sessionSubscriptionsSetId = getSessionSubscriptionsSetId(sessionId);
        final var subscriptionIds = mapResponseToStringList(redisClient.smembers(sessionSubscriptionsSetId));
        return getSubscriptionsBySubscriptionIds(subscriptionIds);
    }

    @Override
    public void remove(final String sessionId) {
        final var sessionSubscriptionsSetId = getSessionSubscriptionsSetId(sessionId);
        final var sessionSubscriptionIds = redisClient.smembers(sessionSubscriptionsSetId).stream().map(Response::toString)
                .toList();
        final var idsToRemove = Stream.concat(Stream.of(sessionSubscriptionsSetId), sessionSubscriptionIds.stream()).toList();
        redisClient.del(idsToRemove);
    }

    @Override
    public void remove(final String sessionId, final String resourceType, final String resourceId) {
        final var sessionSubscriptionsSetId = getSessionSubscriptionsSetId(sessionId);
        if (redisClient.exists(List.of(sessionSubscriptionsSetId)).toInteger() <= 0) {
            return;
        }

        final var resourceSubscriptionsSetId = getResourceSubscriptionsSetId(resourceType, resourceId);
        final var sessionSubscriptionIds = redisClient.smembers(sessionSubscriptionsSetId).stream().map(Response::toString);
        final var resourceSubscriptionIds = redisClient.smembers(resourceSubscriptionsSetId).stream().map(Response::toString)
                .collect(Collectors.toSet());

        final var intersect = sessionSubscriptionIds.distinct()
                .filter(resourceSubscriptionIds::contains)
                .collect(Collectors.toSet());

        // remove all subscriptions by intersected IDS
        if (intersect.isEmpty()) {
            return;
        }
        redisClient.del(intersect.stream().toList());
        redisClient.srem(Stream.concat(Stream.of(sessionSubscriptionsSetId), intersect.stream()).toList());
        redisClient.srem(Stream.concat(Stream.of(resourceSubscriptionsSetId), intersect.stream()).toList());
    }

    @Override
    public int size() {
        return scanForSize(SUB_TEMPLATE);
    }

    @Override
    public int sessionSubscriptionCount() {
        return scanForSize(SESSION_SUB_TEMPLATE);
    }

    @Override
    public void cleanUp() {
        cleanSubscriptionPointers(RESOURCE_SUB_TEMPLATE);
        cleanSubscriptionPointers(SESSION_SUB_TEMPLATE);
    }

    private void cleanSubscriptionPointers(String subTemplate) {
        var scanCursor = "0";
        var subscriptionIdsToRemove = new HashMap<String, List<String>>();
        do {
            final var subscriptionScanResult = redisClient.scan(
                    List.of(scanCursor, "match", String.format(subTemplate, "*")));
            scanCursor = subscriptionScanResult.get(0).toString();

            subscriptionScanResult.get(1).stream().map(Response::toString)
                    .forEach(subscriptionPointerSet -> redisClient.smembers(subscriptionPointerSet).stream()
                            .map(res -> List.of(res.toString()))
                            .filter(subscriptionId -> redisClient.exists(subscriptionId).toLong() == 0L)
                            .forEach(forRemoval -> subscriptionIdsToRemove.computeIfAbsent(subscriptionPointerSet,
                                    k -> new ArrayList<>()).addAll(forRemoval)));
        } while (!scanCursor.equals("0"));

        subscriptionIdsToRemove.forEach((key, value) -> redisClient.srem(
                Stream.concat(Stream.of(key), value.stream()).collect(Collectors.toList())));
    }

    private Set<Subscription> getSubscriptionsBySubscriptionIds(final List<String> subscriptionIds) {
        if (subscriptionIds.isEmpty()) {
            return Set.of();
        }

        return redisClient.mget(subscriptionIds).stream().filter(Objects::nonNull)
                .map(Response::toString)
                .map(this::deserializeSubscriptionJson)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    private Optional<Subscription> deserializeSubscriptionJson(final String json) {
        try {
            return Optional.of(objectMapper.readValue(json, Subscription.class));
        } catch (JsonProcessingException e) {
            log.error("Could not deserialize subscription json {}", json, e);
        }
        return Optional.empty();
    }

    private int scanForSize(String subTemplate) {
        var scanCursor = "0";
        var size = 0;

        do {
            final var scanResponse = redisClient.scan(List.of(scanCursor, "match", String.format(subTemplate, "*")));
            scanCursor = scanResponse.get(0).toString();
            size = size + scanResponse.get(1).size();
        } while (!scanCursor.equals("0"));
        return size;

    }

    private List<String> mapResponseToStringList(final Response smembers) {
        return smembers.stream().map(Response::toString).toList();
    }
}
