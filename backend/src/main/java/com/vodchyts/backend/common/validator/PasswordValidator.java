package com.vodchyts.backend.common.validator;

import com.vodchyts.backend.exception.InvalidPasswordException;
import org.springframework.stereotype.Component;

@Component
public class PasswordValidator {

    public void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new InvalidPasswordException("Пароль не может быть пустым");
        }
        if (password.length() < 8) {
            throw new InvalidPasswordException("Пароль должен содержать не менее 8 символов");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new InvalidPasswordException("Пароль должен содержать хотя бы одну заглавную букву");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new InvalidPasswordException("Пароль должен содержать хотя бы одну строчную букву");
        }
        if (!password.matches(".*\\d.*")) {
            throw new InvalidPasswordException("Пароль должен содержать хотя бы одну цифру");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-={}:;\"'<>,.?/].*")) {
            throw new InvalidPasswordException("Пароль должен содержать хотя бы один специальный символ");
        }
    }
}