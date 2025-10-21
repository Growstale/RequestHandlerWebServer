package com.vodchyts.backend.common.validator;

import com.vodchyts.backend.exception.InvalidPasswordException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PasswordValidator {
    private static final Pattern UPPER_CASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWER_CASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(".*[!@#$%^&*()_+\\-={}:;\"'<>,.?/].*");

    public void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new InvalidPasswordException("Пароль не может быть пустым");
        }
        if (password.length() < 8) {
            throw new InvalidPasswordException("Пароль должен содержать не менее 8 символов");
        }
        if (!UPPER_CASE_PATTERN.matcher(password).matches()) {
            throw new InvalidPasswordException("Пароль должен содержать хотя бы одну заглавную букву");
        }
        if (!LOWER_CASE_PATTERN.matcher(password).matches()) {
            throw new InvalidPasswordException("Пароль должен содержать хотя бы одну строчную букву");
        }
        if (!DIGIT_PATTERN.matcher(password).matches()) {
            throw new InvalidPasswordException("Пароль должен содержать хотя бы одну цифру");
        }
        if (!SPECIAL_CHAR_PATTERN.matcher(password).matches()) {
            throw new InvalidPasswordException("Пароль должен содержать хотя бы один специальный символ");
        }
    }
}