package com.learning.progress.common;

import java.util.regex.Pattern;

public class Const {
    public static class SUBMISSION_EVENT {
        public static final String SESSION_START = "SESSION_START";
        public static final String DEVICE_MISMATCH = "DEVICE_MISMATCH";
    }
    public static class SSE {
        public static final String EVENT_CONNECT = "connect";
        public static final String EVENT_NOTIFICATION = "notification";
        public static final String EVENT_PING = "ping";
        public static final String EVENT_DEVICE_MISMATCH = "device_mismatch";
    }
    public static class VALIDATE_INPUT {
        public static final String regexEmail =
                "^(?=.{6,254}$)(?=.{1,64}@)[A-Za-z0-9._%+-]+@" +
                        "[^-][A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$";
        public static final String regexPhone = "^(?:0|\\+84)(?:\\s?\\d){9,10}$";
        public static final String regexPass = "^[A-Za-z0-9]{6,20}$";
        public static final String regexGender = "MALE|FEMALE|OTHER";
        public static final String regexDate = "yyyy-MM-dd";
        public static final String regexValidateRearrange = "^\\s*(\\[\\[pos_[A-Za-z0-9]{1,10}]]\\s*)+$";
    }

    public static class QUESTION {
        public static final String NULL_OBJECT = "Question DTO cannot be null";
        public static final String EMPTY_TEXT = "Question text cannot be empty";
        public static final String INVALID_SCORE = "Weight must be positive";
        public static final String EMPTY_CONTENT = "Question content cannot be empty";
        public static final String NO_CORRECT_ANSWER = "Question must have at least one correct answer";
        public static final String MISSING_CONTENT_DATA = "Missing content data for placeholders";
        public static final String MISSING_POSITION_ID = "Missing positionId '%s' in content.data";

        public static final String ID_REQUIRED = "Question ID is required";
        public static final String TYPE_REQUIRED = "Question type is required";
        public static final String CONTENT_REQUIRED = "Question content is required";

        public static final Pattern POSITION_PATTERN = Pattern.compile("\\[\\[(.*?)]]");
        public static final String NOT_FOUND = "Question does not exist or has been deleted";
        public static final String IDS_REQUIRED = "Question IDs are required";

        // New/question-specific templates
        public static final String ID_MUST_BE_NULL_INSERT = "Question ID must be null for insert in section %d";
        public static final String VALIDATION_FAILED = "Validation failed: %s";
        public static final String SECTIONS_NOT_FOUND = "Sections not found: %s";
        public static final String MAX_QUESTIONS_EXCEEDED = "Challenge cannot have more than %d questions. Current: %d, Adding: %d, Total: %d";
        public static final String CHALLENGE_PUBLISHED_FORBID_MODIFY = "Challenge is PUBLISHED. Cannot add or delete questions.";
        public static final String QUESTION_NOT_FOUND_WITH_ID = "Question not found: %d";
        public static final String INVALID_QUESTION_IDS = "Invalid question IDs: %s";
        public static final String UNHANDLED_QUESTIONS = "Questions not handled: %s";
        public static final String NON_DELETED_QUESTION_COUNT_MISMATCH = "Non-deleted questions count mismatch! Expected: %d, Actual: %d";
        public static final String ORDER_NUMBER_SEQUENCE_INVALID = "Order numbers must be sequential from 1 to %d. Found: %s";

        // Validator messages
        public static final String DUPLICATE_DATA_ITEM_ID = "Duplicate data item id '%s' found in question %s";
        public static final String NO_PLACEHOLDERS_FOUND = "No placeholders found in question text";
        public static final String NO_DATA_ITEMS_FOR_PLACEHOLDER = "No data items found for placeholder %s";
        public static final String MULTIPLE_CHOICE_ONE_CORRECT = "MULTIPLE_CHOICE must have exactly one correct answer";
        public static final String MULTIPLE_CHOICE_MIN_OPTIONS = "MULTIPLE_CHOICE must have at least 2 options";
        public static final String MULTIPLE_SELECT_MIN_CORRECT = "MULTIPLE_SELECT must have at least one correct answer";
        public static final String MULTIPLE_SELECT_MIN_OPTIONS = "MULTIPLE_SELECT must have at least 2 options";
        public static final String TRUE_FALSE_OPTIONS_REQUIRED = "TRUE_OR_FALSE must have 'True' and 'False' options";
        public static final String TRUE_FALSE_TWO_OPTIONS = "TRUE_OR_FALSE must have exactly 2 options";
        public static final String TRUE_FALSE_ONE_CORRECT = "TRUE_OR_FALSE must have exactly one correct answer";

        public static final String REARRANGE_PLACEHOLDER_REQUIRED = "REARRANGE must have at least one placeholder";
        public static final String REARRANGE_MUST_ONE_PER_PLACEHOLDER = "REARRANGE must have exactly one data item per placeholder and it must be correct";
        public static final String REARRANGE_PLACEHOLDER_FORMAT = "REARRANGE: Invalid placeholder format '%s'. Must start with 'pos_'";
        public static final String REARRANGE_DUPLICATE_PLACEHOLDER = "REARRANGE: Duplicate placeholder detected: '%s'";
        public static final String REARRANGE_PLACEHOLDER_OCCURRENCE = "REARRANGE: Placeholder '%s' appears %d times in question text (should appear once)";
        public static final String REARRANGE_NO_FALSE_ANSWERS = "REARRANGE data must not contain any false answers";
        public static final String REARRANGE_MISSING_DATA_FOR_PLACEHOLDER = "REARRANGE: Missing data for placeholder: %s";

        public static final String FILL_IN_THE_BLANK_PLACEHOLDER_REQUIRED = "FILL_IN_THE_BLANK must have at least one placeholder";
        public static final String FILL_IN_THE_BLANK_MISSING_POSITION_ID = "FILL_IN_THE_BLANK: Missing positionId for placeholder: %s";

        public static final String DROPDOWN_MIN_OPTIONS_PER_PLACEHOLDER = "DROPDOWN must have at least 2 options per placeholder: %s";
        public static final String DROPDOWN_SINGLE_CORRECT_PER_PLACEHOLDER = "DROPDOWN must have exactly one correct answer per placeholder: %s";

        public static final String DRAG_AND_DROP_PLACEHOLDER_REQUIRED = "DRAG_AND_DROP must have at least one placeholder";
        public static final String DRAG_AND_DROP_MIN_OPTIONS_PER_PLACEHOLDER = "DRAG_AND_DROP must have at least one option per placeholder: %s";
        public static final String DRAG_AND_DROP_SINGLE_CORRECT_PER_PLACEHOLDER = "DRAG_AND_DROP must have exactly one correct answer per placeholder: %s";
        public static final String DRAG_AND_DROP_CORRECT_COUNT_MISMATCH = "DRAG_AND_DROP must have exactly one correct answer per placeholder";

        public static final String REWRITE_MIN_CORRECT = "REWRITE must have at least one correct answer";

