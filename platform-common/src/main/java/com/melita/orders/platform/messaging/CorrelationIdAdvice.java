package com.melita.orders.platform.messaging;

import com.melita.orders.events.OrderEvents;
import com.melita.orders.platform.web.RequestIds;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;

/** Puts the event's correlation ID into the MDC for the duration of a listener call, so logs line up. */
class CorrelationIdAdvice implements MethodInterceptor {

    static final String MESSAGE_ID_MDC_KEY = "messageId";

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object[] arguments = invocation.getArguments();
        if (arguments.length > 1 && arguments[1] instanceof Message message) {
            Object correlationId = message.getMessageProperties().getHeader(OrderEvents.CORRELATION_HEADER);
            if (correlationId != null) {
                MDC.put(RequestIds.MDC_KEY, correlationId.toString());
            }
            if (message.getMessageProperties().getMessageId() != null) {
                MDC.put(MESSAGE_ID_MDC_KEY, message.getMessageProperties().getMessageId());
            }
        }
        try {
            return invocation.proceed();
        } finally {
            MDC.remove(RequestIds.MDC_KEY);
            MDC.remove(MESSAGE_ID_MDC_KEY);
        }
    }
}
