package com.learning.progress.common;

public class Const {
    public static class VALIDATE_INPUT {
        public static final String regexEmail = "^(?=.{1,64}@)[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*@"
                + "[^-][A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$";
        public static final String regexPhone = "^(?:0|\\+84)(?:\\s?\\d){9,10}$";
        public static final String regexPass = "^[A-Za-z0-9]{4,20}$";

        public static final String regexGender = "MALE|FEMALE|OTHER";
    }

    public static class CRUD_MESSAGE_CODE {
        public static final String CREATE_SUCCESSFUL = "CREATE_SUCCESSFUL";
        public static final String UPDATE_SUCCESSFUL = "UPDATE_SUCCESSFUL";
        public static final String DELETE_SUCCESSFUL = "DELETE_SUCCESSFUL";
        public static final String REMOVE_SUCCESSFUL = "REMOVE_SUCCESSFUL";
        public static final String ACTIVATE_SUCCESSFUL = "ACTIVATE_SUCCESSFUL";
        public static final String DEACTIVATE_SUCCESSFUL = "DEACTIVATE_SUCCESSFUL";
    }

    public static class ERROR_MESSAGE {
        public static final String ACCOUNT_NOT_FOUND = "Account is not found";
        public static final String USERNAME_EXISTS = "Username already exists";
        public static final String INVALID_ROLE = "Invalid role: %s";
        public static final String INVALID_PASSWORD_FORMAT = "Invalid password format. Must be 4-20 characters, containing only letters and digits";
        public static final String INVALID_GENDER_FORMAT = "Invalid gender format. Must be MALE, FEMALE, or OTHER";
        public static final String INVALID_PHONE_NUMBER_FORMAT = "Invalid phone number format. Must start with 0 or +84 followed by 9 or 10 digits";
        public static final String PASSWORD_TOO_SHORT = "Password must be at least 6 characters long";
    }

    public static class AUTH {
        public static final String LOGIN_SUCCESS = "Login successful";
        public static final String LOGIN_FAILED = "Invalid username or password";
        public static final String LOGOUT_SUCCESS = "Logout successfully";
        public static final String TOKEN_REFRESH_SUCCESS = "Token refreshed successfully";

        public static final String PASSWORD_CHANGED = "Password has been changed successfully";
        public static final String PASSWORD_RESET_EMAIL_SENT = "Password reset email sent successfully";
        public static final String PASSWORD_RESET_BY_TEACHER = "Password has been reset successfully by teacher";

        public static final String INVALID_CREDENTIALS = "Invalid credentials";
        public static final String USER_NOT_FOUND = "User not found";
        public static final String UNAUTHORIZED = "Unauthorized access";
        // ✅ NEW: More detailed auth messages
        public static final String INVALID_OLD_PASSWORD = "Old password is incorrect";
        public static final String INVALID_REFRESH_TOKEN = "Invalid or revoked refresh token";
        public static final String REFRESH_TOKEN_EXPIRED = "Refresh token has expired";
        public static final String ACCESS_TOKEN_BLACKLISTED = "Access token is blacklisted and cannot be used";
        public static final String INVALID_TOKEN_USERNAME = "Cannot extract username from JWT token. Token may be invalid or missing";
    }

    // ✅ User feature messages
    public static class USER {
        public static final String PROFILE_RETRIEVED = "User info retrieved successfully";
        public static final String PROFILE_NOT_FOUND = "User profile not found";

        public static final String USERNAME_EMPTY = "Username must not be empty";
        public static final String USERNAME_NOT_FOUND = "Username does not exist in the system";
        public static final String USER_INACTIVE = "User account is not active";

        public static final String EMAIL_NOT_FOUND = "Email not found in the system";
        public static final String EMAIL_INVALID = "Invalid email format";
    }

    // ✅ Validation / common error messages
    public static class VALIDATION {
        public static final String INVALID_INPUT = "Invalid input data";
        public static final String MISSING_FIELD = "Required field is missing";
        public static final String INVALID_FORMAT = "Invalid format";
        public static final String OPERATION_FAILED = "Operation failed, please try again later";
        // ✅ NEW: More detailed validation messages
        public static final String REQUEST_NULL = "Request body cannot be null";
        public static final String PASSWORD_REQUIRED = "Password is required";
        public static final String LOGIN_ROLE_REQUIRED = "Login role is required";
        public static final String INVALID_LOGIN_ROLE = "Login role must be either TEACHER or STUDENT";
        public static final String EMAIL_REQUIRED = "Email address is required";
        public static final String OLD_PASSWORD_REQUIRED = "Old password is required";
        public static final String NEW_PASSWORD_REQUIRED = "New password is required";
        public static final String CONFIRM_PASSWORD_REQUIRED = "Confirm password is required";
        public static final String PASSWORDS_DO_NOT_MATCH = "New password and confirm password do not match";
        public static final String NEW_PASSWORD_SAME_AS_OLD = "New password must be different from the old password";
        public static final String REFRESH_TOKEN_REQUIRED = "Refresh token is required";
        public static final String EMAIL_SEND_FAILED = "Failed to send reset password email. Please try again";
    }

    // ✅ Permission & role related messages
    public static class SECURITY {
        public static final String ACCESS_DENIED = "Access denied";
        public static final String FORBIDDEN_ROLE = "You do not have permission to perform this action";
        public static final String AUTH_REQUIRED = "Authentication is required";
        // ✅ NEW: More detailed security messages
        public static final String FORBIDDEN_ROLE_STUDENT_ONLY = "Only students and test takers can have their password reset by teachers";
        public static final String AUTH_BEARER_REQUIRED = "Authorization header with Bearer token is required";
        public static final String ACCESS_TOKEN_REQUIRED = "Access token is required in Authorization header";
    }
}