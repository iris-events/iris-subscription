package id.global.iris.subscription.events;

import java.util.List;

import id.global.iris.common.annotations.Message;
import id.global.iris.common.annotations.Scope;
import id.global.iris.subscription.model.Resource;

@Message(name = "subscribe", scope = Scope.FRONTEND)
public record Subscribe(List<Resource> resources) {
}
