package com.kb.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回格式（P0 优化版）
 * <p>
 * 所有服务统一使用此格式返回，前端通过 code 判断状态。
 * traceId 用于跨服务链路追踪。
 */
@Data
public class Result<T> implements Serializable {

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
        return r;
    }

    public static <T> Result<T> fail(String message) {
        return fail(500, message);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public Result<T> withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}
