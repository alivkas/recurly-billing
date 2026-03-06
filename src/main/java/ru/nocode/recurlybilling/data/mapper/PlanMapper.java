package ru.nocode.recurlybilling.data.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.nocode.recurlybilling.data.dto.request.PlanCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.PlanResponse;
import ru.nocode.recurlybilling.data.entities.Plan;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.Map;

@Mapper(componentModel = "spring")
public abstract class PlanMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Mapping(target = "id", source = "id")
    public abstract PlanResponse toResponse(Plan plan);

    @Mapping(target = "metadata", qualifiedByName = "mapToMetadata")
    public abstract Plan toEntity(PlanCreateRequest request);

    @Named("mapToMetadata")
    protected JsonNode mapToMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.valueToTree(metadata);
    }

    protected Map<String, Object> mapFromMetadata(JsonNode metadata) {
        if (metadata == null || metadata.isNull() || metadata.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.convertValue(metadata, Map.class);
        } catch (IllegalArgumentException e) {
            return Collections.emptyMap();
        }
    }
}