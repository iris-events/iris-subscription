package id.global.iris.subscription.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import id.global.iris.common.annotations.Message;
import id.global.iris.common.annotations.Scope;

@Message(name = "session-closed", scope = Scope.INTERNAL)
public record SessionClosed(@JsonProperty("user_id") String userId,@JsonProperty("session_id") String sessionId) {
}
