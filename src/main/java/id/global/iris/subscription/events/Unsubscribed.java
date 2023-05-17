package id.global.iris.subscription.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import id.global.iris.common.annotations.Message;
import id.global.iris.common.annotations.Scope;

@Message(name = "unsubscribed", scope = Scope.SESSION)
public record Unsubscribed(@JsonProperty("resource_type") String resourceType, @JsonProperty("resource_id") String resourceId) {
}
