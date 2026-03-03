package ru.nocode.recurlybilling.data.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import ru.nocode.recurlybilling.data.dto.request.PlanCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.PlanResponse;
import ru.nocode.recurlybilling.data.entities.Plan;

@Mapper(componentModel = "spring")
public interface PlanMapper {
//    PlanMapper INSTANCE = Mappers.getMapper(PlanMapper.class);
//
//    PlanResponse toResponse(Plan plan);
//
//    Plan toEntity(PlanCreateRequest request);
}