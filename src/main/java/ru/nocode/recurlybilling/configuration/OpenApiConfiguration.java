package ru.nocode.recurlybilling.configuration;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Recurly Billing API")
                        .version("1.0")
                        .description("""
                API для управления рекуррентными платежами в образовательных проектах.
                
                **Особенности:**
                - Поддержка российских платежных систем (ЮKassa, CloudPayments)
                - Соответствие ФЗ-152 о персональных данных
                - Многоарендная архитектура (multi-tenant)
                - Вебхуки для уведомления о событиях платежей
                """))
                .addSecurityItem(new SecurityRequirement().addList("TenantAuth"))
                .components(new Components()
                        .addSecuritySchemes("TenantAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("Введите ваш API-ключ организации")
                        )
                        .addParameters("X-Tenant-ID", new Parameter()
                                .in(String.valueOf(ParameterIn.HEADER))
                                .name("X-Tenant-ID")
                                .description("Идентификатор вашей организации")
                                .required(true)
                                .schema(new Schema().type("string"))
                        )
                );
    }
}
