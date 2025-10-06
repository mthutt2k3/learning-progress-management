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
    }
}
