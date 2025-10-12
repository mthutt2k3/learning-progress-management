package com.learning.progress.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learning.progress.common.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
//@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateAccountResponse {
    private Long id;
    private String userName;
    private UserStatus status;
}
