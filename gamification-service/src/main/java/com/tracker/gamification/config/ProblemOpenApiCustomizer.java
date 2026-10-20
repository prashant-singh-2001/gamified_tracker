package com.tracker.gamification.config;

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
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Documents the error bodies. Every failure in this service is an RFC 7807 {@code ProblemDetail}
 * (see GlobalExceptionHandler), but springdoc cannot see that from the controllers, so without this
 * the published spec says nothing about what a 4xx looks like. This adds one shared {@code Problem}
 * schema, named examples under {@code components.examples}, and attaches them to operations.
 *
 * <p>Generic rules apply by shape (a body or parameters can fail validation with 400; a path
 * variable can be unknown, 404); a small table adds the specific outcomes (403 on another user's
 * data, 409 on a threshold that breaks the level curve). An operation that already declares a status
 * itself keeps it, and a table row for an endpoint that does not exist is never applied.
 */
@Configuration
public class ProblemOpenApiCustomizer {

    static final String PROBLEM = "Problem";

    @Bean
    public OpenApiCustomizer problemResponsesCustomizer() {
        return ProblemOpenApiCustomizer::customize;
    }

    /** "METHOD /path" (no trailing slash) -> status code -> example name. */
    private static final Map<String, Map<String, String>> SPECIFIC = Map.ofEntries(
            Map.entry("GET /level/user/{userId}", Map.of("403", "ProblemForbidden")),
            Map.entry("GET /level/{id}", Map.of("404", "ProblemNotFound")),
            Map.entry("POST /notifications/{id}/read", Map.of("404", "ProblemNotificationNotFound")),
            Map.entry("PUT /threshold/{activityId}/{level}", Map.of(
                    "404", "ProblemNotFound", "409", "ProblemThresholdCurve")),
            Map.entry("DELETE /threshold/{activityId}/{level}", Map.of("404", "ProblemNotFound")),
            Map.entry("POST /threshold", Map.of("409", "ProblemThresholdCurve")));

    static void customize(OpenAPI openApi) {
        Components components = openApi.getComponents();
        if (components == null) {
            components = new Components();
            openApi.setComponents(components);
        }
        registerSchemaAndExamples(components);

        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            String key = method.name() + " " + stripTrailingSlash(path);
            boolean hasPathVariable = path.contains("{");
            boolean hasBody = operation.getRequestBody() != null;
            boolean hasInputParameter = operation.getParameters() != null
                    && operation.getParameters().stream().anyMatch(ProblemOpenApiCustomizer::isInput);

            if (hasBody || hasInputParameter) {
                add(operation, "400", "ProblemValidation", "Invalid request. `detail` explains why; field-level "
                        + "violations are also in `errors` (field -> message).");
            }
            if (hasPathVariable) {
                add(operation, "404", "ProblemNotFound", "No such resource.");
            }
            SPECIFIC.getOrDefault(key, Map.of()).forEach((status, example) ->
                    add(operation, status, example, describe(status)));
        }));
    }

    // The userId header is the gateway-injected identity, not something a caller gets wrong here.
    private static boolean isInput(Parameter p) {
        return "path".equals(p.getIn()) || "query".equals(p.getIn());
    }

    private static String describe(String status) {
        return switch (status) {
            case "400" -> "Rejected input. `detail` explains why.";
            case "403" -> "Not permitted: the path names another user's data.";
            case "404" -> "Not found.";
            case "409" -> "Conflicts with current state.";
            default -> "Error.";
        };
    }

    private static void add(Operation operation, String status, String exampleName, String description) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        if (operation.getResponses().containsKey(status)) {
            return;
        }
        Example ref = new Example();
        ref.set$ref("#/components/examples/" + exampleName);
        MediaType mediaType = new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM))
                .addExamples(exampleName, ref);
        operation.getResponses().addApiResponse(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/problem+json", mediaType)));
    }

    private static String stripTrailingSlash(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static void registerSchemaAndExamples(Components components) {
        Schema<?> problem = new ObjectSchema()
                .description("RFC 7807 problem details. `detail` is the human-readable reason.")
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Bad Request"))
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().example("/threshold/1/2"))
                .addProperty("errors", new ObjectSchema()
                        .additionalProperties(new StringSchema())
                        .description("Validation failures only: field name -> message, so a form can mark each "
                                + "field. `detail` joins the same messages."));
        problem.setAdditionalProperties(true);
        components.addSchemas(PROBLEM, problem);

        Map<String, Object> errors = new LinkedHashMap<>();
        errors.put("xpRequired", "xpRequired must be greater than zero");
        components.addExamples("ProblemValidation", example("Validation failure",
                body(400, "Bad Request", "xpRequired must be greater than zero", "/threshold/1/2", "errors", errors)));
        components.addExamples("ProblemNotFound", example("Unknown id",
                body(404, "Not Found", "ActivityLevelThreshold not found for activity 1 level 9", "/threshold/1/9")));
        components.addExamples("ProblemNotificationNotFound", example("Unknown notification",
                body(404, "Not Found", "Notification 42 not found", "/notifications/42/read")));
        components.addExamples("ProblemForbidden", example("Path names another user",
                body(403, "Forbidden", "Not permitted to access another user's data", "/level/user/7")));
        components.addExamples("ProblemThresholdCurve", example("Write would break the level curve",
                body(409, "Conflict", "Level 2 must need more XP than level 1 (100.0), but got 50.0", "/threshold/1/2")));
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
