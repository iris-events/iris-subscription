package org.iris_events.subscription.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Subscription(@JsonProperty("resource_type") String resourceType, @JsonProperty("resource_id") String resourceId,
        String sessionId) {

    public String id() {
        return resourceType + "-" + resourceId;
    }

}
