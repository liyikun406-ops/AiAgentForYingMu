package com.orangecode.agent.model.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiResponse<T> {
    public int coe;
    public String message;
    public T data;

    public static <T> ApiResponse<T> success(T data){
        ApiResponse response = new ApiResponse();
        response.setCoe(200);
        response.setData(data);
        response.setMessage("success");
        return response;
    }

    public static <T> ApiResponse<T> error(String message){
        ApiResponse response = new ApiResponse();
        response.setCoe(500);
        response.setMessage(message);
        return response;
    }

}
