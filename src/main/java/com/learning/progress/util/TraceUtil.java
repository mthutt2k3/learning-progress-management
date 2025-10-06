package com.learning.progress.util;

import org.slf4j.MDC;

public class TraceUtil {
    public static String getTraceId() {
        return MDC.get("traceId");
    }
}
