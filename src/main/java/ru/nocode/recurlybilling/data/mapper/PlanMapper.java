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

}