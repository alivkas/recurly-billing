package ru.nocode.recurlybilling.data.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import ru.nocode.recurlybilling.data.dto.request.SubscriptionCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.SubscriptionResponse;
import ru.nocode.recurlybilling.data.entities.Subscription;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {
//    SubscriptionMapper INSTANCE = Mappers.getMapper(SubscriptionMapper.class);
//
//    SubscriptionResponse toResponse(Subscription subscription);
//
//    Subscription toEntity(SubscriptionCreateRequest request);
}