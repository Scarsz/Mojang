package me.scarsz.mojang.exception;

public class ProfileFetchException extends Exception {

    public ProfileFetchException(String target, Exception cause) {
        super("Failed getting profile for " + target, cause);
    }

}