        // New constants moved from QuestionValidator hard-coded strings
        public static final String INVALID_QUESTION_TYPE = "Invalid question type: %s";
        public static final String FILL_IN_THE_BLANK_PLACEHOLDER_NEEDS_CORRECT =
                "FILL_IN_THE_BLANK: Placeholder '%s' must have at least one correct answer";
        public static final String DRAG_AND_DROP_OPTIONS_MIN =
                "DRAG_AND_DROP must have at least as many options as placeholders";
        public static final String DRAG_AND_DROP_OPTIONS_MIN_FMT =
                "DRAG_AND_DROP must have at least as many options as placeholders. Options: %d, Placeholders: %d";
        public static final String REARRANGE_PLACEHOLDER_MUST_BE_CORRECT =
                "REARRANGE: Placeholder '%s' must have correct = true";
        public static final String QUESTION_TEXT_PLACEHOLDERS_REQUIRED =
                "Question text cannot be null or blank when validating placeholders";
    }


    public static class SECTION {
        public static final String ID_REQUIRED = "Section ID is required when deleting a section";
        public static final String NOT_FOUND = "Section not found";
        public static final String QUESTIONS_REQUIRED = "At least one question is required";
        public static final String INVALID_CHALLENGE_ID = "Challenge ID in DTO does not match path variable";
        public static final String SECTION_REQUIRED = "Section is required";
        public static final String SECTION_TITLE_REQUIRED = "Section title is required";
        public static final String SECTION_TYPE_REQUIRED = "SectionDto type is required";
        public static final String ORDER_NUMBER_NON_NEGATIVE = "Order number must be non-negative";

        // New messages
        public static final String CANNOT_CREATE_FOR_PUBLISHED = "Cannot create a new section for a published challenge.";
        public static final String CANNOT_DELETE_FOR_PUBLISHED = "Cannot delete sections for a published or higher challenge.";
        public static final String DELETE_ID_NOT_FOUND = "Section ID to delete does not exist: %d";
        public static final String INVALID_SECTION_IDS = "Invalid section IDs: %s";
        public static final String SECTIONS_NOT_HANDLED = "Sections not handled: %s";
        public static final String NON_DELETED_SECTIONS_COUNT_MISMATCH = "Non-deleted sections count mismatch! Expected: %d, Actual: %d";
        public static final String SECTIONS_NOT_FOUND = "Sections not found: %s";
    }
    public static class STUDENT {
        public static final String LEVEL_ID_REQUIRED = "Level is required for student";
        public static final String PARENT_NAME_REQUIRED = "Parent name is required";
        public static final String PARENT_PHONE_REQUIRED = "Parent phone number is required";

        public static final String INVALID_ROLE_STUDENT_UPDATE = "Role update from STUDENT to TEST_TAKER is not allowed.";
        public static final String INVALID_ROLE_TEACHER_UPDATE = "Role update from TEACHER to TEACHING_ASSISTANT is not allowed.";


    }
    public static class CHAPTER {
        public static final String ID_REQUIRED = "ID is required when deleting";
        public static final String NOT_FOUND = "Chapter not found";
        public static final String IDS_NOT_FOUND = "Some chapter IDs do not exist or have been deleted: ";
        public static final String CHAPTER_NAME_REQUIRED = "Chapter name cannot be empty";
        public static final String CHAPTER_NAME_MAX_LENGTH = "Chapter name cannot exceed 100 characters";
        public static final int CHAPTER_NAME_MAX_LENGTH_VALUE = 100;
        public static final String UNHANDLED_CHAPTER = "Unhandled chapters: %s. FE must include ALL active chapters!";
        public static final String CHAPTER_COUNT_MISMATCH = "Number of non-deleted chapters does not match! Expected: %d, Actual: %d";
        public static final String ORDER_NUMBER_SEQUENCE_INVALID =
                "Order numbers must be sequential from 1 to %d, without duplicates or gaps.";

        // New import/file related messages
        public static final String IMPORT_FILE_EMPTY = "Import file is empty";
        public static final String IMPORT_DUPLICATE_NAME_IN_FILE = "Row %d: Chapter Name '%s' is duplicated in the file";
        public static final String IMPORT_NAME_REQUIRED_ROW = "Row %d: Chapter Name is required";
        public static final String IMPORT_NAME_TOO_LONG_ROW = "Row %d: Chapter Name exceeds max length (%d)";
        public static final String IMPORT_ALREADY_EXISTS = "Row %d: Chapter '%s' already exists in syllabus ID %d";
        public static final String IMPORT_TOTAL_EXCEEDS_LIMIT = "Syllabus ID %d will exceed max chapters (%d existing + %d import) limit %d";
        public static final String FILE_INVALID_FORMAT = "File must be Excel (.xlsx or .xls)";
        public static final String FILE_TOO_LARGE = "File size exceeds maximum allowed (%d MB)";

        // New messages for ChapterServiceImpl validations
        public static final String EXISTING_IDS_NOT_FOUND = "Existing chapter IDs do not exist: %s";
        public static final String DUPLICATE_NAME_IN_REQUEST = "Duplicate chapter name in request: %s";
        public static final String DUPLICATE_NAME_CASE_INSENSITIVE = "Chapter name duplicated (case-insensitive): %s";
        public static final String REQUEST_IDS_NOT_EXIST_OR_DELETED = "Chapter IDs do not exist or have been deleted: %s";
        public static final String UNHANDLED_IN_SYNC_REQUEST = "The following chapters are not handled in sync request: %s. FE must include all active chapters!";
        public static final String NON_DELETED_COUNT_MISMATCH = "Number of non-deleted chapters does not match! Expected: %d, Actual: %d";
        public static final String ORDER_NUMBER_SEQUENCE_INVALID_VN = "Order numbers must be sequential from 1 to %d. Current: %s";
    }

    public static class LESSON {
        // New validation constants required by SyncLessonRequest
        public static final String ID_REQUIRED = "Lesson ID is required";
        public static final String LESSON_NAME_REQUIRED = "Lesson name is required";
        public static final String LESSON_NAME_MAX_LENGTH = "Lesson name must not exceed 255 characters";
        public static final int LESSON_NAME_MAX_LENGTH_VALUE = 255;

        // General
        public static final String CHAPTER_NOT_FOUND = "Chapter not found or deleted: %s";
        public static final String LESSON_NOT_FOUND = "Lesson not found";
        public static final String LESSON_ID_NOT_FOUND = "Lesson ID to delete not found: %s";
        public static final String LESSON_IDS_NOT_EXIST = "Lesson IDs do not exist: %s";
        public static final String LESSONS_NOT_HANDLED = "Lessons not handled in request: %s. FE must include ALL active lessons!";
        public static final String NON_DELETED_COUNT_MISMATCH = "Number of non-deleted lessons does not match! Expected: %d, Actual: %d";
        public static final String DUPLICATE_LESSON_NAME_IN_REQUEST = "Duplicate lesson name in request (case-insensitive): %s";
        public static final String LESSON_NAME_EXISTS_IN_CHAPTER = "Lesson name '%s' already exists in this chapter";
        public static final String ORDER_NUMBER_SEQUENCE_INVALID = "Order numbers must be sequential from 1 to %d. Current: %s";
        public static final String FINAL_COUNT_EXCEEDS_LIMIT = "Final lesson count (%d) exceeds configured limit (%d)";

