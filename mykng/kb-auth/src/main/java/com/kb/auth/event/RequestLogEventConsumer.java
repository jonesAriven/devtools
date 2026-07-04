package com.kb.auth.event;

import com.kb.auth.entity.RequestLog;
import com.kb.auth.service.RequestLogService;
import com.kb.common.event.AbstractEventConsumer;
import com.kb.common.event.KbEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 请求日志事件消费者
 * <p>
 * 消费 Redis Stream 中的请求日志事件，统一存储到 sys_request_log 表。
 * 所有服务的请求日志都通过事件方式发送到这里集中存储。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLogEventConsumer extends AbstractEventConsumer {

    private final RequestLogService requestLogService;

    @Override
    public String getStream() {
        return KbEvent.STREAM_REQUEST_LOGS;
    }

    @Override
    public String getGroup() {
        return KbEvent.GROUP_REQUEST_LOG;
    }

    @Override
    public String getConsumer() {
        return "kb-auth-1";
    }

    @Override
    public void handleEvent(KbEvent event) {
        if (!KbEvent.REQUEST_LOG.equals(event.getEvent())) {
            return;
        }

        Map<String, Object> payload = event.getPayload();
        if (payload == null) {
            return;
        }

        try {
            RequestLog requestLog = new RequestLog();
            requestLog.setTraceId(getString(payload, "traceId"));
            requestLog.setUserId(getLong(payload, "userId"));
            requestLog.setUsername(getString(payload, "username"));
            requestLog.setHttpMethod(getString(payload, "httpMethod"));
            requestLog.setRequestUri(getString(payload, "requestUri"));
            requestLog.setControllerMethod(getString(payload, "controllerMethod"));
            requestLog.setRequestArgs(getString(payload, "requestArgs"));
            requestLog.setResponseResult(getString(payload, "responseResult"));
            requestLog.setCostMs(getLong(payload, "costMs"));
            requestLog.setStatus(getString(payload, "status"));
            requestLog.setException(getString(payload, "exception"));
            requestLog.setIp(getString(payload, "ip"));
            requestLog.setUserAgent(getString(payload, "userAgent"));
            requestLog.setServiceName(getString(payload, "serviceName"));

            requestLogService.log(requestLog);
        } catch (Exception e) {
            log.error("处理请求日志事件失败 eventId={}", event.getEventId(), e);
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
