package com.orangecode.agent.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRequest {
    @JsonProperty(value = "id")
    @JsonAlias({"id", "ID"})
    public String id;

    @JsonProperty(value = "Question")
    @JsonAlias({"question", "QUESTION"})
    public String Question;
}
