package com.learning.progress.common;

public class Const {
    public static class VALIDATE_INPUT {
        public static final String regexEmail = "^(?=.{1,64}@)[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*@"
                + "[^-][A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$";
        public static final String regexPhone = "^[0-9]{10}$";
        public static final String regexPass = "^(?=.*?[A-Z])(?=.*?[a-z])(?=.*?[0-9]).{6,255}$";
        public static final String regexDate = "^\\d{4}\\-(0[1-9]|1[012])\\-(0[1-9]|[12][0-9]|3[01])$";
        public static final String regexImageFile = "([^\\s]+(\\.(?i)(jpg|png|gif|bmp))$)";
    }

    public static class MESSAGE_CODE {
        public static final String OK = "OK";
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
    }

    // ✅ Permission & role related messages
    public static class SECURITY {
        public static final String ACCESS_DENIED = "Access denied";
        public static final String FORBIDDEN_ROLE = "You do not have permission to perform this action";
        public static final String AUTH_REQUIRED = "Authentication is required";
    }
}
