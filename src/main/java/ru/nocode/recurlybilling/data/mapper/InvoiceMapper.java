package ru.nocode.recurlybilling.data.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import ru.nocode.recurlybilling.data.dto.response.InvoiceResponse;
import ru.nocode.recurlybilling.data.entities.Invoice;

@Mapper(componentModel = "spring")
public interface InvoiceMapper {
//    InvoiceMapper INSTANCE = Mappers.getMapper(InvoiceMapper.class);
//
//    InvoiceResponse toResponse(Invoice invoice);
}
