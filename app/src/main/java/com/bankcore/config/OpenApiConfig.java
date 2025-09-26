package com.bankcore.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 Configuration for BankCore API
 * Provides comprehensive API documentation with security specifications
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bankCoreOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BankCore API")
                        .description("""
                                **Production-Ready Banking API** featuring:
                                
                                - 🔐 **Enterprise Security** with JWT authentication
                                - 🏦 **Banking Transactions** with compliance controls
                                - ⚡ **High Availability** Multi-AZ deployment
                                - 📊 **Observability** with metrics and tracing
                                - 🧪 **Chaos Engineering** resilience testing
                                
                                ## Architecture Highlights
                                
                                - **Event-Driven Architecture** with async processing
                                - **Multi-AZ Kubernetes** deployment with auto-scaling
                                - **Zero-Downtime** rolling updates
                                - **Circuit Breaker** pattern for resilience
                                - **Audit Trail** for regulatory compliance
                                
                                ## Security Features
                                
                                - Input validation with Bean Validation (JSR 380)
                                - SQL injection prevention with JPA
                                - Idempotency keys for transaction safety
                                - Rate limiting and DDoS protection
                                - Comprehensive audit logging
                                """)
                        .version("2.1.0")
                        .contact(new Contact()
                                .name("BankCore Engineering Team")
                                .email("support@bankcore.com")
                                .url("https://github.com/bankingcorp/bankcore"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("https://bankcore-prod.k8s.local")
                                .description("Production Kubernetes Cluster"),
                        new Server()
                                .url("https://bankcore-staging.k8s.local")
                                .description("Staging Environment"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT token for authentication")));
    }
}