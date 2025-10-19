package com.learning.progress.common;

public class Const {
    public static class VALIDATE_INPUT {
        public static final String regexEmail =
                "^(?=.{6,254}$)(?=.{1,64}@)[A-Za-z0-9._%+-]+@" +
                        "[^-][A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$";
        public static final String regexPhone = "^(?:0|\\+84)(?:\\s?\\d){9,10}$";
        public static final String regexPass = "^[A-Za-z0-9]{6,20}$";
        public static final String regexGender = "MALE|FEMALE|OTHER";
        public static final String dateOfbirth = "yyyy-MM-dd";
        public static final String regexDomain =
                "^(?!-)[A-Za-z0-9.-]+(?<!-)$";
        public static final String regexPath =
                "^\\/.*$";
    }
    public static class STUDENT {
        public static final String LEVEL_ID_REQUIRED = "Level is required for student";
        public static final String PARENT_NAME_REQUIRED = "Parent name is required";
        public static final String PARENT_PHONE_REQUIRED = "Parent phone number is required";

        public static final String INVALID_ROLE_UPDATE = "Role update from STUDENT to TEST_TAKER is not allowed.";

    }
    public static class CHAPTER {
        public static final String ID_REQUIRED = "ID bắt buộc khi xóa";
        public static final String CHAPTER_NAME_REQUIRED = "Tên chapter không được để trống";
        public static final String CHAPTER_NAME_MAX_LENGTH = "Tên chapter không được vượt quá 100 ký tự";
        public static final int CHAPTER_NAME_MAX_LENGTH_VALUE = 100;
    }

    public static class LESSON {
        public static final String ID_REQUIRED = "ID bắt buộc khi xóa";
        public static final String LESSON_NAME_REQUIRED = "Tên lesson không được để trống";
        public static final String LESSON_NAME_MAX_LENGTH = "Tên lesson không được vượt quá 200 ký tự";
        public static final int LESSON_NAME_MAX_LENGTH_VALUE = 200;

    }

    public static class CLASS_LESSON {
        public static final String NOT_FOUND = "Class lesson does not exist in the system or has been deleted";

    }
    public static class CHALLENGE {
        public static final String NAME_REQUIRED = "Challenge name is required";
        public static final String CLASS_LESSON_REQUIRED = "Class lesson is required";
    }
    public static class DOB {
        public static final String DOB_IN_FUTURE = "Date of birth cannot be in the future.";
        public static final String DOB_UNDER_AGE = "User must be at least %d years old.";
        public static final String DOB_OVER_AGE = "Tuổi không được lớn hơn %d";
    }
    public static class FILE {
        public static final String AVATAR_REQUIRED = "Avatar file is required.";
        public static final String AVATAR_UPLOAD_FAILED = "Failed to upload avatar.";
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
        public static final String LENGTH_INVALID = "Username must be between 3 and 30 characters";
        public static final int MAX_LENGTH_VALUE = 3;
        public static final int MIN_LENGTH_VALUE = 30;

    }

    public static class PASSWORD {
        public static final String REQUIRED = "Password is required";
        public static final String OLD_PASSWORD_REQUIRED = "Old password is required";
        public static final String NEW_PASSWORD_REQUIRED = "New password is required";
        public static final String CONFIRM_PASSWORD_REQUIRED = "Confirm password is required";
        public static final String INVALID_PASSWORD_FORMAT = "Invalid password format. Must be 6-20 characters, containing only letters and digits";
    }
    public static class GENDER {
        public static final String REQUIRED = "Gender is required";
    }
    public static class PHONE_NUMBER {
        public static final String REQUIRED = "Phonenumber is required";
        public static final String INVALID_PHONE_FORMAT = "Invalid phone number format. Must start with 0 or +84 followed by 9 or 10 digits";
    }
    public static class NAME {
        public static final String FULL_NAME_REQUIRED = "Full name is required";
        public static final String ROLE_NAME_REQUIRED = "Role name is required";

    }
    public static class EMAIL {
        public static final String REQUIRED = "Email is required";
        public static final String INVALID =
                "Invalid email format. Email must follow the standard structure (e.g., user@example.com), "
                        + "contain a valid local part and domain, and not exceed 254 characters.";
    }

    public static class ID {
        public static final String USER_ID_REQUIRED = "UserId is required";

    }

    public static class ROLE {
        public static final String LOGIN_ROLE_VALUE = "TEACHER|STUDENT";

        public static final String REQUIRED = "Role is required";
        public static final String INVALID_LOGIN_ROLE = "Login role must be either TEACHER or STUDENT";
        public static final String NOT_FOUND = "Role is not found";
        public static final String ROLE_NAME_MAX_LENGTH = "Role name must not exceed 50 characters";
        public static final int ROLE_NAME_MAX_LENGTH_VALUE = 50;
        public static final String ROLE_NAME_REQUIRED = "Role name is required";

    }
    public static class ENUM {
        public static final String INVALID_ENUM_VALUE = "Invalid %s: %s";
    }
    public static class PAGINATION {
        public static final String PAGE_NEGATIVE = "Page must be >= 0";
        public static final String SIZE_NEGATIVE_OR_ZERO = "Size must be > 0";
        public static final String SIZE_EXCEEDS_MAX = "Size must be <= %d"; // String.format
    }

    public static class SORT {
        public static final String INVALID_SORT_BY = "Invalid sort field: %s. Valid fields are: %s";
        public static final String INVALID_SORT_DIR = "Invalid sort direction: %s. Valid values are: asc, desc";
    }