        // Import related
        public static final String IMPORT_FILE_EMPTY = "Import file is empty";
        public static final String IMPORT_CHAPTER_NOT_FOUND = "Chapter not found or deleted with code: %s";
        public static final String IMPORT_DUPLICATE_NAMES_IN_CHAPTER = "In chapter '%s' duplicate lesson names in file: %s";
        public static final String IMPORT_LESSON_NAME_REQUIRED = "Lesson name is required at row: %d";
        public static final String IMPORT_LESSON_NAME_TOO_LONG = "Lesson name exceeds max length (255) at row: %d";
        public static final String IMPORT_CONTENT_TOO_LONG = "Content exceeds max length (1000) at row: %d";
        public static final String IMPORT_ORDER_NUMBER_INVALID = "Order number must be positive at row: %d";
        public static final String IMPORT_ORDER_SEQUENCE_INVALID = "Order numbers must be sequential from 1 to %d for chapter '%s'. Missing: %s";

        // New: Vietnamese/localized validation message for lesson content length
        public static final String LESSON_CONTENT_TOO_LONG_VN = "Nội dung của lesson không được vượt quá 1000 ký tự";
    }
    public static class CHALLENGE {
        // validation / not found
        public static final String LENGTH_INVALID = "Challenge name must be less than 200 characters.";
        public static final int MAX_LENGTH_VALUE = 200;
        public static final String DESCRIPTION_LENGTH_INVALID = "Description must be less than 1000 characters.";
        public static final int MAX_DESCRIPTION_LENGTH_VALUE = 1000;
        public static final String DURATION_NULL_OR_POSITIVE = "Duration must be null or greater than 0";
        public static final String NAME_REQUIRED = "Challenge name is required";
        public static final String CLASS_LESSON_REQUIRED = "Class lesson is required";
        public static final String NOT_FOUND = "Challenge not found or has been deleted";

        // Date related validations
        public static final String START_DATE_REQUIRED = "Start date is required";
        public static final String END_DATE_REQUIRED = "End date is required";
        public static final String INVALID_DATE_RANGE = "End date must be after start date";
        public static final String START_DATE_MUST_BE_FUTURE = "Start date must be in the future";

        // Content validations
        public static final String NO_SECTIONS = "Challenge must have at least one section to publish";
        public static final String NAME_ALREADY_EXISTS_WITH_NAME = "Challenge name already exists for this lesson: %s";

        // New specific/error templates and messages
        public static final String NOT_DRAFT = "Challenge is not in DRAFT status: %s";
        public static final String CANNOT_CHANGE_START_DATE = "Cannot change startDate after challenge has started or been closed";
        public static final String CANNOT_CHANGE_END_DATE = "Cannot change endDate after challenge has been closed";
        public static final String TYPE_REQUIRED = "Challenge type is required";
        public static final String CHALLENGE_INVALID_DATES = "Challenge must have valid start and end date";

        // New validation / message constants used by DailyChallengeServiceImpl & DTOs
        public static final String CHALLENGE_REQUIRED = "Challenge cannot be null";
        public static final String METHOD_REQUIRED = "Challenge method is required";

        // Test-method specific templates (first parameter is context e.g. "Publish" or "Update")
        public static final String TEST_CANNOT_DISABLE_ANTICHEAT = "%s - Test method cannot disable anti-cheat";
        public static final String TEST_CANNOT_ENABLE_TRANSLATE = "%s - Test method cannot enable translate on screen";
        public static final String TEST_DURATION_REQUIRED = "%s - Test method - Duration minutes must be greater than 0";

        // Notification templates
        public static final String NOTIFY_CREATE_TITLE = "Create challenge success";
        public static final String NOTIFY_CREATE_MESSAGE = "You created challenge \"%s\" for the class.";
        public static final String NOTIFY_UPDATE_TITLE = "Update challenge success";
        public static final String NOTIFY_UPDATE_MESSAGE = "You updated challenge \"%s\".";
        public static final String NOTIFY_DELETE_TITLE = "Delete challenge success";
        public static final String NOTIFY_DELETE_MESSAGE = "You deleted challenge \"%s\".";
        public static final String NOTIFY_NEW_CHALLENGE_TITLE = "New challenge: %s";
        public static final String NOTIFY_NEW_CHALLENGE_MESSAGE = "A new challenge has been published in the class.";

        // Export error
        public static final String EXPORT_WORKSHEET_FAILED = "Failed to export worksheet: %s";
    }
    public static class DOB {
        public static final String DOB_IN_FUTURE = "Date of birth cannot be in the future.";
        public static final String DOB_UNDER_AGE = "User must be at least %d years old.";
        public static final String DOB_OVER_AGE = "Age cannot be greater than %d";
    }
    public static class FILE {
        public static final String AVATAR_REQUIRED = "Avatar file is required.";
        public static final String AVATAR_UPLOAD_FAILED = "Failed to upload avatar.";
        public static final String EMPTY= "The file has no data to import.";
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

        // New token related messages
        public static final String REFRESH_TOKEN_GENERATED = "Generated refresh token (masked) for userId=%d";
        public static final String REFRESH_TOKEN_CREATED = "Refresh token record created id=%d userId=%d expiresAt=%s";
        public static final String REFRESH_TOKEN_CREATE_FAILED = "Failed to create refresh token for userId=%d: %s";
        public static final String ACCESS_TOKEN_BLACKLISTED = "Access token blacklisted until %s";
        public static final String ACCESS_TOKEN_ALREADY_BLACKLISTED = "Access token is already blacklisted";
    }
    public static class USERNAME {
        public static final String REQUIRED = "User Name is required";
        public static final String LENGTH_INVALID = "Username must be between 3 and 30 characters";
        public static final int MAX_LENGTH_VALUE = 30;
        public static final int MIN_LENGTH_VALUE = 3;

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
    public static class LOGGING {
        public static final String TRACE_ID = "traceId";
    }

    public static class RESULT_MESSAGE_CODE {
        public static final String SUCCESSFUL = "Successful";
        public static final String CREATE_SUCCESSFUL = "Created successful";
        public static final String UPDATE_SUCCESSFUL = "Updated successful";
        public static final String DELETE_SUCCESSFUL = "Deleted successful";
        public static final String RETRIEVE_SUCCESSFUL = "Retrieved successful";
        public static final String IMPORT_SUCCESSFUL = "Imported successful";
        public static final String REQUEST_SENT = "Request sent successful";
        public static final String LOGIN_SUCCESS = "Login successful";
        public static final String LOGOUT_SUCCESS = "Logout successfully";
        public static final String TOKEN_REFRESH_SUCCESS = "Token refreshed successfully";
        public static final String PASSWORD_RESET_EMAIL_SENT = "Password reset email sent successfully";
        public static final String PASSWORD_RESET_TEACHER_SENT = "Password reset request sent your teacher successfully";
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

    public static class CLASS_LESSON {

