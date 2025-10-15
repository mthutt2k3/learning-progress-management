package com.learning.progress.common;

public class Const {
    public static class VALIDATE_INPUT {
        public static final String regexEmail = "^(?=.{1,64}@)[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*@"
                + "[^-][A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$";
        public static final String regexPhone = "^(?:0|\\+84)(?:\\s?\\d){9,10}$";
        public static final String regexPass = "^[A-Za-z0-9]{4,20}$";
        public static final String regexGender = "MALE|FEMALE|OTHER";
        public static final String dateOfbirth = "yyyy-MM-dd";
    }

    public static class CRUD_MESSAGE_CODE {
        public static final String CREATE_SUCCESSFUL = "Created successful";
        public static final String UPDATE_SUCCESSFUL = "Updated successful";
        public static final String DELETE_SUCCESSFUL = "Deleted successful";
        public static final String REMOVE_SUCCESSFUL = "Removed successful";
        public static final String ACTIVATE_SUCCESSFUL = "Activated successful";
        public static final String DEACTIVATE_SUCCESSFUL = "Deactivated successful";
        public static final String RETRIEVE_SUCCESSFUL = "Retrieved successful";
        public static final String IMPORT_SUCCESSFUL = "Imported successful";
        public static final String REQUEST_SENT = "Request sent successful";
    }
    public static class PAGINATION {
        public static final String PAGE_NEGATIVE = "Page must be >= 0";
        public static final String SIZE_NEGATIVE_OR_ZERO = "Size must be > 0";
        public static final String SIZE_EXCEEDS_MAX = "Size must be <= %d"; // String.format
    }

    public static class SORT {
        public static final String INVALID_SORT_BY = "Invalid sortBy: %s";
        public static final String INVALID_SORT_DIR = "Invalid sortDir: must be asc or desc";
    }


    public static class PATH {
        public static final String REQUIRED = "Path is required";
    }
    public static class DOMAIN {
        public static final String REQUIRED = "Domain is required";
    }
    public static class TOKEN {
        public static final String REFRESH_TOKEN_REQUIRED = "Refresh token is required";

        public static final String REQUIRED = "Token is required";
        public static final String EXPIRED = "Token has expired";

    }
    public static class USERNAME {
        public static final String REQUIRED = "User Name is required";
    }
    public static class PASSWORD {
        public static final String REQUIRED = "Password is required";
        public static final String OLD_PASSWORD_REQUIRED = "Old password is required";
        public static final String NEW_PASSWORD_REQUIRED = "New password is required";
        public static final String CONFIRM_PASSWORD_REQUIRED = "Confirm password is required";
        public static final String INVALID_PASSWORD_FORMAT = "Invalid password format. Must be 4-20 characters, containing only letters and digits";


    }
    public static class GENDER {
        public static final String REQUIRED = "Gender is required";
    }
    public static class PHONE_NUMBER {
        public static final String REQUIRED = "Phonenumber is required";
        public static final String INVALID_PHONE_FORMAT = "Invalid phone number format. Must start with 0 or +84 followed by 9 or 10 digits";
    }
    public static class NAME {
        public static final String FIRST_NAME_REQUIRED = "First name is required";
        public static final String LAST_NAME_REQUIRED = "Last name is required";
        public static final String ROLE_NAME_REQUIRED = "Role name is required";

    }
    public static class EMAIL {
        public static final String REQUIRED = "Email is required";
        public static final String INVALID = "Invalid email format";


    }
    public static class ROLE {
        public static final String REQUIRED = "Role is required";
        public static final String INVALID_LOGIN_ROLE = "Login role must be either TEACHER or STUDENT";
        public static final String NOT_FOUND = "Role is not found";

    }
    public static class ENUM {
        public static final String INVALID_ENUM_VALUE = "Invalid %s: %s";
    }

