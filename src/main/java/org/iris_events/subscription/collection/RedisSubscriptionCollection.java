package org.iris_events.subscription.collection;

import static org.iris_events.subscription.collection.Utils.RESOURCE_SUB_TEMPLATE;
import static org.iris_events.subscription.collection.Utils.SESSION_SUB_TEMPLATE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.iris_events.subscription.model.Resource;
import org.iris_events.subscription.model.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.iris_events.subscription.exception.SubscriptionException;

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
            final var subscriptionId = Utils.generateSubscriptionId(subscription);
            final var sessionSubId = Utils.getSessionSubscriptionsSetId(sessionId);
            final var resSubscriptionsId = Utils.getResourceSubscriptionsSetId(resourceType, resourceId);

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
        final var resSubscriptionsId = Utils.getResourceSubscriptionsSetId(resourceType, resourceId);

        final var subscriptionIds = mapResponseToStringList(redisClient.smembers(resSubscriptionsId));
        return getSubscriptionsBySubscriptionIds(subscriptionIds);
    }

    @Override
    public Set<Subscription> get(final String sessionId) {
        final var sessionSubscriptionsSetId = Utils.getSessionSubscriptionsSetId(sessionId);
        final var subscriptionIds = mapResponseToStringList(redisClient.smembers(sessionSubscriptionsSetId));
        return getSubscriptionsBySubscriptionIds(subscriptionIds);
    }

    @Override
    public void remove(final String sessionId) {
        final var sessionSubscriptionsSetId = Utils.getSessionSubscriptionsSetId(sessionId);
        final var sessionSubscriptionIds = redisClient.smembers(sessionSubscriptionsSetId).stream().map(Response::toString)
                .toList();

        removeFromResourceIndexes(sessionSubscriptionIds);
        final var idsToRemove = Stream.concat(Stream.of(sessionSubscriptionsSetId), sessionSubscriptionIds.stream()).toList();
        redisClient.del(idsToRemove);
    }

    @Override
    public void remove(final String sessionId, final String resourceType, final String resourceId) {
        final var sessionSubscriptionsSetId = Utils.getSessionSubscriptionsSetId(sessionId);
        if (redisClient.exists(List.of(sessionSubscriptionsSetId)).toInteger() <= 0) {
            return;
        }

        final var sessionSubscriptionIds = redisClient.smembers(sessionSubscriptionsSetId)
                .stream()
                .map(Response::toString)
                .toList();

        if (resourceId == null) {
            final var sessionsByResourceId = scanByResourceType(resourceType);
            sessionsByResourceId
                    .forEach((resourceSubscriptionsSetId, allSessionSubscriptionIds) -> {
                        final var intersect = allSessionSubscriptionIds.stream().distinct()
                                .filter(sessionSubscriptionIds::contains)
                                .collect(Collectors.toSet());
                        removeForResourceAndSession(sessionSubscriptionsSetId, resourceSubscriptionsSetId, intersect);
                    });
        } else {
            final var resourceSubscriptionsSetId = Utils.getResourceSubscriptionsSetId(resourceType, resourceId);
            final var resourceSubscriptionIds = redisClient.smembers(resourceSubscriptionsSetId).stream()
                    .map(Response::toString)
                    .collect(Collectors.toSet());
            final var intersect = sessionSubscriptionIds.stream().distinct()
                    .filter(resourceSubscriptionIds::contains)
                    .collect(Collectors.toSet());
            removeForResourceAndSession(sessionSubscriptionsSetId, resourceSubscriptionsSetId, intersect);
        }
    }

    private void removeForResourceAndSession(final String sessionSubscriptionsSetId, final String resourceSubscriptionsSetId,
            final Set<String> resourceIds) {

        // remove all subscriptions by intersected IDS
        if (resourceIds.isEmpty()) {
            return;
        }
        redisClient.del(resourceIds.stream().toList());
        redisClient.srem(Stream.concat(Stream.of(sessionSubscriptionsSetId), resourceIds.stream()).toList());
        redisClient.srem(Stream.concat(Stream.of(resourceSubscriptionsSetId), resourceIds.stream()).toList());
    }

    @Override
    public int size() {
        return scanForSize(Utils.SUB_TEMPLATE);
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

    private void removeFromResourceIndexes(final List<String> subscriptionIds) {
        // Group operations by resource set key to batch them
        Map<String, List<String>> removalsByResourceSet = subscriptionIds.stream()
                .filter(Utils::isValidSubscriptionId)
                .collect(Collectors.groupingBy(Utils::getResourceSetKey));

        // Execute batched removals
        for (Map.Entry<String, List<String>> entry : removalsByResourceSet.entrySet()) {
            String resourceSetKey = entry.getKey();
            List<String> subscriptionsToRemove = entry.getValue();

            List<String> sremArgs = new ArrayList<>(subscriptionsToRemove.size() + 1);
            sremArgs.add(resourceSetKey);
            sremArgs.addAll(subscriptionsToRemove);

            redisClient.srem(sremArgs);
        }

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

    private HashMap<String, List<String>> scanByResourceType(String resourceType) {
        var scanCursor = "0";
        var subscriptionIds = new HashMap<String, List<String>>();
        do {
            final var subscriptionScanResult = redisClient.scan(
                    List.of(scanCursor, "match", String.format(RESOURCE_SUB_TEMPLATE, resourceType) + "|*"));
            scanCursor = subscriptionScanResult.get(0).toString();

            subscriptionScanResult.get(1).stream().map(Response::toString)
                    .forEach(subscriptionPointerSet -> redisClient.smembers(subscriptionPointerSet).stream()
                            .map(res -> List.of(res.toString()))
                            .forEach(matching -> subscriptionIds.computeIfAbsent(subscriptionPointerSet,
                                    k -> new ArrayList<>()).addAll(matching)));
        } while (!scanCursor.equals("0"));

        return subscriptionIds;
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
            log.debug("Could not deserialize subscription json {}", json, e);
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
