package id.global.iris.subscription.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Resource(@JsonProperty("resource_type") String resourceType, @JsonProperty("resource_id") String resourceId) {
}
