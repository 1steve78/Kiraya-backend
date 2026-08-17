package com.hyperlocal.dto;

public class LoginResponse {

    private  String token;
    private  UserResponse user;

    public LoginResponse(String token , UserResponse user){
        this.token = token;
        this.user = user;
    }

    public UserResponse getUser() {
        return user;
    }

    public String getToken() {
        return token;
    }
}