        // Not found & invalid
        public static final String NOT_FOUND = "Class lesson not found";
        public static final String INVALID_ID = "Invalid class lesson ID: %s";
        public static final String LESSONS_NOT_FOUND = "Lesson IDs not found: %s";
        public static final String CHAPTER_CODE_NOT_FOUND = "Chapter not found or deleted with code: %s";

        // Validation & logic errors
        public static final String DUPLICATE_NAME_LESSONS = "Duplicate lesson names found in the request: %s";
        public static final String UNHANDLED_LESSONS = "Unhandled lessons: %s. FE must include ALL active lessons!";
        public static final String NON_DELETED_COUNT_MISMATCH = "Mismatch in non-deleted lesson count! Expected: %d, actual: %d";
        public static final String LESSON_NAME_REQUIRED = "Lesson name is required: %s";
        public static final String LESSON_NAME_TOO_LONG = "Lesson name exceeds 255 characters: %s";
        public static final String LESSON_CONTENT_TOO_LONG = "Lesson content exceeds 1000 characters: %s";
        public static final String ORDER_NUMBER_INVALID = "Order number must be positive: %s";
        public static final String ORDER_NUMBER_SEQUENCE_INVALID =
                "Order numbers must be sequential from 1 to %d, without duplicates or gaps. Current: %s";

        // Action / history log
        public static final String ACTION_DELETE =
                "Deleted lesson %s from chapter %s, class %s";
        public static final String ACTION_UPDATE =
                "Updated lesson %s in chapter %s, class %s with order number %d";
        public static final String ACTION_CREATE =
                "Created lesson %s for chapter %s, class %s with order number %d";

        // New: validation / file helper constants for ClassLesson import/validation flows
        public static final String VALIDATION_FILE_ERROR_PREFIX = "❌ ERROR:\n";
        public static final String VALIDATION_FILE_READ_ERROR_PREFIX = "❌ FILE READ ERROR:\n";
        public static final String VALIDATION_ROW_PROCESSING_ERROR = "⚠️ Row processing error: ";
        public static final String VALIDATION_OK = "✓ Valid";

        public static final String VALIDATION_CHAPTER_CODE_REQUIRED = "• Chapter Code is required";
        public static final String VALIDATION_LESSON_NAME_REQUIRED = "• Lesson Name is required";
        public static final String VALIDATION_LESSON_NAME_TOO_LONG = "• Lesson Name exceeds max length (%d)";
        public static final String VALIDATION_DUPLICATE_IN_FILE = "• Duplicate Lesson Name in file: %s";
        public static final String VALIDATION_ALREADY_EXISTS_IN_SYSTEM = "• Lesson Name already exists in this chapter: %s";
        public static final String VALIDATION_CONTENT_TOO_LONG = "• Content exceeds max length (%d)";
        public static final String VALIDATION_ORDER_NUMBER_REQUIRED = "• Order Number is required";
        public static final String VALIDATION_ORDER_NUMBER_POSITIVE = "• Order Number must be positive: %s";
        public static final String VALIDATION_ORDER_NUMBER_DUPLICATE_IN_FILE = "• Duplicate Order Number in file: %s";
        public static final String VALIDATION_ORDER_SEQUENCE_GAP = "• Order Number not sequential from 1 to %d. Missing: %s";
    }

    public static class CLASS_CHAPTER {

        // Not found & invalid
        public static final String NOT_FOUND = "Class chapter not found";
        public static final String ID_NOT_FOUND = "Class chapter ID not found : %d";

        // Invalid existing IDs formatted
        public static final String INVALID_EXISTING_CHAPTER_IDS_FMT = "Existing chapter IDs do not exist: %s";

        // Duplicate name (request) formatted
        public static final String DUPLICATE_NAME_CHAPTER_FMT = "Duplicate chapter name in request: %s";

        // Action / history log
        public static final String ACTION_DELETE =
                "Deleted chapter %s from class %s";
        public static final String ACTION_UPDATE =
                "Updated chapter %s in class %s with order number %d";
        public static final String ACTION_CREATE =
                "Created chapter %s for class %s with order number %d";

        // Import validations / messages
        public static final String IMPORT_TOTAL_EXCEEDS_LIMIT = "Import would exceed max chapters (%d). Existing: %d, Import: %d";
        public static final String IMPORT_ROW_CHAPTER_NAME_REQUIRED = "Row %d, Column 'Chapter Name': cannot be empty";
        public static final String IMPORT_ROW_CHAPTER_NAME_TOO_LONG = "Row %d, Column 'Chapter Name': exceeds max length (%d)";
        public static final String IMPORT_DUPLICATE_NAME_IN_FILE_ROW = "Row %d, Column 'Chapter Name': '%s' is duplicated in the file";
        public static final String IMPORT_ALREADY_EXISTS_IN_CLASS_ROW = "Row %d, Column 'Chapter Name': '%s' already exists in this class (id=%d)";

        // Validation file prefixes / helpers
        public static final String VALIDATION_FILE_ERROR_PREFIX = "❌ ERROR:\n";
        public static final String VALIDATION_FILE_READ_ERROR_PREFIX = "❌ FILE READ ERROR:\n";
        public static final String VALIDATION_CHAPTER_NAME_REQUIRED = "• Chapter Name is required";
        public static final String VALIDATION_CHAPTER_NAME_TOO_LONG = "• Chapter Name exceeds max length (%d)";
        public static final String VALIDATION_DUPLICATE_IN_FILE = "• Duplicate Chapter Name in file: %s";
        public static final String VALIDATION_ALREADY_EXISTS_IN_CLASS = "• Chapter already exists in this class: %s";
        public static final String VALIDATION_OK = "✓ Valid";
        public static final String VALIDATION_ROW_PROCESSING_ERROR = "⚠️ Row processing error: ";

        // New: Vietnamese validation messages reused by DTOs
        public static final String ID_REQUIRED_WHEN_DELETING_VN = "ID bắt buộc khi xóa";
        public static final String CHAPTER_NAME_REQUIRED_VN = "Tên chapter không được để trống";
        public static final String CHAPTER_NAME_MAX_LENGTH_VN = "Tên chapter không được vượt quá 100 ký tự";
        public static final String ORDER_NUMBER_REQUIRED_VN = "Thứ tự không được để trống";
        public static final String ORDER_NUMBER_MIN_VN = "Thứ tự phải từ 1 trở lên";
    }

    public static class USER {
        public static final String NOT_FOUND = "User does not exist in the system or has been deleted";
        public static final String USER_INACTIVE = "User account is not active";
        public static final String USERNAME_EXISTS = "Username already exists";
        public static final String PENDING_STATUS = "User is not in PENDING status";
        public static final String ADMIN_CANNOT_CHANGE_STATUS = "Admin cannot change status of other Admins or themselves";
        public static final String MANAGER_CANNOT_CHANGE_STATUS = "Manager cannot change status of other Manager or themselves";
        public static final String NO_RESET_REQUEST_FROM_STUDENT =
                "Cannot reset password because the student has not requested a password reset.";
        public static final String DELETED = "User has been deleted";
        public static final String INVALID_ROLE_STUDENT_ONLY = "User must have student role";
        public static final String INVALID_ROLE_FOR_CLASS = "User must have a student or test taker role to join class";
        public static final String INVALID_ROLE_TEACHER_ONLY = "User must have teacher or teaching assistant role";
        public static final String INACTIVE = "User is inactive";

