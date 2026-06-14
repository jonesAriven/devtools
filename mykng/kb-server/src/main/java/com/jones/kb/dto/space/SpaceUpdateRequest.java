package com.jones.kb.dto.space;

import lombok.Data;

@Data
public class SpaceUpdateRequest {

    private String name;

    private String type;

    private String description;

    private Integer status;
}