    public static class RESULT_MESSAGE_CODE {
        public static final String CREATE_SUCCESSFUL = "Created successful";
        public static final String UPDATE_SUCCESSFUL = "Updated successful";
        public static final String DELETE_SUCCESSFUL = "Deleted successful";
        public static final String REMOVE_SUCCESSFUL = "Removed successful";
        public static final String ACTIVATE_SUCCESSFUL = "Activated successful";
        public static final String DEACTIVATE_SUCCESSFUL = "Deactivated successful";
        public static final String RETRIEVE_SUCCESSFUL = "Retrieved successful";
        public static final String IMPORT_SUCCESSFUL = "Imported successful";
        public static final String REQUEST_SENT = "Request sent successful";
        public static final String LOGIN_SUCCESS = "Login successful";
        public static final String LOGOUT_SUCCESS = "Logout successfully";
        public static final String TOKEN_REFRESH_SUCCESS = "Token refreshed successfully";
        public static final String PASSWORD_RESET_EMAIL_SENT = "Password reset email sent successfully";
        public static final String PASSWORD_RESET_BY_TEACHER = "Password has been reset successfully by teacher";
        public static final String REFRESH_TOKEN_EXPIRED = "Refresh token has expired";

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
        public static final String HAVE_CHANGED_PASSWORD = "Your account have changed password";
    }

    public static class USER {
        public static final String USER_NOT_FOUND = "User does not exist in the system or has been deleted";
        public static final String USER_INACTIVE = "User account is not active";
        public static final String USERNAME_EXISTS = "Username already exists";


        public static final String DELETED = "User has been deleted";
        public static final String INVALID_ROLE_STUDENT_ONLY = "User must have student role";
        public static final String INVALID_ROLE_FOR_CLASS = "User must have a student or test taker role to join class";
        public static final String INVALID_ROLE_TEACHER_ONLY = "User must have teacher or teaching assistant role";
        public static final String INACTIVE = "User is inactive";
    }
    public static class ACCOUNT {
        public static final String ACCOUNT_NOT_FOUND = "Account does not exist in the system";
        public static final String FORBIDDEN_EMAIL_CHANGE_ACTIVE_USER =
                "You don't have permission to change email of an active account.";
        public static final String CANNOT_CHANGE_STATUS_TO_PENDING =
                "Cannot change account status to PENDING";
        public static final String CANNOT_CHANGE_STATUS_TO_ACTIVE_MANUALLY =
                "Cannot change account status to ACTIVE manually";


    }
    public static class VALIDATION {
        public static final String MISSING_FIELD = "Required field is missing";
        public static final String INVALID_FORMAT = "Invalid format";
        public static final String INVALID_DIFFICULTY = "Invalid difficulty format"; // Thêm mới
        public static final String REQUEST_NULL = "Request body cannot be null";
        public static final String EMAIL_REQUIRED = "Email address is required";
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
        public static final String AUTH_BEARER_REQUIRED = "Authorization header with Bearer token is required";
        public static final String ACCESS_TOKEN_REQUIRED = "Access token is required in Authorization header";
    }

    public static class LEVEL {

        public static final String LEVEL_NOT_FOUND = "Level does not exist in the system";
        public static final String DUPLICATE_LEVEL_NAME = "Level name already exists";
        public static final String LEVEL_BULK_UPDATE_FORBIDDEN = "Cannot bulk update levels because one or more levels are already PUBLISHED";
        public static final String NOT_PUBLISHED = "Level is not published";
        public static final String ID_REQUIRED_WHEN_DELETING = "ID is required when deleting";
        public static final String LEVEL_NAME_REQUIRED = "Level name is required";
        public static final String ESTIMATED_DURATION_REQUIRED = "Estimated duration is required";
        public static final String DURATION_NON_NEGATIVE = "Duration must be non-negative";
        public static final String DESCRIPTION_MAX_LENGTH = "Description cannot exceed 1000 characters";
        public static final String PROMOTION_CRITERIA_MAX_LENGTH = "Promotion criteria cannot exceed 1000 characters";
        public static final String LEARNING_OBJECTIVES_MAX_LENGTH = "Learning objectives cannot exceed 1000 characters";
        public static final int DESCRIPTION_MAX_LENGTH_VALUE = 1000;
        public static final int PROMOTION_CRITERIA_MAX_LENGTH_VALUE = 1000;
        public static final int LEARNING_OBJECTIVES_MAX_LENGTH_VALUE = 1000;


        public static final String LIST_RETRIEVED = "Level list retrieved successfully";
        public static final String DETAILS_RETRIEVED = "Level details retrieved successfully";
        public static final String LEVEL_UPDATED = "Level updated successfully";

    }

    public static class ORDER_NUMBER {
        public static final String ORDER_NUMBER_REQUIRED = "Order number is required";
        public static final String ORDER_NUMBER_MIN = "Order number must be 1 or greater";
    }

    public static class CLASS {
        public static final String CLASS_NOT_FOUND = "Class not found";
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
        public static final String STUDENT_NOT_FOUND = "Student not found in class";
        public static final String STUDENT_ALREADY_IN_CLASS = "Student is already in the class";
    }

    public static class CLASS_TEACHER {
        public static final String TEACHER_ADDED = "Teacher added to class successfully";
        public static final String TEACHER_REMOVED = "Teacher removed from class successfully";
        public static final String LIST_RETRIEVED = "Teacher list retrieved successfully";
        public static final String PERFORMANCE_RETRIEVED = "Teacher performance report retrieved successfully";
        public static final String TEACHER_NOT_FOUND = "Teacher not found in class";
        public static final String TEACHER_ALREADY_IN_CLASS = "Teacher is already in the class";
    }
}
