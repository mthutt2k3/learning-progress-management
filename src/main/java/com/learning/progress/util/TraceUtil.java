package com.learning.progress.util;

import com.learning.progress.common.Const;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public class TraceUtil {
    public static String getTraceId() {
        // Ưu tiên lấy từ Request Attribute
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            Object trace = attrs.getAttribute(Const.LOGGING.TRACE_ID, RequestAttributes.SCOPE_REQUEST);
            if (trace != null) return trace.toString();
        }
        // fallback: lấy từ MDC
        return MDC.get(Const.LOGGING.TRACE_ID);
    }
}
