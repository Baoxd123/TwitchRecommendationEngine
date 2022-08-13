package com.laioffer.twitchjupiter.service;

public class TwitchException extends RuntimeException{
    public TwitchException(String errorMessage){
        super(errorMessage);
    }
}
