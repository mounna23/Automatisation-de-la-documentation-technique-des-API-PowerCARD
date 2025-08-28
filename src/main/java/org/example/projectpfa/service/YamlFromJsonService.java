package org.example.projectpfa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.stereotype.Service;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class YamlFromJsonService {
    static ObjectMapper jsonMapper = new ObjectMapper(); // pour request.json
    static ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public static JsonNode loadJson(String path) throws IOException {
        return jsonMapper.readTree(new File(path));
    }

    public static JsonNode loadYaml(String path) throws IOException {
        return yamlMapper.readTree(new File(path));
    }


public static void fillEmptyFieldsDeep(JsonNode aggregateNode, JsonNode sourceRoot) {
    if (aggregateNode.isObject()) {
        ObjectNode objNode = (ObjectNode) aggregateNode;
        Iterator<Map.Entry<String, JsonNode>> fields = aggregateNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();

            if (key.toLowerCase().endsWith("createcorporate") && key.toLowerCase().contains("list")) {
                // 1️⃣ Calculer le nom "de base"
                String baseKey = key.substring(0, key.length() - "CreateCorporate".length());

                // 2️⃣ Chercher ce noeud "de base" dans le JSON
                JsonNode matchingValue = findValueIgnoreCase(sourceRoot, baseKey);

                // 3️⃣ Ajouter 'example' au noeud actuel sans écraser les autres champs
                if (matchingValue != null && matchingValue.isArray() && value.isObject()) {
                    ObjectNode listNode = ((ObjectNode) aggregateNode).objectNode();
                    listNode.set("example", matchingValue);
                    ((ObjectNode) aggregateNode).set(key, listNode);

                }

            }
        }

        List<String> keys = new ArrayList<>();
        aggregateNode.fieldNames().forEachRemaining(keys::add);

        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);

            JsonNode value = objNode.get(key);
            if (value == null) {
                continue; // déjà supprimé
            }

            if (i < keys.size() - 1) {
                String currBase = key.replaceAll("(?i)list", "");
                String nextKey = keys.get(i + 1);
                String nextBase = nextKey.replaceAll("(?i)list", "");


                if (currBase.equalsIgnoreCase(nextBase)) {
                    objNode.remove(nextKey);
                    continue;
                }
            }

            JsonNode matchingValue = findValueIgnoreCase(sourceRoot, key);
            if (value.isObject()) {
                if (value.has("example") &&
                        value.get("example").isTextual() &&
                        value.get("example").asText().isEmpty() &&
                        matchingValue != null && matchingValue.isValueNode()) {
                    ((ObjectNode) value).put("example", matchingValue.asText());
                }

                fillEmptyFieldsDeep(value, sourceRoot);
            } else if (value.isArray()) {
                for (JsonNode arrayItem : value) {
                    fillEmptyFieldsDeep(arrayItem, sourceRoot);
                }
            } else if (value.isTextual() && value.asText().isEmpty() &&
                    matchingValue != null && matchingValue.isValueNode()) {
                objNode.put(key, matchingValue.asText());
            }
        }
    }
}

private static JsonNode findValueIgnoreCase(JsonNode root, String targetKey) {
    if (root.isObject()) {
        for (Iterator<Map.Entry<String, JsonNode>> it = root.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            if (entry.getKey().equalsIgnoreCase(targetKey)) {
                return entry.getValue();
            }
            JsonNode childResult = findValueIgnoreCase(entry.getValue(), targetKey);
            if (childResult != null) {
                return childResult;
            }
        }
    } else if (root.isArray()) {
        for (JsonNode item : root) {
            JsonNode childResult = findValueIgnoreCase(item, targetKey);
            if (childResult != null) {
                return childResult;
            }
        }
    }
    return null;
}

    public static void processAndSave(String jsonPath, String yamlPath, String outputPath) throws IOException {
        JsonNode requestNode = loadJson(jsonPath);
        JsonNode aggregateNode = loadYaml(yamlPath);

        // Remplir
        fillEmptyFieldsDeep(aggregateNode, requestNode);

        // Sauvegarder
        yamlMapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File(outputPath), aggregateNode);
    }
}
