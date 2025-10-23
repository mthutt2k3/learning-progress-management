package com.learning.progress.service.strategy;

import com.learning.progress.common.RoleName;
import com.learning.progress.service.ClassService;

public interface ClassServiceStrategy extends ClassService {
    boolean supports(RoleName role);
}
