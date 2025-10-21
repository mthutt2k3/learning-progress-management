package com.learning.progress.util;

import com.learning.progress.common.Const;
import org.slf4j.MDC;

public class TraceUtil {
    public static String getTraceId() {
        return MDC.get(Const.LOGGING.TRACE_ID);
    }
}
