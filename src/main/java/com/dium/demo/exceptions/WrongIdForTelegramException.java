package com.dium.demo.exceptions;

public class WrongIdForTelegramException extends Exception{
    public WrongIdForTelegramException(String message){
        super(message);
    }
}
