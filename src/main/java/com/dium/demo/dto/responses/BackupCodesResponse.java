package com.dium.demo.dto.responses;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record BackupCodesResponse(
        @Schema(description = "Одноразовые резервные коды. Возвращаются только один раз — на сервере хранятся лишь хэши")
        List<String> backupCodes
) { }