    public static class ERROR_MESSAGE {
        public static final String ACCOUNT_NOT_FOUND = "Account is not found";
        public static final String USERNAME_EXISTS = "Username already exists";
        public static final String INVALID_ROLE = "Invalid role: %s";
        public static final String INVALID_PASSWORD_FORMAT = "Invalid password format. Must be 4-20 characters, containing only letters and digits";
        public static final String INVALID_GENDER_FORMAT = "Invalid gender format. Must be MALE, FEMALE, or OTHER";
        public static final String INVALID_PHONE_NUMBER_FORMAT = "Invalid phone number format. Must start with 0 or +84 followed by 9 or 10 digits";
        public static final String PASSWORD_TOO_SHORT = "Password must be at least 6 characters long";
        public static final String INVALID_PAGE = "Page number must be non-negative";
        public static final String INVALID_SIZE = "Size must be between 1 and 100";
        public static final String INVALID_TEXT = "Text search cannot be blank";
        public static final String INVALID_STATUS = "Status list cannot be empty";
        public static final String INVALID_ROLE_NAME = "Role name is not valid";
        public static final String INVALID_SORT_BY = "Invalid sort field";
        public static final String INVALID_SORT_DIR = "Sort direction must be 'asc' or 'desc'";
    }

    public static class AUTH {
        public static final String INVALID_CREDENTIALS = "Invalid credentials";
        public static final String INVALID_OLD_PASSWORD = "Old password is incorrect";
        public static final String PASSWORDS_DO_NOT_MATCH = "New password and confirm password do not match";
        public static final String EMAIL_SEND_FAILED = "Failed to send reset password email. Please try again";
        public static final String NEW_PASSWORD_SAME_AS_OLD = "New password must be different from the old password";
        public static final String ACCESS_TOKEN_BLACKLISTED = "Access token is blacklisted and cannot be used";
        public static final String INVALID_REFRESH_TOKEN = "Invalid or revoked refresh token";
        public static final String INVALID_TOKEN_USERNAME = "Cannot extract username from JWT token. Token may be invalid or missing";



        public static final String LOGIN_SUCCESS = "Login successful";
        public static final String LOGIN_FAILED = "Invalid username or password";
        public static final String LOGOUT_SUCCESS = "Logout successfully";
        public static final String TOKEN_REFRESH_SUCCESS = "Token refreshed successfully";
        public static final String PASSWORD_CHANGED = "Password has been changed successfully";
        public static final String PASSWORD_RESET_EMAIL_SENT = "Password reset email sent successfully";
        public static final String PASSWORD_RESET_BY_TEACHER = "Password has been reset successfully by teacher";
        public static final String USER_NOT_FOUND = "User not found";
        public static final String UNAUTHORIZED = "Unauthorized access";
        public static final String REFRESH_TOKEN_EXPIRED = "Refresh token has expired";
    }

    public static class USER {
        public static final String USER_NOT_FOUND = "User does not exist in the system";
        public static final String USER_INACTIVE = "User account is not active";
        public static final String USERNAME_EXISTS = "Username already exists";


