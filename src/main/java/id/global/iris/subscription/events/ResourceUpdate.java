package id.global.iris.subscription.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import id.global.iris.common.annotations.Message;
import id.global.iris.common.annotations.Scope;

@Message(name = "resource-update", scope = Scope.SESSION)
public record ResourceUpdate(@JsonProperty("resource_type") String resourceType, @JsonProperty("resource_id") String resourceId, Object payload) {
}
