package com.tracker.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Documents the error bodies of the endpoints the gateway serves itself (/auth/**, /users/me).
 * Failures there are RFC 7807 {@code ProblemDetail}s (GatewayExceptionHandler) with one exception:
 * the rate limiter's 429 is a plain {@code {"error": "..."}} body written by AuthRateLimitFilter, so
 * it gets its own schema instead of being passed off as a Problem.
 *
 * <p>Proxied {@code /api/**} routes are not served by this application, so their errors are
 * documented in the owning service's spec; the gateway passes those bodies through unchanged. An
 * operation that already declares a status itself keeps it, and a table row for an endpoint that
 * does not exist is never applied.
 */
@Configuration
public class ProblemOpenApiCustomizer {

    static final String PROBLEM = "Problem";
    static final String RATE_LIMITED = "RateLimited";

    @Bean
    public OpenApiCustomizer problemResponsesCustomizer() {
        return ProblemOpenApiCustomizer::customize;
    }

    /** "METHOD /path" -> status code -> example name. */
    private static final Map<String, Map<String, String>> SPECIFIC = Map.ofEntries(
            Map.entry("POST /auth/register", Map.of("400", "ProblemValidation", "409", "ProblemEmailTaken")),
            Map.entry("POST /auth/login", Map.of("400", "ProblemValidation", "401", "ProblemInvalidCredentials")),
            Map.entry("POST /auth/refresh", Map.of("401", "ProblemInvalidRefreshToken")),
            Map.entry("POST /auth/forgot", Map.of("400", "ProblemValidation")),
            Map.entry("POST /auth/reset", Map.of("400", "ProblemInvalidResetToken")),
            Map.entry("GET /users/me", Map.of("404", "ProblemUserNotFound")));

    static void customize(OpenAPI openApi) {
        Components components = openApi.getComponents();
        if (components == null) {
            components = new Components();
            openApi.setComponents(components);
        }
        registerSchemasAndExamples(components);

        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            String key = method.name() + " " + path;
            SPECIFIC.getOrDefault(key, Map.of()).forEach((status, example) ->
                    add(operation, status, PROBLEM, "application/problem+json", example, describe(status)));
            if (path.startsWith("/auth/")) {
                // AuthRateLimitFilter: one shared per-IP budget across every /auth endpoint.
                add(operation, "429", RATE_LIMITED, "application/json", "RateLimitedExample",
                        "Too many requests from this address. Wait and retry.");
            }
        }));
    }

    private static String describe(String status) {
        return switch (status) {
            case "400" -> "Rejected input. `detail` explains why; field-level violations are also in `errors`.";
            case "401" -> "Credentials or token not accepted.";
            case "404" -> "Not found.";
            case "409" -> "Conflicts with current state.";
            default -> "Error.";
        };
    }

    private static void add(Operation operation, String status, String schema, String mediaType,
                            String exampleName, String description) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        if (operation.getResponses().containsKey(status)) {
            return;
        }
        Example ref = new Example();
        ref.set$ref("#/components/examples/" + exampleName);
        MediaType content = new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/" + schema))
                .addExamples(exampleName, ref);
        operation.getResponses().addApiResponse(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(mediaType, content)));
    }

    private static void registerSchemasAndExamples(Components components) {
        Schema<?> problem = new ObjectSchema()
                .description("RFC 7807 problem details. `detail` is the human-readable reason.")
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Bad Request"))
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().example("/auth/register"))
                .addProperty("errors", new ObjectSchema()
                        .additionalProperties(new StringSchema())
                        .description("Validation failures only: field name -> message, so a form can mark each "
                                + "field. `detail` joins the same messages."));
        problem.setAdditionalProperties(true);
        components.addSchemas(PROBLEM, problem);
        components.addSchemas(RATE_LIMITED, new ObjectSchema()
                .description("Body of a 429 from the /auth rate limiter. Not a Problem.")
                .addProperty("error", new StringSchema().example("Too many requests")));

        Map<String, Object> errors = new LinkedHashMap<>();
        errors.put("email", "email should be formatted and required");
        errors.put("password", "password is required");
        components.addExamples("ProblemValidation", example("Validation failure",
                body(400, "Bad Request", "email should be formatted and required; password is required",
                        "/auth/register", "errors", errors)));
        components.addExamples("ProblemEmailTaken", example("Email already registered",
                body(409, "Conflict", "Email already registered", "/auth/register")));
        components.addExamples("ProblemInvalidCredentials", example("Wrong email or password (the same message for both)",
                body(401, "Unauthorized", "Invalid email or password", "/auth/login")));
        components.addExamples("ProblemInvalidRefreshToken", example("Refresh token unknown, expired, revoked or reused",
                body(401, "Unauthorized", "Refresh token expired, please log in again", "/auth/refresh")));
        components.addExamples("ProblemInvalidResetToken", example(
                "Reset token unknown, expired or already used (one message for all three)",
                body(400, "Bad Request", "Invalid or expired reset token", "/auth/reset")));
        components.addExamples("ProblemUserNotFound", example("Token is valid but the account no longer exists",
                body(404, "Not Found", "User not found", "/users/me")));
        Map<String, Object> limited = new LinkedHashMap<>();
        limited.put("error", "Too many requests");
        components.addExamples("RateLimitedExample", example("Rate limited", limited));
    }

    private static Example example(String summary, Object value) {
        Example example = new Example();
        example.setSummary(summary);
        example.setValue(value);
        return example;
    }

    private static Map<String, Object> body(int status, String title, String detail, String instance, Object... extras) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        body.put("instance", instance);
        for (int i = 0; i < extras.length; i += 2) {
            body.put((String) extras[i], extras[i + 1]);
        }
        return body;
    }
}
