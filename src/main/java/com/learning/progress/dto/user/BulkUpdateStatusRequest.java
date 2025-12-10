package com.learning.progress.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
import com.learning.progress.common.UserStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BulkUpdateStatusRequest {

    @NotEmpty(message = Const.USER.DELETED)
    private List<Long> userIds;

    @NotNull(message = Const.USER.DELETED)
    private UserStatus targetStatus;
}
