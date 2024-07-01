package org.opentripplanner.middleware.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.jetty.http.HttpMethod;
import org.opentripplanner.middleware.OtpMiddlewareMain;
import org.opentripplanner.middleware.controllers.response.ResponseList;
import org.opentripplanner.middleware.models.AbstractUser;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.YamlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.ConfigUtils.getVersionFromJar;

/**
 * Class that generates an enhanced public-facing documentation, in OpenAPI 2.0 (Swagger) format,
 * from the documentation that is auto-generated at runtime by spark-swagger.
 * The public facing documentation contains a collection of public endpoints, including
 * applicable security parameters and object models available to an {@link ApiUser}.
 * It excludes admin endpoints such as viewing application errors or request logs.
 */
public class PublicApiDocGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(PublicApiDocGenerator.class);
    private static final String SWAGGER_SUPPLEMENTS_FILE = "swagger-supplements.yaml";

    /**
     * List of public API paths published in the public-facing API documentation.
     * TODO: List any new public endpoints here in order for them to be published.
     */
    private static final String[] PUBLIC_PATHS = new String[] {
        "api/secure/monitoredtrip",
        "api/secure/triprequests",
        "api/secure/user",
        "otp",
        "pelias"
    };
    private static final String DEFINITIONS_KEY = "definitions";
    private static final String DEFINITIONS_REF = "#/definitions/";
    private static final String REF_FIELD = "$ref";
    private static final String RESPONSES = "responses";
    private static final String ABSTRACT_USER = "AbstractUser";
    private static final String OTP_USER = "OtpUser";
    private static final String AUTOGENERATED_DOC_URL = "http://localhost:4567/doc.yaml";
    // Use the system temporary directory to write the output file
    private static final String OUTPUT_FILE = Paths.get(System.getProperty("java.io.tmpdir"), "otp-middleware-swagger.yaml").toString();

    private static final String AWS_API_SERVER = getConfigPropertyAsText("AWS_API_SERVER");
    private static final String AWS_API_STAGE = getConfigPropertyAsText("AWS_API_STAGE");
    public static final String RESPONSE_LIST = "ResponseList";

    /**
     * Basic map of types to path
     */
    private static final Map<String, String> TYPES_TO_PATH = Map.of(
        OTP_USER, "user",
        "MonitoredTrip", "monitoredtrip",
        "TripRequest", "triprequests"
    );

    // Cached variables.
    private final ObjectNode swaggerRoot;
    private final ObjectNode paths;
    private final ObjectNode definitions;
    private final ArrayNode tags;

    public PublicApiDocGenerator() throws IOException {
        // Start with the swagger YAML skeleton generated by spark-swagger (clone it)
        String autoGeneratedSwagger = getSwaggerDocsAsString();

        // The document we are working off and related nodes.
        swaggerRoot =  YamlUtils.yamlMapper.readTree(autoGeneratedSwagger).deepCopy();
        definitions = (ObjectNode) swaggerRoot.get(DEFINITIONS_KEY);
        paths = (ObjectNode) swaggerRoot.get("paths");
        tags = (ArrayNode) swaggerRoot.get("tags");
    }

    /**
     * Generate the public-facing Swagger document.
     * This method adds security parameters, method responses, and removes non-public API resources
     * and unused object models from the document auto-generated by spark-swagger.
     */
    public Path generatePublicApiDocs() throws IOException {
        // Supplemental snippets YAML document
        JsonNode supplements = YamlUtils.yamlMapper.readTree(OtpMiddlewareMain.class.getClassLoader().getResourceAsStream(SWAGGER_SUPPLEMENTS_FILE));

        // Do the modifications for creating a public API documentation.
        insertPeliasGeocoderEndpoint(supplements);
        removeRestrictedPathsAndTags();
        addAuthorizationParams(supplements.get("securityDefinitions"));
        insertMethodResponses(supplements.get(RESPONSES));
        insertAbstractUserRefs(supplements.get(DEFINITIONS_KEY));
        insertResponseListRefs(supplements.get(DEFINITIONS_KEY));
        inlineArrayDefinitions();
        // TODO: Add description to the fields of generated types?

        // Remove unwanted fields that spark-swagger created, add fields that were missed.
        removeTypeFields(ItineraryExistence.ItineraryExistenceResult.class.getSimpleName(), "itineraries");
        addTypeField(ItineraryExistence.ItineraryExistenceResult.class.getSimpleName(), "valid", "boolean");
        removeTypeFields(ItineraryExistence.class.getSimpleName(), "otpRequests", "referenceItinerary", "tripIsArriveBy", "otpResponseProvider");

        // Cleanup the final document.
        generateMissingTypes();
        removeUnusedTypes();
        alphabetizeEntries(paths);
        alphabetizeEntries(definitions);

        // Overwrite top-level parameters.
        swaggerRoot.put("host", AWS_API_SERVER);
        swaggerRoot.put("basePath", "/" + AWS_API_STAGE);
        ((ObjectNode) swaggerRoot.get("info")).put("version", getVersionFromJar());


        // Generate output file.
        Path outputPath = new File(OUTPUT_FILE).toPath();
        String yamlOutput = YamlUtils.yamlMapper.writer().writeValueAsString(swaggerRoot);
        Files.writeString(outputPath, yamlOutput);

        LOG.info("Wrote public API documentation to: {}", outputPath);

        return outputPath;
    }

    /**
     * Inserts Pelias Geocoder documentation placeholder.
     */
    private void insertPeliasGeocoderEndpoint(JsonNode templateRootNode) {
        final String PELIAS = "pelias";
        JsonNode templatePathsNode = templateRootNode.get("paths");

        // Insert Pelias geocoder template in the paths (one item only).
        String geocoderPath = getFieldNames(templatePathsNode, path -> path.contains(PELIAS)).get(0);
        paths.set(geocoderPath, templatePathsNode.get(geocoderPath));

        // Insert geocoder tag (one item only).
        for (JsonNode tagNode : templateRootNode.get("tags")) {
            if (tagNode.get("name").asText().equals(PELIAS)) {
                tags.add(tagNode);
                break;
            }
        }
    }

    /**
     * Reorders entries under the provided node alphabetically.
     */
    private void alphabetizeEntries(ObjectNode node) {
        List<String> sortedFields = getFieldNames(node);
        sortedFields.sort(String::compareTo);

        ObjectNode newNode = JsonNodeFactory.instance.objectNode();
        for (String field : sortedFields) {
            newNode.set(field, node.get(field));
        }

        node.removeAll();
        node.setAll(newNode);
    }

    /**
     * Inserts template responses to the methods under all paths.
     */
    private void insertMethodResponses(JsonNode responseDefinitions) {
        // Insert the response definitions to the root document.
        swaggerRoot.set(RESPONSES, responseDefinitions);

        // Response template that contains a reference to each entry in responseDefinitions,
        // and that will be added to the response section for all methods.
        ObjectNode responseTemplate = JsonNodeFactory.instance.objectNode();
        for (String responseCode : getFieldNames(responseDefinitions)) {
            responseTemplate.set(
                responseCode,
                JsonNodeFactory.instance.objectNode().put(REF_FIELD, "#/responses/" + responseCode)
            );
        }

        // Insert response references in method responses.
        for (JsonNode responseNode: paths.findValues(RESPONSES)) {
            ((ObjectNode) responseNode).setAll(responseTemplate.deepCopy());
        }
    }

    /**
     * Inserts security definitions, and insert security params to any methods
     * in all public-facing /secure/ endpoints. (Restricted endpoints are excluded and removed from final docs.)
     */
    private void addAuthorizationParams(JsonNode securityDefinitions) {
        // Insert security definitions.
        swaggerRoot.set("securityDefinitions", securityDefinitions);

        // Extract security names from the security definition and
        // build a security template node to be inserted all methods in /secure/ endpoints:
        //   security:
        //   - api_key: []
        //   - bearer_token: []
        //   ... (Names are extracted from the security definition.)
        ArrayNode securityParam = JsonNodeFactory.instance.arrayNode();
        for (String securityName : getFieldNames(securityDefinitions)) {
            securityParam.add(JsonNodeFactory.instance.objectNode()
                .set(securityName, JsonNodeFactory.instance.arrayNode()));
        }

        List<String> secureEndpoints = getFieldNames(paths, path -> path.contains("/secure/"));
        for (String endpoint : secureEndpoints) {
            for (JsonNode methodNode : paths.get(endpoint)) {
                ((ObjectNode) methodNode).set("security", securityParam.deepCopy());
            }
        }
    }

    /**
     * Extracts the field names of a JsonNode subject to a predicate.
     * @param node The node whose fields to scan.
     * @param nameFilter If specified, determines which fields are returned.
     * @return The list of field names that match the nameFilter, or all fields if none is provided.
     */
    private List<String> getFieldNames(JsonNode node, Predicate<String> nameFilter) {
        List<String> fields = new ArrayList<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String fieldName = it.next();
            if (nameFilter == null || nameFilter.test(fieldName)) {
                fields.add(fieldName);
            }
        }
        return fields;
    }
    /**
     * Shorthand for the method above.
     */
    private List<String> getFieldNames(JsonNode node) {
        return getFieldNames(node, null);
    }

    /**
     * Remove restricted paths and tags (e.g. api/admin/user).
     * See methods isRestricted/isPublic for restricted and public paths.
     */
    private void removeRestrictedPathsAndTags() {
        // Remove restricted paths.
        List<String> pathsToRemove = getFieldNames(paths, this::isRestricted);
        paths.remove(pathsToRemove);

        // Remove restricted tags
        // (actually builds ArrayNode with sorted non-restricted tags).

        HashMap<String, JsonNode> remainingTags = new HashMap<>();
        for (JsonNode tagNode : tags) {
            String tagName = tagNode.get("name").asText();
            if (isPublic(tagName)) {
                remainingTags.put(tagName, tagNode.deepCopy());
            }
        }

        ArrayNode newTagsNode = JsonNodeFactory.instance.arrayNode();
        remainingTags.keySet()
            .stream()
            .sorted(String::compareTo)
            .forEach(tagName -> newTagsNode.add(remainingTags.get(tagName)));

        tags.removeAll();
        tags.addAll(newTagsNode);
    }

    /**
     * Determines whether a given path is public.
     */
    private boolean isPublic(String pathName) {
        for (String publicPath : PUBLIC_PATHS) {
            if (pathName.matches("^/?" + publicPath + ".*")) {
                return true;
            }
        }
        return false;
    }
    /**
     * Determines whether a given path is restricted (non-public).
     */
    private boolean isRestricted(String pathName) {
        return !isPublic(pathName);
    }

    /**
     * Edit array types (e.g. MonitoredTrip[]) found in the definitions sections.
     * (These are generated by us, using an array as return type, bypassing a spark-swagger limitation.)
     */
    private void inlineArrayDefinitions() {
        final String ARRAY_MARKER = "[]";

        // Find return type references from all "schema" entries.
        for (JsonNode node: swaggerRoot.findValues("schema")) {
            ObjectNode schemaNode = (ObjectNode) node;
            JsonNode refNode = node.get(REF_FIELD);
            if (refNode != null && refNode.asText().endsWith(ARRAY_MARKER)) {
                String baseType = refNode.asText().substring(DEFINITIONS_REF.length()).replace(ARRAY_MARKER, "");

                // Replace the ref node with the inlined array definition, so the schema node becomes:
                //   schema:
                //     type: array
                //     items:
                //       $ref: "#/definitions/MonitoredTrip"
                schemaNode.remove(REF_FIELD);
                schemaNode
                    .put("type", "array")
                    .set("items", JsonNodeFactory.instance.objectNode()
                        .put(REF_FIELD, DEFINITIONS_REF + baseType));

                // Delete the autogenerated array definition.
                definitions.remove(baseType + ARRAY_MARKER);
            }
        }
    }

    /**
     * Generate missing types not created by spark-swagger.
     * Note: This method might not end up creating types if there are no missing types.
     */
    private void generateMissingTypes() {
        // Find the types from all $ref entries.
        for (JsonNode node: swaggerRoot.findValues(REF_FIELD)) {
            if (node.asText().startsWith(DEFINITIONS_REF)) {
                String baseType = node.asText().substring(DEFINITIONS_REF.length());

                // Add type if it is not defined.
                if (definitions.get(baseType) == null) {
                    JsonNode typeDefinition = JsonNodeFactory.instance.objectNode()
                        .put("type", "object")
                        .put("description", "autogenerated");

                    definitions.set(baseType, typeDefinition);
                }
            }
        }
    }

    /**
     * Removes unused types (not referenced in a $ref entry anywhere) from the definitions section.
     */
    private void removeUnusedTypes() {
        int unusedTypeCount;
        // Execute until there are no unused types.
        do {
            // Find return type references anywhere (only store one instance of each).
            HashSet<String> typeReferences = new HashSet<>();
            for (JsonNode refNode : swaggerRoot.findValues(REF_FIELD)) {
                // The reference is typically in the form "#/definitions/TypeName".
                // Extract the "raw" type (TypeName in example above) from the definition string.
                String typeName = refNode.asText().substring(DEFINITIONS_REF.length());
                typeReferences.add(typeName);
            }

            // List types from the definitions node that don't have a reference from the list above.
            // Then remove unused types from the definitions node.
            List<String> typesToRemove = getFieldNames(definitions, type -> !typeReferences.contains(type));
            definitions.remove(typesToRemove);

            unusedTypeCount = typesToRemove.size();
        } while(unusedTypeCount > 0);
    }

    /**
     * Removes specific fields in a type.
     */
    private void removeTypeFields(String type, String... fields) {
        ObjectNode typeProperties = (ObjectNode) definitions.get(type).get("properties");
        typeProperties.remove(Arrays.asList(fields));
    }

    /**
     * Adds a simple field to a type.
     */
    private void addTypeField(String type, String field, String fieldType) {
        ObjectNode fieldNode = JsonNodeFactory.instance.objectNode();
        fieldNode.put("type", fieldType);
        ObjectNode typeProperties = (ObjectNode) definitions.get(type).get("properties");
        typeProperties.set(field, fieldNode);
    }

    /**
     * Insert the definition for {@link AbstractUser}
     * and add fields (with description) from AbstractUser to {@link OtpUser}.
     * (spark-swagger ignores parent classes when generating swagger.)
     * Note: {@link AdminUser} and {@link ApiUser} are skipped because their endpoints are non-public.
     * TODO: Find a better way to include parent classes.
     */
    private void insertAbstractUserRefs(JsonNode definitionsTemplate) {
        // Insert the AbstractUser type.
        definitions.set(ABSTRACT_USER, definitionsTemplate.get(ABSTRACT_USER));

        ObjectNode abstractUserRefNode = JsonNodeFactory.instance.objectNode();
        abstractUserRefNode.put(REF_FIELD, DEFINITIONS_REF + ABSTRACT_USER);

        // On the OtpUser type, insert an "allOf" entry to include the ref to AbstractUser.
        ObjectNode userNode = (ObjectNode) definitions.get(OTP_USER);
        userNode.put("description", "A user of the trip planner UI");
        ArrayNode allOfNode = JsonNodeFactory.instance.arrayNode()
            .add(abstractUserRefNode.deepCopy())
            .add(userNode);
        ObjectNode newUserNode = JsonNodeFactory.instance.objectNode()
            .set("allOf", allOfNode);
        definitions.set(OTP_USER, newUserNode);
    }

    /**
     * Add and extend the definition of {@link ResponseList} for each endpoint that returns it.
     */
    private void insertResponseListRefs(JsonNode definitionsTemplate) {
        // A ref node for the generic ResponseList node.
        ObjectNode responseListRefNode = JsonNodeFactory.instance.objectNode();
        responseListRefNode.put(REF_FIELD, DEFINITIONS_REF + RESPONSE_LIST);

        // Template node for the data attribute of ResponseList.
        JsonNode dataFieldTemplate = definitionsTemplate.get("ResponseListData");

        TYPES_TO_PATH.forEach((type, path) -> {
            String responseType = String.format("%s<%s>", RESPONSE_LIST, type);

            // Definition for the data field in the returned type
            ObjectNode dataFieldNode = dataFieldTemplate.deepCopy();
            ((ObjectNode) dataFieldNode.at("/properties/data/items")).put(REF_FIELD, DEFINITIONS_REF + type);

            // Add a ResponseList subtype for each.
            ArrayNode allOfNode = JsonNodeFactory.instance.arrayNode()
                .add(responseListRefNode.deepCopy())
                .add(dataFieldNode);
            ObjectNode responseListSubtypeNode = JsonNodeFactory.instance.objectNode()
                .set("allOf", allOfNode);
            definitions.set(responseType, responseListSubtypeNode);

            // Replace the return type with the one created for that type.
            JsonNode pathNode = paths.get(String.format("/api/secure/%s", path));
            JsonNode response200Node = pathNode.at("/get/responses/200");
            ((ObjectNode) response200Node.get("responseSchema")).put(
                REF_FIELD, DEFINITIONS_REF + responseType);
            ((ObjectNode) response200Node.get("schema")).put(
                REF_FIELD, DEFINITIONS_REF + responseType);
        });
    }

    /** Convenience method to get swagger docs as string. */
    static String getSwaggerDocsAsString() {
        return getSwaggerDocs().responseBody;
    }

    /**
     * Get swagger docs from the endpoint provided by spark-swagger.
     * Note: the URL is fixed and is managed by spark-swagger.
     */
    static HttpResponseValues getSwaggerDocs() {
        return HttpUtils.httpRequestRawResponse(
            URI.create(AUTOGENERATED_DOC_URL),
            10,
            HttpMethod.GET,
            null,
            null
        );
    }
}