        // New:
        public static final String INVALID_STATUS = "Invalid status: %s";
    }
    public static class ACCOUNT {
        public static final String ACCOUNT_NOT_FOUND = "Account does not exist in the system";
        public static final String FORBIDDEN_EMAIL_CHANGE_ACTIVE_USER =
                "You don't have permission to change email of an active account.";
        public static final String CANNOT_CHANGE_STATUS_TO_PENDING =
                "Cannot change account status to PENDING";
        public static final String CANNOT_CHANGE_STATUS_TO_ACTIVE_MANUALLY =
                "Cannot change account status to ACTIVE/INACTIVE manually";
    }

    public static class EMAIL_CHANGE {
        public static final String MANAGER_ONLY_PENDING_TEACHERS =
                "Manager can only change email for TEACHER/TEACHING_ASSISTANT when their status is PENDING";
        public static final String TEACHER_ONLY_STUDENTS =
                "Teacher/Teaching Assistant can only change email for STUDENT/TEST_TAKER";
        public static final String NEW_EMAIL_SAME_AS_CURRENT_WHEN_ACTIVE =
                "New email must not be the same as current email when user status is ACTIVE";
        public static final String CHANGE_LINK_ALREADY_USED = "The email change link has already been used.";
    }

    public static class IMPORT_STUDENT {
        public static final String ROW_EMAIL_REQUIRED = "Row %d: Email is required.";
        public static final String ROW_INVALID_EMAIL = "Row %d: Invalid email format: %s";
        public static final String ROW_FULL_NAME_REQUIRED = "Row %d: Full name is required.";
        public static final String ROW_INVALID_ROLE = "Row %d: Invalid role name: %s";
        public static final String ROW_INVALID_PHONE = "Row %d: Invalid phone number: %s";
        public static final String ROW_INVALID_GENDER = "Row %d: Invalid gender: %s";
        public static final String ROW_INVALID_PARENT_EMAIL = "Row %d: Invalid parent email: %s";
        public static final String ROW_LEVEL_NOT_FOUND = "Row %d: Level not found with code: %s";
    }

    public static class IMPORT_TEACHER {
        public static final String ROW_EMAIL_REQUIRED = "Row %d: Email cannot be empty";
        public static final String ROW_INVALID_EMAIL = "Row %d: Invalid email format: %s";
        public static final String ROW_FULL_NAME_REQUIRED = "Row %d: Full name cannot be empty";
        public static final String ROW_ROLE_NAME_REQUIRED = "Row %d: Role name cannot be empty";
        public static final String ROW_INVALID_ROLE = "Row %d: Invalid role name: %s";
        public static final String ROW_INVALID_PHONE = "Row %d: Invalid phone number: %s";
        public static final String ROW_INVALID_GENDER = "Row %d: Invalid gender: %s";
    }

    public static class VALIDATION {
        public static final String MISSING_FIELD = "Required field is missing";
        public static final String INVALID_FORMAT = "Invalid format";
        public static final String EMAIL_REQUIRED = "Email address is required";
        public static final String REFRESH_TOKEN_REQUIRED = "Refresh token is required";
        public static final String EMAIL_SEND_FAILED = "Failed to send reset password email. Please try again";
        public static final String SUBJECT_REQUIRED = "Email subject is required";
        public static final String TEMPLATE_PATH_REQUIRED = "Template path is required";
        public static final String TEMPLATE_VARIABLES_NULL = "Template variables cannot be null";
    }

    public static class AI {
        public static final String MISSING_FIELD = "Required field is missing";
        public static final String CHALLENGE_ID_REQUIRED = "Challenge ID is required";
        public static final String DESCRIPTION_REQUIRED = "Description is required";
        public static final String AT_LEAST_ONE_QUESTION_TYPE_CONFIG_REQUIRED = "At least one question type configuration is required";
        public static final String QUESTION_TYPE_REQUIRED = "Question type is required";
        public static final String NUMBER_OF_QUESTIONS_REQUIRED = "Number of questions is required";
        public static final String NUMBER_OF_QUESTIONS_MIN = "Number of questions must be at least 1";

        public static final String SECTIONS_REQUIRED = "Sections are required";
        public static final String AT_LEAST_ONE_SECTION_REQUIRED = "At least one section is required";
        public static final String LEVEL_REQUIRED = "Level is required";
        public static final String SECTION_REQUIRED = "Section is required";
        public static final String QUESTION_TYPE_CONFIGS_REQUIRED = "Question type configs are required";

        public static final String QUESTION_TEXT_REQUIRED = "Question text is required";
        public static final String CORRECT_ANSWER_REQUIRED = "Correct answer is required";

        public static final String NUMBER_OF_PARAGRAPHS_REQUIRED = "Number of paragraphs is required";
        public static final String NUMBER_OF_PARAGRAPHS_MIN = "Number of paragraphs must be at least 1";
        public static final String NUMBER_OF_PARAGRAPHS_MAX = "Number of paragraphs cannot exceed 10";
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

        public static final String NOT_FOUND = "Level does not exist in the system";
        public static final String DUPLICATE_LEVEL_NAME = "Level name already exists";
        public static final String LEVEL_BULK_UPDATE_FORBIDDEN = "Cannot bulk update levels because one or more levels are already PUBLISHED";
        public static final String NOT_PUBLISHED = "Level is not published";
        public static final String ID_REQUIRED_WHEN_DELETING = "ID is required when deleting";
        public static final String LEVEL_NAME_REQUIRED = "Level name is required";
        public static final String DESCRIPTION_MAX_LENGTH = "Description cannot exceed 1000 characters";
        public static final String PROMOTION_CRITERIA_MAX_LENGTH = "Promotion criteria cannot exceed 1000 characters";
        public static final String LEARNING_OBJECTIVES_MAX_LENGTH = "Learning objectives cannot exceed 1000 characters";
        public static final int DESCRIPTION_MAX_LENGTH_VALUE = 1000;
        public static final int PROMOTION_CRITERIA_MAX_LENGTH_VALUE = 1000;
        public static final int LEARNING_OBJECTIVES_MAX_LENGTH_VALUE = 1000;


        public static final String LIST_RETRIEVED = "Level list retrieved successfully";
        public static final String DETAILS_RETRIEVED = "Level details retrieved successfully";
        public static final String LEVEL_UPDATED = "Level updated successfully";

