package org.iris_events.subscription.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.iris_events.subscription.exception.SubscriptionException;
import org.iris_events.subscription.model.Subscription;

class SubscriptionValidatorTest {
    @ParameterizedTest
    @MethodSource
    void validate(Subscription subscription, Class<Exception> expectedException, String expectedMessage) {

        if (expectedException == null) {

            assertDoesNotThrow(() -> SubscriptionValidator.validate(subscription));
        } else {
            String exceptionMessage = assertThrows(expectedException, () -> {
                SubscriptionValidator.validate(subscription);
            }).getMessage();
            MatcherAssert.assertThat(exceptionMessage, CoreMatchers.containsString(expectedMessage));
        }
    }

    private static Stream<Arguments> validate() {

        String userId = "userId";
        String sessionId = "sessionId";
        String resType = "res-type";
        String invalidResTypeFormat = "resType";
        String resId = "resId";

        return Stream.of(
                Arguments.of(new Subscription(resType, resId, sessionId), null, null),
                Arguments.of(new Subscription(resType, resId, null), SubscriptionException.class,
                        "Subscription must contain sessionId"),
                Arguments.of(new Subscription(null, userId, sessionId), SubscriptionException.class,
                        "Subscription must contain resourceType and resourceId"),
                Arguments.of(new Subscription(resType, null, sessionId), SubscriptionException.class,
                        "Subscription must contain resourceType and resourceId"),
                Arguments.of(new Subscription(invalidResTypeFormat, resId, sessionId), SubscriptionException.class,
                        "Subscription must have resourceType in kebab case pattern"));
    }
}
