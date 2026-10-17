package com.orangecode.agent.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SseResponse {
    private String type;
    private String data;

    public static SseResponse error(String data){
        SseResponse response = new SseResponse();
        response.setData(data);
        response.setType("error");
        return response;
    }

    public static SseResponse content(String data){
        SseResponse response = new SseResponse();
        response.setData(data);
        response.setType("content");
        return response;
    }

    public static SseResponse done(){
        SseResponse response = new SseResponse();
        response.setType("done");
        return response;
    }
}
