package com.vodchyts.backend.exception;

public class ShopAlreadyExistsException extends RuntimeException {
    public ShopAlreadyExistsException(String message) {
        super(message);
    }
}