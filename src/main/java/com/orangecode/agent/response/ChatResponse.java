package com.orangecode.agent.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatResponse {
    public boolean success;
    public String answer;
    public String errorMessage;

    public static ChatResponse success(String answer) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setAnswer(answer);
        return response;
    }

    public static ChatResponse error(String errorMessage) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        return response;
    }
}
