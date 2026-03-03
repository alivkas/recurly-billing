package ru.nocode.recurlybilling.data.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import ru.nocode.recurlybilling.data.dto.response.TenantOnboardingResponse;
import ru.nocode.recurlybilling.data.entities.Tenant;

@Mapper(componentModel = "spring")
public interface TenantMapper {
//    TenantMapper INSTANCE = Mappers.getMapper(TenantMapper.class);
//
//    TenantOnboardingResponse toOnboardingResponse(Tenant tenant);
}