        // New messages used by LevelServiceImpl
        public static final String NO_DRAFT_LEVELS_TO_PUBLISH = "No DRAFT levels to publish";
        public static final String NO_PUBLISHED_LEVELS_TO_DRAFT = "No PUBLISHED levels to draft";
        public static final String INVALID_DELETE_ID = "Level ID to delete not found: %s";
        public static final String LEVEL_IDS_NOT_FOUND = "Level IDs not found: %s";
        public static final String LEVELS_NOT_HANDLED = "Levels not handled: %s";
        public static final String NON_DELETED_COUNT_MISMATCH = "Number of non-deleted levels does not match! Expected: %d, Actual: %d";
        public static final String ORDER_NUMBER_SEQUENCE_INVALID = "Order numbers must be sequential from 1 to %d. Current: %s";
        public static final String DUPLICATE_LEVEL_NAMES = "Level name '%s' is duplicated at order numbers %s";
        public static final String VALIDATION_ERROR = "Validation error: %s";
    }

    public static class ORDER_NUMBER {
        public static final String ORDER_NUMBER_REQUIRED = "Order number is required";
        public static final String ORDER_NUMBER_MIN = "Order number must be 1 or greater";
    }

    public static class CLASS {
        public static final String NOT_FOUND = "Class not found or be deleted";
        public static final String INACTIVE = "Class is inactive";
        public static final String DELETED = "Class has been deleted";
        public static final String FINISHED_CLASS = "Class has been finished";

        public static final String EXIST_NAME = "Class name already exists";
        public static final String CLASS_NAME_REQUIRED = "Class name cannot be empty";
        public static final String CLASS_NAME_MAX_LENGTH = "Class name cannot exceed 50 characters";
        public static final int CLASS_NAME_MAX_LENGTH_VALUE = 50;

        public static final String START_DATE_REQUIRED = "Start date cannot be empty";
        public static final String END_DATE_REQUIRED = "End date cannot be empty";
        public static final String END_DATE_INVALID = "End date cannot be earlier than start date";
        public static final String INVALID_DATE_RANGE = "Start date and end date must not be null";

        public static final String INVALID_DATE_FORMAT = "Invalid date format for %s, expected: %s";
        public static final String CANNOT_DELETE_ACTIVE_CLASS = "Cannot delete class %s because its status is %s";

        // Permission / access
        public static final String TEACHER_NOT_ASSIGNED = "Teacher is not assigned to this class";

        public static final String FINISHED_OR_DELETED = "Class has been finished or deleted, cannot modify";

        // Notification templates (used by schedulers / controllers / services)
        public static final String NOTIFY_CLASS_ACTIVATED_TITLE = "Class activated";
        public static final String NOTIFY_CLASS_DEACTIVATED_TITLE = "Class deactivated";
        public static final String NOTIFY_CLASS_STATUS_MESSAGE = "Class %s has been %s by manager";

    }
    public static class SYLLABUS {
        public static final String ID_REQUIRED = "Syllabus ID cannot be empty";
        public static final String NOT_FOUND = "Syllabus not found or deleted";
        public static final String EXIST_NAME = "Syllabus name already exists";
        public static final String IN_USE_BY_ACTIVE_CLASS = "Cannot delete syllabus that is being used by active classes";

        // New messages for SyllabusServiceImpl
        public static final String INVALID_INCLUDE_PARAM = "Invalid include parameter. Use CHAPTERS, LESSONS, or ALL";
        public static final String IMPORT_NAME_REQUIRED = "Syllabus name is required.";
        public static final String IMPORT_NAME_EMPTY = "Syllabus name must not be empty or blank.";
        public static final String IMPORT_NAME_TOO_LONG = "Syllabus name must not exceed 100 characters.";
        public static final String IMPORT_LEVEL_CODE_REQUIRED = "Level code is required";
        public static final String IMPORT_LEVEL_NOT_FOUND = "Level not found with code: %s";
        public static final String IMPORT_SYLLABUS_ALREADY_EXISTS = "Syllabus name already exists: %s";
        public static final String IMPORT_FILE_EMPTY = "Import file is empty";

        // Export related messages
        public static final String EXPORT_NO_SYLLABUSES_FOUND = "No syllabuses found in the system";
        public static final String EXPORT_IDS_CONTAIN_NULL = "IDs list cannot contain null values";
        public static final String EXPORT_DUPLICATE_IDS = "Duplicate IDs found: %s";
        public static final String EXPORT_INVALID_IDS = "Invalid IDs (must be positive): %s";
        public static final String EXPORT_NO_MATCHING_IDS = "No syllabuses found with provided IDs or all are deleted";
        public static final String EXPORT_NOT_FOUND_OR_DELETED_FOR_IDS = "Syllabuses not found or deleted for IDs: %s";

        // new formatted message
        public static final String NOT_FOUND_WITH_ID = "Syllabus ID %d does not exist or has been deleted";
    }
    public static class CLASS_HISTORY {
        public static final String CREATE_CLASS = "Created class %s with syllabus %s";
        public static final String UPDATE_CLASS = "Updated class %s: %s";
        public static final String CHANGE_STATUS = "Changed class %s status from %s to %s";
        public static final String DELETE_CLASS = "Deleted class %s";

        // New messages for ClassHistoryServiceImpl validations/logs
        public static final String INVALID_ACTION_TYPE = "Invalid action type: %s";
        public static final String INVALID_ROLE_IN_VISIBLE_TO_ROLES = "Invalid role in visible_to_roles: %s";
        public static final String VISIBLE_TO_ROLES_ALLOWED = "MANAGER,TEACHER,TEACHING_ASSISTANT,STUDENT,TEST_TAKER";
        public static final String SAVE_HISTORY_SUCCESS = "Saved class history id=%d classId=%d actionType=%s byUser=%s";

        // Messages used by CreateClassHistoryRequest (validation)
        public static final String CLASS_ID_REQUIRED = "Class ID is required";
        public static final String ACTION_DETAILS_REQUIRED = "Action details are required";
        public static final String ACTION_TYPE_REQUIRED = "Action type is required";
        public static final String VISIBLE_TO_ROLES_INVALID = "Invalid visible_to_roles format. Must be a comma-separated list of valid roles or empty.";
    }


    public static class CLASS_STUDENT {
        public static final String LIST_RETRIEVED = "Student list retrieved successfully";
        public static final String PROFILE_RETRIEVED = "Student profile retrieved successfully";
        public static final String STUDENT_ADDED = "Student added to class successfully";
        public static final String STUDENT_REMOVED = "Student removed from class successfully";
        public static final String STUDENTS_IMPORTED = "Students imported successfully";
        public static final String STUDENT_NOT_FOUND = "Student not found in class";
        public static final String DUPLICATE_ID = "Duplicate user IDs found in request: %s";
        public static final String STUDENT_LIMIT_EXCEEDED = "Cannot add more than %d students. Current: %d, Requested: %d";
        public static final String USER_EXISTS = "User %d is already active in classes %s";
        public static final String USER_ALREADY_ENROLLED = "Some students are already active in other classes: ";
        public static final String USERS_ALREADY_ACTIVE_IN_CLASS = "Users %s are already active in the class";
        public static final String ADD_STUDENT_SUCCESSFULLY = "Added %d student(s) to class '%s': %s";
        public static final String REACTIVE_STUDENT_SUCCESSFULLY = "Re-activated %d student(s) in class '%s': %s";
        public static final String REMOVE_STUDENT_SUCCESSFULLY = "Removed student '%s' from class '%s'";
        public static final String IMPORT_STUDENT_SUCCESSFULLY = "Imported %d student(s) to class '%s' from Excel file";

