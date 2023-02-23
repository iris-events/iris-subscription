package id.global.iris.subscription.events;

import id.global.iris.common.annotations.Message;
import id.global.iris.common.annotations.Scope;

@Message(name = "session-closed", scope = Scope.INTERNAL)
public record SessionClosed(String userId, String sessionId) {
}
