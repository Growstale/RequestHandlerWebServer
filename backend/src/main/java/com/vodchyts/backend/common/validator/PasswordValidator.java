package com.vodchyts.backend.common.validator;

import com.vodchyts.backend.exception.InvalidPasswordException;
import org.springframework.stereotype.Component;

@Component
public class PasswordValidator {

    public void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new InvalidPasswordException("Password cannot be empty");
        }
        if (password.length() < 8) {
            throw new InvalidPasswordException("Password must be at least 8 characters long");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new InvalidPasswordException("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new InvalidPasswordException("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new InvalidPasswordException("Password must contain at least one digit");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-={}:;\"'<>,.?/].*")) {
            throw new InvalidPasswordException("Password must contain at least one special character");
        }
    }
}
