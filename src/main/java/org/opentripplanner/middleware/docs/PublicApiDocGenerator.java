package org.opentripplanner.middleware.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.opentripplanner.middleware.utils.YamlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Class that generates the public-facing API documentation.
 */
public class PublicApiDocGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(PublicApiDocGenerator.class);
    private static final HttpClient client = HttpClient.newBuilder().build();
    private static final File apiGatewayDefinitionsFile = new File("src/main/resources/api-swagger-template.yaml");
    private static final String[] PUBLIC_PATHS = new String[] {
        "api/secure/monitoredtrip", "api/secure/triprequests", "api/secure/user", "otp", "pelias"
    };
    public static final String DEFINITIONS_REF = "#/definitions/";
    public static final String $REF_FIELD = "$ref";

    // Cached variables.

    public PublicApiDocGenerator() {

    }

    /**
     * Generate an enhanced public-facing Swagger document from the one auto-generated by spark-swagger.
     * This handles things like adding security parameters, method responses, and removing restricted API resources
     * from the public-facing document.
     */
    public void generatePublicApiDocs() throws IOException, InterruptedException {
        // Start with the swagger YAML skeleton generated by spark-swagger (clone it)
        String autoGeneratedSwagger = getSwaggerDocsAsString();

        // The document we are working off.
        ObjectNode swaggerJson =  YamlUtils.yamlMapper.readTree(autoGeneratedSwagger).deepCopy();

        // Load definitions from a template file.
        JsonNode templateJson = YamlUtils.yamlMapper.readTree(apiGatewayDefinitionsFile);

        // Do the modifications for creating a public API documentation.
        insertPeliasGeocoderEndpoint(swaggerJson, templateJson);
        removeRestrictedPathsAndTags(swaggerJson);
        addAuthorizationParams(swaggerJson, templateJson.get("securityDefinitions"));
        insertMethodResponses(swaggerJson, templateJson.get("responses"));
        insertAbstractUserRefs(swaggerJson, templateJson.get("definitions"));
        inlineArrayDefinitions(swaggerJson);
        // TODO: Add description to the fields of generated types?

        // Some cleanup.
        generateMissingTypes(swaggerJson);
        removeUnusedTypes(swaggerJson);
        alphabetizeEntries((ObjectNode) swaggerJson.get("definitions"));

        // Insert version (the version attribute in spark-swagger.conf is not read by spark-swagger.)
        ((ObjectNode)swaggerJson.get("info"))
            .put("version", templateJson.at("/info/version").asText());

        // Generate output file.
        Path outputPath = new File("target/otp-middleware-swagger.yaml").toPath();
        String yamlOutput = YamlUtils.yamlMapper.writer().writeValueAsString(swaggerJson);
        Files.writeString(outputPath, yamlOutput);
        LOG.info("Wrote API Gateway enhanced Swagger docs to: {}", outputPath);
    }

    /**
     * Inserts a link placeholder to Pelias Geocoder documentation.
     * FIXME: Determine where Pelias API should live and update this method accordingly.
     */
    private void insertPeliasGeocoderEndpoint(ObjectNode rootNode, JsonNode templateNode) {
        final String PELIAS = "pelias";
        JsonNode templatePathsNode = templateNode.get("paths");

        // Insert Pelias geocoder template in the paths.
        String geocoderPath = getFieldNames(templatePathsNode, path -> path.contains(PELIAS)).get(0);
        ObjectNode pathsNode = (ObjectNode) rootNode.get("paths");
        pathsNode.set(geocoderPath, templatePathsNode.get(geocoderPath));

        // Insert geocoder tag.
        for (JsonNode tagNode : templateNode.get("tags")) {
            if (tagNode.get("name").asText().equals(PELIAS)) {
                ((ArrayNode) rootNode.get("tags")).add(tagNode);
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
    private void insertMethodResponses(ObjectNode rootNode, JsonNode responseDefinitions) {
        // Insert the response definitions to the root document.
        rootNode.set("responses", responseDefinitions);

        // Response template that contains a reference to each entry in responseDefinitions,
        // and that will be added to the response section for all methods.
        ObjectNode responseTemplate = JsonNodeFactory.instance.objectNode();
        for (String responseCode : getFieldNames(responseDefinitions)) {
            responseTemplate.set(
                responseCode,
                JsonNodeFactory.instance.objectNode().put($REF_FIELD, "#/responses/" + responseCode)
            );
        }

        // Insert response references in method responses.
        JsonNode pathsNode = rootNode.get("paths");
        for (JsonNode responseNode: pathsNode.findValues("responses")) {
            ((ObjectNode) responseNode).setAll(responseTemplate.deepCopy());
        }
    }

    /**
     * Inserts security definitions, and insert security params to any methods
     * in all /secure/ endpoints.
     */
    private void addAuthorizationParams(ObjectNode rootNode, JsonNode securityDefinitions) {
        // Insert security definitions.
        rootNode.set("securityDefinitions", securityDefinitions);

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

        ObjectNode pathsNode = (ObjectNode) rootNode.get("paths");
        List<String> secureEndpoints = getFieldNames(pathsNode, path -> path.contains("/secure/"));
        for (String endpoint : secureEndpoints) {
            for (JsonNode methodNode : pathsNode.get(endpoint)) {
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
    private void removeRestrictedPathsAndTags(ObjectNode rootNode) {
        // Remove restricted paths.
        ObjectNode pathsNode = (ObjectNode) rootNode.get("paths");
        List<String> pathsToRemove = getFieldNames(pathsNode, this::isRestricted);
        pathsNode.remove(pathsToRemove);

        // Remove restricted tags
        // (actually builds ArrayNode with sorted non-restricted tags).

        ArrayNode tagsNode = (ArrayNode) rootNode.get("tags");
        HashMap<String, JsonNode> remainingTags = new HashMap<>();
        for (JsonNode tagNode : tagsNode) {
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

        tagsNode.removeAll();
        tagsNode.addAll(newTagsNode);
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
    private void inlineArrayDefinitions(ObjectNode rootNode) {
        ObjectNode definitionsNode = (ObjectNode) rootNode.get("definitions");
        final String ARRAY_MARKER = "[]";

        // Find return type references from all "schema" entries.
        for (JsonNode node: rootNode.findValues("schema")) {
            ObjectNode schemaNode = (ObjectNode) node;
            JsonNode refNode = node.get($REF_FIELD);
            if (refNode != null && refNode.asText().endsWith(ARRAY_MARKER)) {
                String baseType = refNode.asText().substring(DEFINITIONS_REF.length()).replace(ARRAY_MARKER, "");

                // Replace the ref node with the inlined array definition, so the schema node becomes:
                //   schema:
                //     type: array
                //     items:
                //       $ref: "#/definitions/MonitoredTrip"
                schemaNode.remove($REF_FIELD);
                schemaNode
                    .put("type", "array")
                    .set("items", JsonNodeFactory.instance.objectNode()
                        .put($REF_FIELD, DEFINITIONS_REF + baseType));

                // Delete the autogenerated array definition.
                definitionsNode.remove(baseType + ARRAY_MARKER);
            }
        }
    }

    /**
     * Generate missing types not created by spark-swagger.
     */
    private void generateMissingTypes(ObjectNode rootNode) {
        ObjectNode definitionsNode = (ObjectNode) rootNode.get("definitions");

        // Find the types from all $ref entries.
        for (JsonNode node: rootNode.findValues($REF_FIELD)) {
            if (node.asText().startsWith(DEFINITIONS_REF)) {
                String baseType = node.asText().substring(DEFINITIONS_REF.length());

                // Add type if it is not defined.
                if (definitionsNode.get(baseType) == null) {
                    JsonNode typeDefinition = JsonNodeFactory.instance.objectNode()
                        .put("type", "object")
                        .put("description", "autogenerated");

                    definitionsNode.set(baseType, typeDefinition);
                }
            }
        }
    }

    /**
     * Removes unused types (not referenced in a $ref entry anywhere) from the definitions section.
     */
    private void removeUnusedTypes(ObjectNode rootNode) {
        ObjectNode definitionsNode = (ObjectNode) rootNode.get("definitions");
        int unusedTypeCount;
        // Execute until there are no unused types.
        do {
            // Find return type references anywhere (only store one instance of each).
            LinkedHashSet<String> typeReferences = new LinkedHashSet<>();
            for (JsonNode refNode : rootNode.findValues($REF_FIELD)) {
                // The reference is typically in the form "#/definitions/TypeName".
                // Extract the "raw" type (TypeName in example above) from the definition string.
                String typeName = refNode.asText().substring(DEFINITIONS_REF.length());
                typeReferences.add(typeName);
            }

            // List types from the definitions node that don't have a reference from the list above.
            // Then remove unused types from the definitions node.
            List<String> typesToRemove = getFieldNames(definitionsNode, type -> !typeReferences.contains(type));
            definitionsNode.remove(typesToRemove);

            unusedTypeCount = typesToRemove.size();
        } while(unusedTypeCount > 0);
    }

    /**
     * Insert the definition for {@link:AbstractUser}
     * and add fields (with description) from AbstractUser to OtpUser.
     * (spark-swagger ignores parent classes when generating swagger.)
     * TODO: Find a better way to include parent classes.
     */
    private void insertAbstractUserRefs(ObjectNode rootNode, JsonNode definitionsTemplate) {
        ObjectNode definitionsNode = (ObjectNode) rootNode.get("definitions");

        // Insert the AbstractUser type.
        definitionsNode.set("AbstractUser", definitionsTemplate.get("AbstractUser"));

        ObjectNode abstractUserRefNode = JsonNodeFactory.instance.objectNode();
        abstractUserRefNode.put($REF_FIELD, DEFINITIONS_REF + "AbstractUser");

        // On the OtpUser type, insert an "allOf" entry to include the ref to AbstractUser.
        ObjectNode userNode = (ObjectNode) definitionsNode.get("OtpUser");
        ArrayNode allOfNode = JsonNodeFactory.instance.arrayNode()
            .add(abstractUserRefNode.deepCopy())
            .add(userNode);
        ObjectNode newUserNode = JsonNodeFactory.instance.objectNode()
            .set("allOf", allOfNode);
        definitionsNode.set("OtpUser", newUserNode);
    }

    /** Convenience method to get swagger docs as string. */
    static String getSwaggerDocsAsString() throws IOException, InterruptedException {
        return getSwaggerDocs().body();
    }

    /**
     * Get swagger docs from the endpoint provided by spark-swagger.
     * Note: the URL is fixed and is managed by spark-swagger.
     */
    static HttpResponse<String> getSwaggerDocs() throws IOException, InterruptedException {
        HttpRequest get = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:4567/doc.yaml"))
            .GET()
            .build();
        return client.send(get, HttpResponse.BodyHandlers.ofString());
    }
}

