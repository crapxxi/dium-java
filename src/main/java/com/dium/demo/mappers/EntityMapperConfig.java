package com.dium.demo.mappers;

import org.mapstruct.Builder;
import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Общие настройки мапперов. Билдеры сущностей отключены намеренно: MapStruct должен создавать
 * сущность через конструктор и сеттеры, иначе не сработают {@code @AfterMapping} с
 * {@code @MappingTarget} и значения полей по умолчанию.
 * <p>
 * Поля сущностей, которых нет в запросах (id, владелец, статус, служебные флаги), заполняют
 * сервисы, поэтому предупреждения о незамапленных полях отключены.
 */
@MapperConfig(
        componentModel = "spring",
        builder = @Builder(disableBuilder = true),
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface EntityMapperConfig {
}