        // New: centralized validation / notification templates for ClassStudentServiceImpl
        public static final String VALIDATION_ERRORS = "Validation errors: %s";

        public static final String NOTIFY_ADDED_TITLE = "You have been added to class %s";
        public static final String NOTIFY_ADDED_MESSAGE = "%s added you to class %s";

        public static final String NOTIFY_REACTIVATED_TITLE = "Your account has been reactivated in class %s";
        public static final String NOTIFY_REACTIVATED_MESSAGE = "%s reactivated your account in class %s";

        public static final String NOTIFY_REMOVED_TITLE = "You have been removed from class %s";
        public static final String NOTIFY_REMOVED_MESSAGE = "%s removed you from class %s";

        public static final String USER_IDS_REQUIRED = "User IDs list cannot be empty";
        public static final String USER_ID_REQUIRED = "User ID cannot be null";
    }

    public static class SUBMISSION {
        public static final String NOT_FOUND = "Submission not found";
        public static final String INVALID_ID = "Invalid submissionId";
        public static final String FORBIDDEN_NOT_OWNER = "Forbidden: not the owner of the submission";
        public static final String CANNOT_START_IN_CURRENT_STATUS = "Submission cannot be started in its current status";
        public static final String UNAUTHORIZED_VIEW_SUBMISSIONS = "Unauthorized: Only teachers or teaching assistants can view submissions";
        public static final String FORBIDDEN_CHALLENGE_DRAFT = "You are not allowed to access challenges that are not yet completed.";
        public static final String EMPTY_SUBMISSION_IDS = "Submission IDs cannot be empty";
        public static final String INVALID_EXTEND_TIME = "Invalid extend time";
        public static final String NO_ELIGIBLE_FOR_EXTENSION = "No submissions valid for extension time: ";
        public static final String INVALID_RESET_DATES = "Invalid reset dates";

        // Moved/centralized messages used by SubmissionChallengeServiceImpl
        public static final String CANNOT_EXTEND_ELIGIBLE = "Submissions with ids [%s] cannot be extended because they are already submitted/graded/missed";
        public static final String CANNOT_RESET_PENDING_DRAFT = "Submissions with ids [%s] cannot be reset because they are PENDING or DRAFT";
        public static final String EXTEND_SUCCESS = "Extended deadline for %d submissions.";
        public static final String RESET_SUCCESS = "Reset %d submissions.";

        // New constants used by SubmissionQuestionServiceImpl
        public static final String NO_SECTIONS_FOR_CHALLENGE = "No sections found for challenge";
        public static final String ALREADY_COMPLETED = "Submission already completed";
        public static final String SUBMISSION_MISSED = "Submission missed";

        // New: DTO validation messages (Vietnamese / specific)
        public static final String SUBMISSION_IDS_REQUIRED_VN = "Danh sách submission ID không được để trống";
        public static final String NEW_EXPIRED_AT_REQUIRED_VN = "Thời gian gia hạn không được để trống";
        public static final String NEW_EXPIRED_AT_FUTURE_VN = "Thời gian gia hạn phải từ hiện tại trở đi";

        public static final String NEW_START_DATE_REQUIRED_VN = "Thời gian bắt đầu mới không được để trống";
        public static final String NEW_END_DATE_REQUIRED_VN = "Thời gian kết thúc mới không được để trống";

        // New: SaveSubmissionRequest / QuestionAnswer validation messages
        public static final String QUESTION_ANSWERS_REQUIRED = "Question answers cannot be null";
        public static final String QUESTION_ID_REQUIRED = "Question ID cannot be null";
        public static final String SUBMISSION_CONTENT_REQUIRED = "Submission content cannot be null";
    }

    public static final class TRANSLATOR {
        public static final String API_ERROR = "Failed to connect to the translation service. Please try again later.";
        public static final String TRANSLATION_FAILED = "Unable to translate the text. Please try again later.";
    }

    public static final class NOTIFICATION {
        public static final String NOT_FOUND_OR_NOT_OWNER = "Notification not found or you do not have permission.";

        // existing templates...
        public static final String SUBMISSION_TITLE = "Challenge submitted";
        public static final String SUBMISSION_MESSAGE_TEMPLATE = "You submitted \"%s\". Your submission has been received and will be graded soon.";

        // New notification constants used by SubmissionChallengeServiceImpl
        public static final String NEW_TEMP_SUBMISSION_TITLE = "Bạn có bài tập mới";
        public static final String NEW_TEMP_SUBMISSION_MESSAGE_TEMPLATE = "Một bài tập mới đã được tạo: %s";

        public static final String RESTORED_SUBMISSION_TITLE = "Submission phục hồi";
        public static final String RESTORED_SUBMISSION_MESSAGE_TEMPLATE = "Submission của bạn đã được phục hồi cho bài %s";

        public static final String TEMP_CREATED_SUBMISSION_TITLE = "Submission tạm tạo";
        public static final String TEMP_CREATED_SUBMISSION_MESSAGE_TEMPLATE = "Submission tạm đã được tạo cho bài %s";

        public static final String SOFT_DELETE_TITLE = "Các bài nộp của bạn đã bị ẩn";
        public static final String SOFT_DELETE_MESSAGE_TEMPLATE = "Một số submission của bạn trong lớp đã bị ẩn/gỡ bởi %s";

        public static final String EXTEND_DEADLINE_TITLE = "Thời hạn nộp bài đã được gia hạn";
        public static final String EXTEND_DEADLINE_MESSAGE_TEMPLATE = "Thời hạn nộp bài cho \"%s\" đã được gia hạn tới %s";

        public static final String RESET_SUBMISSION_TITLE = "Bài đã được reset";
        public static final String RESET_SUBMISSION_MESSAGE_TEMPLATE = "Bài \"%s\" đã được reset. Thời gian mới: %s → %s";

        public static final String SUBMISSION_STATUS_UPDATE_TITLE = "Cập nhật nộp bài";
        public static final String SUBMISSION_STATUS_UPDATE_MESSAGE_TEMPLATE = "Submissions: %d/%d students";

