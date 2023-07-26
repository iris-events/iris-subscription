package org.iris_events.subscription.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.iris_events.annotations.Message;
import org.iris_events.annotations.Scope;

@Message(name = "session-closed", scope = Scope.INTERNAL)
public record SessionClosed(@JsonProperty("user_id") String userId, @JsonProperty("session_id") String sessionId) {
}
