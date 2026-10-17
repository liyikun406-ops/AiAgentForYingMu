package com.orangecode.agent.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data){
        ApiResponse response = new ApiResponse();
        response.setCode(200);
        response.setData(data);
        response.setMessage("success");
        return response;
    }

    public static <T> ApiResponse<T> error(String message){
        ApiResponse response = new ApiResponse();
        response.setCode(500);
        response.setMessage(message);
        return response;
    }

}
