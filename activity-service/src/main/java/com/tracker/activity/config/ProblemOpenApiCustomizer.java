package com.tracker.activity.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Documents the error bodies. Every failure in this service is an RFC 7807 {@code ProblemDetail}
 * (see GlobalExceptionHandler), but springdoc cannot see that from the controllers, so without this
 * the published spec says nothing about what a 4xx looks like. This adds one shared {@code Problem}
 * schema, a set of named examples under {@code components.examples}, and attaches them to operations.
 *
 * <p>Generic rules apply by shape (a body or parameters can fail validation with 400; a path
 * variable can be unknown, 404). A small table adds the specific outcomes a client has to handle
 * (409 on a taken name, the 404 with {@code suggestions}, 403 on someone else's data). Anything an
 * operation already declares itself (an {@code @ApiResponse}) is left alone, so a hand-written entry
 * always wins. A table row for an endpoint that does not exist is simply never applied.
 */
@Configuration
public class ProblemOpenApiCustomizer {

    static final String PROBLEM = "Problem";

    @Bean
    public OpenApiCustomizer problemResponsesCustomizer() {
        return ProblemOpenApiCustomizer::customize;
    }

    /** "METHOD /path" (no trailing slash) -> status code -> example name. Applied in addition to the shape rules. */
    private static final Map<String, Map<String, String>> SPECIFIC = Map.ofEntries(
            Map.entry("POST /activitylog", Map.of(
                    "404", "ProblemActivityNameNotFound",
                    "409", "ProblemActivityInactive",
                    "400", "ProblemImplausibleSession")),
            Map.entry("POST /activity", Map.of("409", "ProblemNameTaken")),
            Map.entry("PATCH /activity/{id}", Map.of("409", "ProblemNameTaken", "400", "ProblemValidation")),
            Map.entry("GET /activitylog/{id}", Map.of("404", "ProblemNotFound")),
            Map.entry("GET /activitylog/user/{id}", Map.of("403", "ProblemForbidden")),
            Map.entry("GET /activitylog/user/{id}/recent", Map.of("403", "ProblemForbidden")),
            Map.entry("GET /activitylog/streaks/user/{id}", Map.of("403", "ProblemForbidden")),
            Map.entry("POST /activitylog/review/{id}/approve", Map.of("409", "ProblemReviewConflict")),
            Map.entry("POST /activitylog/review/{id}/reject", Map.of("409", "ProblemReviewConflict")));

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
        final Components target = components;
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

    // Path and query parameters can be rejected (bad type, bad value); the userId header is the
    // gateway-injected identity and not something a caller gets wrong in a way described here.
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
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
        }
        if (operation.getResponses().containsKey(status)) {
            return; // a hand-written @ApiResponse wins
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
                .description("RFC 7807 problem details. `detail` is the human-readable reason. The members below "
                        + "the standard ones are extensions that appear only on the failures that document them.")
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Bad Request"))
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().example("/activitylog/"))
                .addProperty("errors", new ObjectSchema()
                        .additionalProperties(new StringSchema())
                        .description("Validation failures only: field name -> message, so a form can mark each "
                                + "field. `detail` joins the same messages."))
                .addProperty("requestedName", new StringSchema()
                        .description("Unknown activity name only: what the client sent."))
                .addProperty("suggestions", new ArraySchema()
                        .items(new Schema<>().$ref("#/components/schemas/ActivitySuggestion"))
                        .description("Unknown activity name only: ranked close matches, inactive ones included "
                                + "(with `active: false`)."));
        problem.setAdditionalProperties(true);
        components.addSchemas(PROBLEM, problem);

        components.addExamples("ProblemValidation", example("Validation failure",
                body(400, "Bad Request", "Start time is required; Activity name is required", "/activitylog/",
                        "errors", map("startTime", "Start time is required", "activityName", "Activity name is required"))));
        components.addExamples("ProblemNotFound", example("Unknown id",
                body(404, "Not Found", "Activity log not found: 42", "/activitylog/42")));
        components.addExamples("ProblemActivityNameNotFound", example(
                "Unknown activity name: the body carries the typed name and ranked suggestions",
                body(404, "Not Found", "Activity not found: Runing", "/activitylog/",
                        "requestedName", "Runing",
                        "suggestions", List.of(map("name", "Running", "category", "HEALTH", "active", true,
                                "score", 0.93, "matchedOn", "NAME")))));
        components.addExamples("ProblemActivityInactive", example("Activity exists but is switched off",
                body(409, "Conflict", "Activity 'Archery' is inactive and cannot accept new log entries. Re-enable the activity before logging time against it.", "/activitylog/")));
        components.addExamples("ProblemImplausibleSession", example("Session rejected by the integrity caps",
                body(400, "Bad Request", "Session duration of 1500 minutes exceeds the maximum allowed 1440 minutes.", "/activitylog/")));
        components.addExamples("ProblemNameTaken", example("Name already used by another activity",
                body(409, "Conflict", "An activity named 'Reading' already exists", "/activity/")));
        components.addExamples("ProblemForbidden", example("Path names another user",
                body(403, "Forbidden", "Not permitted to access another user's data", "/activitylog/user/7")));
        components.addExamples("ProblemReviewConflict", example("Log is not in a reviewable state",
                body(409, "Conflict", "Activity log 12 is not pending review (current status: CLEARED). Only FLAGGED logs can be approved or rejected.", "/activitylog/review/12/approve")));
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

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
