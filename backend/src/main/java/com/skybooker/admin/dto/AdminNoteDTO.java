package com.skybooker.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminNoteDTO {

    @Size(max = 500, message = "管理员备注不超过 500 字")
    private String adminNote;
}