        // Device mismatch notifications
        public static final String DEVICE_MISMATCH_USER_MESSAGE = "Cảnh báo: Chỉ được dùng 1 thiết bị!";
        public static final String DEVICE_MISMATCH_TEACHER_TITLE = "Phát hiện gian lận: 2 thiết bị";
        public static final String DEVICE_MISMATCH_TEACHER_MESSAGE_TEMPLATE =
                "Học sinh <b>%s</b> đang dùng <b>2 thiết bị</b> cho bài <b>%s</b>";
    }
    public static class CLASS_TEACHER {
        public static final String TEACHER_ADDED = "Teacher added to class successfully";
        public static final String TEACHER_REMOVED = "Teacher removed from class successfully";
        public static final String LIST_RETRIEVED = "Teacher list retrieved successfully";
        public static final String TEACHER_NOT_FOUND = "Teacher not found in class";
        public static final String TEACHER_LIMIT_EXCEEDED= "Cannot add more than %d teacher with TEACHER role";
        public static final String TEACHING_ASSISTANT_LIMIT_EXCEEDED= "Cannot add more than %d teacher with TEACHING ASSISTANT role";
        public static final String TEACHER_EXISTED= "Class already has teacher";
        public static final String ADD_TEACHER_SUCCESSFULLY = "Added %d teacher to class '%s': %s";
        public static final String REACTIVE_TEACHER_SUCCESSFULLY = "Re-activated %d teacher in class '%s': %s";
        public static final String ADD_TEACHING_ASSISTANT_SUCCESSFULLY = "Added %d teaching assistant(s) to class '%s': %s";
        public static final String REACTIVE_TEACHING_ASSISTANT_SUCCESSFULLY = "Re-activated %d teaching assistant(s) in class '%s': %s";
        public static final String REMOVE_TEACHER_SUCCESSFULLY = "Removed %s '%s' from class '%s'";

        // Validation templates
        public static final String VALIDATION_USER_NOT_ACTIVE = "User ID %d (%s) is not active";
        public static final String VALIDATION_USER_NOT_TEACHER = "User ID %d (%s) is not a teacher or a teaching assistant";
        public static final String VALIDATION_USER_DELETED = "User ID %d (%s) has been deleted";
        public static final String VALIDATION_ERRORS = "Validation errors: %s";
        public static final String VALIDATION_ROLE_IN_CLASS = "Role in class is required";

        // Notification templates
        public static final String NOTIFY_ADDED_AS_TEACHER_TITLE = "You have been added as teacher to class %s";
        public static final String NOTIFY_ADDED_AS_TEACHER_MESSAGE = "%s assigned you as teacher in class %s";
        public static final String NOTIFY_ADDED_AS_TA_TITLE = "You have been added as teaching assistant to class %s";
        public static final String NOTIFY_ADDED_AS_TA_MESSAGE = "%s assigned you as teaching assistant in class %s";
        public static final String NOTIFY_REACTIVATED_TITLE = "Your role in class %s has been reactivated";
        public static final String NOTIFY_REACTIVATED_MESSAGE = "%s reactivated your role in class %s";
        public static final String NOTIFY_REMOVED_TITLE = "You have been removed from class %s";
        public static final String NOTIFY_REMOVED_MESSAGE = "%s removed your role from class %s";

        // New: canonical role strings and user-ids-not-found message
        public static final String ROLE_TYPE_TEACHER = "teacher";
        public static final String ROLE_TYPE_TEACHING_ASSISTANT = "teaching assistant";
        public static final String USER_IDS_NOT_FOUND = "User IDs not found: %s";
    }
    public static class GRADING {
        public static final String GRADING_NOT_FOUND = "Grading not found";
        public static final String CHALLENGE_MISSING = "Challenge missing";
        public static final String MANUAL_GRADING_ONLY_WR_SP = "Manual grading only allowed for WR, SP";
        public static final String SUBMISSION_QUESTION_NOT_FOUND = "Submission question not found";
        public static final String RECEIVED_WEIGHT_EXCEEDS_MAX = "Received weight exceeds max";

        // Notification templates
        public static final String AUTO_GRADE_NOTIFICATION_TITLE = "Bài làm vừa được chấm tự động";
        public static final String AUTO_GRADE_NOTIFICATION_TEMPLATE = "Bài làm của bạn cho bài \"%s\" đã được chấm tự động. Điểm: %s";

        public static final String MANUAL_GRADE_NOTIFICATION_TITLE = "Bài làm đã được chấm";
        public static final String MANUAL_GRADE_NOTIFICATION_TEMPLATE = "Bài làm của bạn cho bài \"%s\" đã được chấm. Điểm: %s";

        public static final String PER_QUESTION_GRADE_NOTIFICATION_TITLE = "Cập nhật điểm câu hỏi";
        public static final String PER_QUESTION_GRADE_NOTIFICATION_TEMPLATE = "Một câu hỏi trong bài làm của bạn đã được chấm. SubmissionId=%d";

        // New validation messages for DTOs (used by Grade* DTOs)
        public static final String RECEIVED_WEIGHT_REQUIRED = "receivedWeight is required";
        public static final String RECEIVED_WEIGHT_NON_NEGATIVE = "receivedWeight must be non-negative";

        public static final String RAW_SCORE_REQUIRED = "Raw score is required";
        public static final String RAW_SCORE_RANGE = "Raw score must be 0-10";
        public static final String PENALTY_RANGE = "Penalty must be 0.0-1.0";

        public static final String HIGHLIGHT_START_REQUIRED = "Start index is required";
        public static final String HIGHLIGHT_START_NON_NEGATIVE = "Start index must be non-negative";
        public static final String HIGHLIGHT_END_REQUIRED = "End index is required";
        public static final String HIGHLIGHT_END_NON_NEGATIVE = "End index must be non-negative";

        public static final String OVERALL_FEEDBACK_LENGTH_INVALID = "Overall feedback must be less than 5000 characters.";
        public static final int MAX_OVERALL_FEEDBACK_LENGTH_VALUE = 5000;
    }
    public static class SUBMISSION_LOG {
        public static final String INVALID_REQUEST_PARAMS = "Invalid request parameters";
        public static final String LOG_EVENT_REQUIRED = "Log event type is required";
        public static final String LOG_TIMESTAMP_REQUIRED = "Log timestamp is required";
        public static final String ANTI_CHEAT_DISABLED = "Anti-cheat disabled for challenge %d, ignoring logs";
        public static final String UNAUTHORIZED_LOG_APPEND = "Unauthorized log append by user %d for submission %d";
        public static final String FAILED_APPEND = "Failed to append logs for submission %d";
        public static final String APPEND_SUCCESS = "Appended %d logs for submission %d";
    }

    // NEW: centralized messages for student-level operations
    public static class STUDENT_LEVEL {
        public static final String INVALID_PARAMS = "UserId and levelId are required";
        public static final String EXISTING_LEVEL_DEACTIVATED = "Existing active level %d deactivated for user %d";
        public static final String ASSIGN_SUCCESS = "Assigned level %d to user %d";
        public static final String USER_NOT_FOUND = "User not found: %d";
        public static final String LEVEL_NOT_FOUND = "Level not found: %d";
        public static final String LEVEL_NOT_PUBLISHED = "Level is not published";
    }
    public static class REPORT {
        public static final String USER_ID_REQUIRED_FOR_TEACHERS = "userId is required for teachers";
        public static final String CLASS_NOT_FOUND = "Class not found";
        public static final String LOG_PARSE_FAILED = "Failed to parse submission logs";
        public static final String GET_CLASS_OVERVIEW = "Getting class overview for classId: %d";
        public static final String GET_CHALLENGE_OVERVIEW = "Getting challenge overview for challengeId: %d";
        public static final String GET_STUDENT_OVERVIEW = "Getting student overview for userId: %d";
        public static final String AT_RISK_STARTED = "Analyzing at-risk students for classId: %d";
    }
}
