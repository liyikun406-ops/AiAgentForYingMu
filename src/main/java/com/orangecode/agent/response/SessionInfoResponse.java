package com.orangecode.agent.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SessionInfoResponse {
    public String sessionId;
    private int messagePairCOunt;
    private long createTime;
}
