package com.kb.common.result;

import lombok.Data;
import org.slf4j.MDC;

import java.io.Serializable;

@Data
public class Result<T> implements Serializable {

    private static final String TRACE_ID_KEY = "traceId";

    private int code;
    private String message;
    private T data;
    private String traceId;

    private Result() {}

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        r.fillTraceId();
        return r;
    }

    public static <T> Result<T> fail(String message) {
        return fail(500, message);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        r.fillTraceId();
        return r;
    }

    public Result<T> withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    private void fillTraceId() {
        if (this.traceId == null) {
            this.traceId = MDC.get(TRACE_ID_KEY);
        }
    }
}