        public static final String USERNAME_NOT_FOUND = "Username does not exist in the system";
        public static final String PROFILE_RETRIEVED = "User info retrieved successfully";
        public static final String PROFILE_NOT_FOUND = "User profile not found";
        public static final String USERNAME_EMPTY = "Username must not be empty";
        public static final String EMAIL_NOT_FOUND = "Email not found in the system";
        public static final String EMAIL_INVALID = "Invalid email format";
        public static final String INVALID_ID = "Invalid user ID";
        public static final String DELETED = "User has been deleted";
        public static final String INVALID_ROLE_STUDENT_ONLY = "User must have student role";
        public static final String INVALID_ROLE_FOR_CLASS = "User must have a student or test taker role to join class";
        public static final String INACTIVE = "User is inactive";
    }
    public static class ACCOUNT {
        public static final String ACCOUNT_NOT_FOUND = "Account does not exist in the system";

    }
    public static class VALIDATION {
        public static final String INVALID_INPUT = "Invalid input data";
        public static final String MISSING_FIELD = "Required field is missing";
        public static final String INVALID_FORMAT = "Invalid format";
        public static final String INVALID_DIFFICULTY = "Invalid difficulty format"; // Thêm mới
        public static final String REQUEST_NULL = "Request body cannot be null";
        public static final String PASSWORD_REQUIRED = "Password is required";
        public static final String LOGIN_ROLE_REQUIRED = "Login role is required";
        public static final String INVALID_LOGIN_ROLE = "Login role must be either TEACHER or STUDENT";
        public static final String EMAIL_REQUIRED = "Email address is required";
        public static final String OLD_PASSWORD_REQUIRED = "Old password is required";
        public static final String NEW_PASSWORD_REQUIRED = "New password is required";
        public static final String CONFIRM_PASSWORD_REQUIRED = "Confirm password is required";
        public static final String PASSWORDS_DO_NOT_MATCH = "New password and confirm password do not match";
        public static final String REFRESH_TOKEN_REQUIRED = "Refresh token is required";
        public static final String EMAIL_SEND_FAILED = "Failed to send reset password email. Please try again";
        public static final String SUBJECT_REQUIRED = "Email subject is required";
        public static final String TEMPLATE_PATH_REQUIRED = "Template path is required";
        public static final String TEMPLATE_VARIABLES_NULL = "Template variables cannot be null";
    }

    public static class SECURITY {
        public static final String FORBIDDEN_ROLE = "You do not have permission to perform this action";
        public static final String OPERATION_FAILED = "Operation failed, please try again later";
        public static final String FORBIDDEN_ROLE_STUDENT_ONLY = "Only students and test takers can have their password reset by teachers";
        public static final String NOT_MATCH_CURRENT_USER = "User ID does not match current user";


        public static final String AUTH_REQUIRED = "Authentication is required";
        public static final String AUTH_BEARER_REQUIRED = "Authorization header with Bearer token is required";
        public static final String ACCESS_TOKEN_REQUIRED = "Access token is required in Authorization header";
    }

    public static class LEVEL {

        public static final String LEVEL_NOT_FOUND = "Level does not exist in the system";

        public static final String LIST_RETRIEVED = "Level list retrieved successfully";
        public static final String DETAILS_RETRIEVED = "Level details retrieved successfully";
        public static final String LEVEL_CREATED = "Level created successfully";
        public static final String LEVEL_UPDATED = "Level updated successfully";
        public static final String STATUS_UPDATED = "Level status updated successfully";
        public static final String DUPLICATE_LEVEL_NAME = "Level name already exists";
        public static final String DUPLICATE_ORDER_NUMBER = "Order number already exists";
        public static final String INVALID_ORDER_SEQUENCE = "Order number have to be in order one by one";
    }

    public static class CLASS {
        public static final String CLASS_NOT_FOUND = "Class not found";
        public static final String INVALID_ID = "Invalid class ID";
        public static final String INACTIVE = "Class is inactive";
        public static final String DELETED = "Class has been deleted";
    }

    public static class CLASS_STUDENT {
        public static final String LIST_RETRIEVED = "Student list retrieved successfully";
        public static final String PROFILE_RETRIEVED = "Student profile retrieved successfully";
        public static final String PERFORMANCE_RETRIEVED = "Student performance report retrieved successfully";
        public static final String PROGRESS_RETRIEVED = "Student progress overview retrieved successfully";
        public static final String STUDENT_ADDED = "Student added to class successfully";
        public static final String STUDENT_REMOVED = "Student removed from class successfully";
        public static final String STUDENTS_IMPORTED = "Students imported successfully";
        public static final String TEMPLATE_GENERATION_FAILED = "Failed to generate import template";
        public static final String IMPORT_FAILED = "Failed to import students from Excel";
        public static final String STUDENT_NOT_FOUND = "Student not found in class";
        public static final String STUDENT_ALREADY_IN_CLASS = "Student is already in the class";
    }


}
