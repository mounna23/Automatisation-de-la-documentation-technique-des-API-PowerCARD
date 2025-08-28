package org.example.projectpfa.service;

import org.apache.commons.lang3.StringUtils;
import org.example.projectpfa.model.FieldNode;
import org.springframework.stereotype.Service;

import java.beans.Introspector;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.example.projectpfa.service.GeneratorService.*;
import static org.example.projectpfa.service.TreeBuilderService.buildFieldPaths;

@Service
public class YamlGeneratorService {


     static void printYamlAggregate(String aggregatName, List<FieldNode> attributList, List<String> printedMainAggregates) {
        alreadyPrinted=false;
        for(String printedAgg:printedMainAggregates) {
            if(printedAgg.equalsIgnoreCase(aggregatName+"_"+apiName)) {
                alreadyPrinted=true;
                break;
            }
        }
        if(!alreadyPrinted) {
            printValue(aggregatName,"# ---- " + StringUtils.capitalize(aggregatName) + " definition");
            printValue(aggregatName,StringUtils.capitalize(aggregatName) + ":");
            printValue(aggregatName,"      type: object");
            String requiredElements = "";
            boolean requiredElementsExist = false;

            for (FieldNode child : attributList.get(0).getChildren()) {
                if (child.getOccurrence().toLowerCase().contains("required")) {
                    requiredElements = requiredElements + Introspector.decapitalize(child.getFieldName()) + ";";
                    requiredElementsExist = true;
                }
            }
            if (requiredElementsExist) {
                printValue(aggregatName,"      required:");
                for (String requiredElmt : requiredElements.split(";")) {
                    if (requiredElmt.toLowerCase().startsWith(pwcServiceId.toLowerCase())) {
                        requiredElmt = requiredElmt.replace(pwcServiceId, pwcServiceId.toLowerCase());
                    }
                    printValue(aggregatName,"    - " + requiredElmt);
                }
                requiredElements = "";
                requiredElementsExist = false;
            }
            printValue(aggregatName,"      properties:");
            for (FieldNode node : attributList.get(0).getChildren()) {
                recursivePrint(aggregatName, node,printedMainAggregates);
            }
            printedMainAggregates.add(aggregatName+"_"+apiName);
        }

    }

    static void recursivePrint(String aggregatName, FieldNode node,List<String> printedAggregates) {
        Map<String, String> fieldToFullPath = buildFieldPaths(node.getChildren());
        String fieldName;
        String dataType;
        String parent;
        String occurrence;
        String description;
        if(node!=null && !StringUtils.isEmpty(node.getFieldName())) {
            fieldName = Introspector.decapitalize(node.getFieldName());
            dataType= node.getDataType();
            parent= node.getParent();
            occurrence= node.getOccurrence();
            description= node.getFieldDescription();
            if (StringUtils.isNotEmpty(description)) {
                description = description.replaceAll("[\\\r]+", "\r            <br>").replaceAll("[\\\n]+","\n            <br>");
            }

            if (fieldName.toLowerCase().startsWith(pwcServiceId.toLowerCase())) {
                fieldName = fieldName.replace(pwcServiceId, pwcServiceId.toLowerCase());
            }
            if (isComplexType(node.getDataType()) && !aggregatName.equalsIgnoreCase(apiName+"V35Rq") && !aggregatName.equalsIgnoreCase(apiName+"V35Rs")) {
                aggregatName= node.getFieldName() +apiName;
            }
            if (isComplexType(dataType) || !isComplexType(dataType)) {
                boolean subFieldAlreadyPrinted=false;
                for(String printedAgg:printedAggregates) {
                    if(printedAgg.equalsIgnoreCase(fieldName+"_"+parent+"_"+apiName)) {
                        subFieldAlreadyPrinted=true;
                        break;
                    }
                }
                if(!subFieldAlreadyPrinted) {
                    boolean skipObjectDeclaration=false;
                    for(String printedAgg:printedAggregates) {
                        if(printedAgg.equalsIgnoreCase(fieldName+"_"+parent+"_"+apiName)) {
                            skipObjectDeclaration=true;
                            break;
                        }
                    }
                    if(node.getChildren() !=null && node.getChildren().size()>0) {
                        if(!skipObjectDeclaration) {
                            printedAggregates.add(parent+"_"+apiName);
                            printedAggregates.add(fieldName+"_"+apiName);
                            printValue(aggregatName,"    " + fieldName + ":");
                            printValue(aggregatName,"        $ref: 'aggregate.yaml#/" +StringUtils.capitalize(fieldName) + apiName + "'");
                            if(!fieldName.toLowerCase().endsWith("list")) {
//								printValue(aggregatName, fieldName +apiName+ ":");
                            }
                        }

                        if (isComplexType(node.getDataType()) && !node.getFieldName().equalsIgnoreCase(apiName+"V35Rq") && !node.getFieldName().equalsIgnoreCase(apiName+"V35Rs")) {
                            aggregatName= node.getFieldName() +apiName;
                        }
                        subFieldAlreadyPrinted=false;
                        for(String printedAgg:printedAggregates) {
                            if(printedAgg.equalsIgnoreCase(aggregatName+"_"+apiName)) {
                                subFieldAlreadyPrinted=true;
                                break;
                            }
                        }
                        if(!subFieldAlreadyPrinted){

                            printedAggregates.add(aggregatName+"_"+apiName);
                            if(fieldName.toLowerCase().endsWith("list") ) {
                                //printValue(aggregatName,"        $ref: 'aggregate.yaml#/" + StringUtils.capitalize(fieldName).replace("List","").replace("list","") + apiName + "'");
                                printValue(aggregatName,"# ---- " + StringUtils.capitalize(aggregatName) + " definition");
                                printValue(aggregatName,StringUtils.capitalize(aggregatName) + ":");
                                printValue(aggregatName,"      type: array");
                                printValue(aggregatName,"      description: >");
                                printValue(aggregatName,"        " + description);
                                if (occurrence!=null && occurrence.contains("(")) {
                                    String maxItems = occurrence.substring(occurrence.indexOf("(") + 1, occurrence.indexOf(")"));
                                    if(maxItems!=null) {
                                        // Replacing every non-digit character with a space (" ")
                                        maxItems = maxItems.replaceAll("[^\\d]", " ");
                                        // Remove extra spaces from the beginning and the end of the string
                                        maxItems = maxItems.trim();
                                        // Replace all consecutive white spaces with a single space
                                        maxItems = maxItems.replaceAll(" +", " ");
                                        if (!maxItems.equals("")) {
                                            printValue(aggregatName,"      maxItems: "+maxItems);
                                        }
                                    }
                                }
                                String baseFieldName = fieldName;

                                // Cas spécial : "CorporateAddressListCreateCorporate" → "AddressCreateCorporate"
                                // Sinon, comportement générique : suppression de "list" à la fin
                                baseFieldName = fieldName.replaceAll("(?i)list$", "").trim();

                                String refName = StringUtils.capitalize(baseFieldName);
                                printValue(aggregatName,"      items: ");
                                printValue(aggregatName,"        $ref: 'aggregate.yaml#/" + refName + apiName + "'");
                                printedAggregates.add(aggregatName + "_" + apiName);


                            }
                            else {
                                printValue(aggregatName,"# ---- " + StringUtils.capitalize(aggregatName) + " definition");
                                printedAggregates.add(aggregatName+"_"+apiName);
                                printValue(aggregatName,StringUtils.capitalize(aggregatName) + ":");
                                printValue(aggregatName,"  type: object");
                                String requiredElements = "";
                                boolean requiredElementsExist = false;
                                for (FieldNode child : node.getChildren()) {
                                    if (child.getOccurrence().toLowerCase().contains("required")) {
                                        requiredElements = requiredElements + Introspector.decapitalize(child.getFieldName()) + ";";
                                        requiredElementsExist = true;
                                    }
                                }

                                if (requiredElementsExist) {
                                    printValue(aggregatName,"  required:");
                                    for (String requiredElmt : requiredElements.split(";")) {
                                        if (requiredElmt.toLowerCase().startsWith(pwcServiceId.toLowerCase())) {
                                            requiredElmt = requiredElmt.replace(pwcServiceId, pwcServiceId.toLowerCase());
                                        }
                                        printValue(aggregatName,"    - " + requiredElmt);

                                    }
                                    requiredElements = "";
                                    requiredElementsExist = false;
                                }
                                printValue(aggregatName,"  properties:");
                                Map<String,FieldNode> subComplexTypes = new LinkedHashMap<String,FieldNode>();
                                for(FieldNode child: node.getChildren()) {
                                    if(isComplexType(child.getDataType())){
                                        String refName = StringUtils.capitalize(child.getFieldName()) + apiName;
//                                    if (child.fieldName.equalsIgnoreCase(child.parent.replace("List", "").replace("list", ""))) {
//                                        continue;
//                                    }
                                        // Écriture du $ref dans la propriété
                                        printValue(aggregatName, "    " + child.getFieldName() + ":");
                                        printValue(aggregatName, "        $ref: 'aggregate.yaml#/" + refName + "'");
                                    }
                                    subFieldAlreadyPrinted=false;
                                    fieldName = Introspector.decapitalize(child.getFieldName());
                                    dataType= child.getDataType();
                                    parent= child.getParent();
                                    occurrence= child.getOccurrence();
                                    description= child.getFieldDescription();
                                    if (StringUtils.isNotEmpty(description)) {
                                        description = description.replaceAll("[\\\r]+", "\r            <br>").replaceAll("[\\\n]+","\n            <br>");
                                    }

                                    if (fieldName.toLowerCase().startsWith(pwcServiceId.toLowerCase())) {
                                        fieldName = fieldName.replace(pwcServiceId, pwcServiceId.toLowerCase());
                                    }
                                    if (!child.getParent().equalsIgnoreCase("-") && !child.getFieldName().equalsIgnoreCase(apiName+"V35Rq") && !child.getFieldName().equalsIgnoreCase(apiName+"V35Rs")) {
                                        aggregatName= child.getFieldName() +apiName;
                                    }
                                    for (String elemnt : printedAggregates) {
                                        if(elemnt.equalsIgnoreCase(fieldName+"_"+parent+"_"+apiName)) {
                                            subFieldAlreadyPrinted=true;
                                            break;
                                        }
                                    }
                                    if(!subFieldAlreadyPrinted) {
                                        if (isComplexType(dataType)) {
                                            skipObjectDeclaration = false;
                                            for (String printedAgg : printedAggregates) {
                                                if (printedAgg.equalsIgnoreCase(parent + "_" + apiName)) {
                                                    skipObjectDeclaration = true;
                                                    break;
                                                }
                                            }
                                            if (!skipObjectDeclaration) {
                                                printValue(aggregatName, "    " + fieldName + ":");
                                                printedAggregates.add(parent + "_" + apiName);
                                                if (fieldName.toLowerCase().endsWith("list")) {
                                                    printValue(aggregatName, "      type: array");
                                                    printValue(aggregatName, "      description: >");
                                                    printValue(aggregatName, "        " + description);
                                                    if (occurrence != null && occurrence.contains("(")) {
                                                        String maxItems = occurrence.substring(occurrence.indexOf("(") + 1, occurrence.indexOf(")"));
                                                        if (maxItems != null) {
                                                            // Replacing every non-digit character with a space (" ")
                                                            maxItems = maxItems.replaceAll("[^\\d]", " ");
                                                            // Remove extra spaces from the beginning and the end of the string
                                                            maxItems = maxItems.trim();
                                                            // Replace all consecutive white spaces with a single space
                                                            maxItems = maxItems.replaceAll(" +", " ");
                                                            if (!maxItems.equals("")) {
                                                                printValue(aggregatName, "      maxItems: " + maxItems);
                                                            }
                                                        }
                                                    }
                                                    printValue(aggregatName, "      items: ");
                                                    //  printValue(aggregatName,"        $ref: 'aggregate.yaml#/" + StringUtils.capitalize(fieldName).replace("List","").replace("list","") + apiName + "'");
                                                } else {
                                                    printValue(aggregatName, "      $ref: 'aggregate.yaml#/" + StringUtils.capitalize(fieldName) + apiName + "'");
                                                }
                                            }
                                        } else {
                                            printValue(aggregatName, "    " + fieldName + ":");
                                            printValue(aggregatName, "      type: string");
                                            if (fieldName.toLowerCase().equalsIgnoreCase("resultID")) {
                                                printValue(aggregatName, "      enum: [ProceedWithSuccess, ProceedWithSuccessMC, Error, SystemError]");
                                            } else {
                                                if (dataType.toLowerCase().contains("(")) {
                                                    printValue(aggregatName, "      maxLength: "
                                                            + dataType.substring(dataType.indexOf("(") + 1, dataType.indexOf(")")));
                                                } else if (dataType.toLowerCase().contains("date")) {
                                                    printValue(aggregatName, "      format: date-time");
                                                }
                                            }
                                            printValue(aggregatName, "      description: >");
                                            printValue(aggregatName, "        " + description);

                                            if (!dataType.toLowerCase().contains("date")) {//ne pas générer exmeple data pr les champs de type date pr qu'il prend sysdate par defaut
                                                printValue(aggregatName, "      example: '"
//                                                        + getJsonSampleData(apiName, apiVersion, Introspector.decapitalize(parent), child, fieldToFullPath)
                                                        + "'");
                                            }
                                        }}}

                            }

                            String requiredElements = "";
                            boolean requiredElementsExist = false;
                            for (FieldNode child : node.getChildren()) {
                                if (child.getOccurrence().toLowerCase().contains("required")) {
                                    requiredElements = requiredElements + Introspector.decapitalize(child.getFieldName()) + ";";
                                    requiredElementsExist = true;
                                }
                            }

                            if (requiredElementsExist) {
                                // printValue(aggregatName,"  required:");
                                for (String requiredElmt : requiredElements.split(";")) {
                                    if (requiredElmt.toLowerCase().startsWith(pwcServiceId.toLowerCase())) {
                                        requiredElmt = requiredElmt.replace(pwcServiceId, pwcServiceId.toLowerCase());
                                    }
                                    //  printValue(aggregatName,"    - " + requiredElmt);

                                }
                                requiredElements = "";
                                requiredElementsExist = false;
                            }
                            // printValue(aggregatName,"  properties:");
                            Map<String,FieldNode> subComplexTypes = new LinkedHashMap<String,FieldNode>();
                            for(FieldNode child: node.getChildren()) {
                                if(isComplexType(child.getDataType())){
                                    String refName = StringUtils.capitalize(child.getFieldName()) + apiName;
//                                    if (child.fieldName.equalsIgnoreCase(child.parent.replace("List", "").replace("list", ""))) {
//                                        continue;
//                                    }
                                    // Écriture du $ref dans la propriété
//                                    printValue(aggregatName, "    " + child.fieldName + ":");
//                                    printValue(aggregatName, "        $ref: 'aggregate.yaml#/najlaa" + refName + "'");
                                }
                                subFieldAlreadyPrinted=false;
                                fieldName = Introspector.decapitalize(child.getFieldName());
                                dataType= child.getDataType();
                                parent= child.getParent();
                                occurrence= child.getOccurrence();
                                description= child.getFieldDescription();
                                if (StringUtils.isNotEmpty(description)) {
                                    description = description.replaceAll("[\\\r]+", "\r            <br>").replaceAll("[\\\n]+","\n            <br>");
                                }

                                if (fieldName.toLowerCase().startsWith(pwcServiceId.toLowerCase())) {
                                    fieldName = fieldName.replace(pwcServiceId, pwcServiceId.toLowerCase());
                                }
                                if (!child.getParent().equalsIgnoreCase("-") && !child.getFieldName().equalsIgnoreCase(apiName+"V35Rq") && !child.getFieldName().equalsIgnoreCase(apiName+"V35Rs")) {
                                    aggregatName= child.getFieldName() +apiName;
                                }
                                for (String elemnt : printedAggregates) {
                                    if(elemnt.equalsIgnoreCase(fieldName+"_"+parent+"_"+apiName)) {
                                        subFieldAlreadyPrinted=true;
                                        break;
                                    }
                                }
                                if(!subFieldAlreadyPrinted) {
                                    if (isComplexType(dataType)) {
                                        skipObjectDeclaration=false;
                                        for(String printedAgg:printedAggregates) {
                                            if(printedAgg.equalsIgnoreCase(parent+"_"+apiName)) {
                                                skipObjectDeclaration=true;
                                                break;
                                            }
                                        }
                                        if(!skipObjectDeclaration) {
//                                            printValue(aggregatName,"    " + fieldName + ":");
//                                            printedAggregates.add(parent+"_"+apiName);
                                            if(fieldName.toLowerCase().endsWith("list")) {
//                                                printValue(aggregatName,"      type: array");
//                                                printValue(aggregatName,"      description: >");
//                                                printValue(aggregatName,"        " + description);
                                                if (occurrence!=null && occurrence.contains("(")) {
                                                    String maxItems = occurrence.substring(occurrence.indexOf("(") + 1, occurrence.indexOf(")"));
                                                    if(maxItems!=null) {
                                                        // Replacing every non-digit character with a space (" ")
                                                        maxItems = maxItems.replaceAll("[^\\d]", " ");
                                                        // Remove extra spaces from the beginning and the end of the string
                                                        maxItems = maxItems.trim();
                                                        // Replace all consecutive white spaces with a single space
                                                        maxItems = maxItems.replaceAll(" +", " ");
                                                        if (!maxItems.equals("")) {
                                                            printValue(aggregatName,"      maxItems: "+maxItems);
                                                        }
                                                    }
                                                }
//                                                printValue(aggregatName,"      items: ");
                                                //  printValue(aggregatName,"        $ref: 'aggregate.yaml#/" + StringUtils.capitalize(fieldName).replace("List","").replace("list","") + apiName + "'");
                                            }else {
//                                                printValue(aggregatName,"      $ref: 'aggregate.yaml#/" + StringUtils.capitalize(fieldName) + apiName + "'");
                                            }
                                        }
//                                    }else {
//                                        printValue(aggregatName,"    " + fieldName + ":");
//                                        printValue(aggregatName,"      type: string");
                                        if (fieldName.toLowerCase().equalsIgnoreCase("resultID")) {
//                                            printValue(aggregatName,"      enum: [ProceedWithSuccess, ProceedWithSuccessMC, Error, SystemError]");
                                        } else {
                                            if (dataType.toLowerCase().contains("(")) {
//                                                printValue(aggregatName,"      maxLength: "
//                                                        + dataType.substring(dataType.indexOf("(") + 1, dataType.indexOf(")")));
                                            } else if (dataType.toLowerCase().contains("date")) {
//                                                printValue(aggregatName,"      format: date-time");
                                            }
                                        }
//                                        printValue(aggregatName,"      description: >");
//                                        printValue(aggregatName,"        " + description);

                                        if (!dataType.toLowerCase().contains("date")) {//ne pas générer exmeple data pr les champs de type date pr qu'il prend sysdate par defaut
//                                            printValue(aggregatName,"      example: '"
//                                                    + getJsonSampleData(apiName, apiVersion, Introspector.decapitalize(parent), child,fieldToFullPath)
//                                                    + "'");
                                        }

                                    }
                                    if(child.getChildren() !=null && child.getChildren().size()>0)
                                        subComplexTypes.put(child.getFieldName(), child);
                                }
                            }


                            if(subComplexTypes!=null && subComplexTypes.size()>0) {
                                for (Map.Entry<String, FieldNode> entry : subComplexTypes.entrySet()) {
                                    recursivePrint(entry.getKey(), entry.getValue(),printedAggregates);

                                }
                                subComplexTypes = new LinkedHashMap<String,FieldNode>();
                            }

                        }

                    }else {

                        printValue(aggregatName,"    " + fieldName + ":");

                        if(fieldName.toLowerCase().endsWith("list")) {
                            printValue(aggregatName,"      type: array");
                            printValue(aggregatName,"      description: >");
                            printValue(aggregatName,"        " + description);
                            if (occurrence!=null && occurrence.contains("(")) {
                                String maxItems = occurrence.substring(occurrence.indexOf("(") + 1, occurrence.indexOf(")"));
                                if(maxItems!=null) {
                                    // Replacing every non-digit character with a space (" ")
                                    maxItems = maxItems.replaceAll("[^\\d]", " ");
                                    // Remove extra spaces from the beginning and the end of the string
                                    maxItems = maxItems.trim();
                                    // Replace all consecutive white spaces with a single space
                                    maxItems = maxItems.replaceAll(" +", " ");
                                    if (!maxItems.equals("")) {
                                        printValue(aggregatName,"      maxItems: "+maxItems);
                                    }
                                }
                            }
                            printValue(aggregatName,"      items: ");
                            //printValue(aggregatName,"        $ref: 'aggregate.yaml#/" + StringUtils.capitalize(fieldName).replace("List","").replace("list","") + apiName + "'");
                        }else {
                            printValue(aggregatName,"      type: string");
                            if (fieldName.toLowerCase().equalsIgnoreCase("resultID")) {
                                printValue(aggregatName,"      enum: [ProceedWithSuccess, ProceedWithSuccessMC, Error, SystemError]");
                            } else {
                                if (dataType.toLowerCase().contains("(")) {
                                    printValue(aggregatName,"      maxLength: "
                                            + dataType.substring(dataType.indexOf("(") + 1, dataType.indexOf(")")));
                                } else if (dataType.toLowerCase().contains("date")) {
                                    printValue(aggregatName,"      format: date-time");
                                }
                            }
                            printValue(aggregatName,"      description: >");
                            printValue(aggregatName,"        " + description);

                            if (!dataType.toLowerCase().contains("date")) {//ne pas générer exmeple data pr les champs de type date pr qu'il prend sysdate par defaut
                                printValue(aggregatName,"      example: '"
                                        +  "'");
                            }
                        }
                    }
                }
            } else {
                printValue(aggregatName,"    " + fieldName + ":");
                printValue(aggregatName,"      type: string");
                if (fieldName.toLowerCase().equalsIgnoreCase("resultID")) {
                    printValue(aggregatName,"      enum: [ProceedWithSuccess, ProceedWithSuccessMC, Error, SystemError]");
                } else {
                    if (dataType.toLowerCase().contains("(")) {
                        printValue(aggregatName,"      maxLength: "
                                + dataType.substring(dataType.indexOf("(") + 1, dataType.indexOf(")")));
                    } else if (dataType.toLowerCase().contains("date")) {
                        printValue(aggregatName,"      format: date-time");
                    }
                }
                printValue(aggregatName,"      description: >");
                printValue(aggregatName,"        " + description);

                if (!dataType.toLowerCase().contains("date")) {//ne pas générer exmeple data pr les champs de type date pr qu'il prend sysdate par defaut
                    printValue(aggregatName,"      example: '"
//                            + getJsonSampleData(apiName, apiVersion, Introspector.decapitalize(parent),node,fieldToFullPath)
                            + "'");
                }

            }

        }
    }


    public static void printValue(String aggregatName, String value) {
        try {
            if (aggregatName.equalsIgnoreCase(apiName + "V35Rq")) {
                writer = new PrintWriter(new BufferedWriter(new FileWriter(yamlOutputPath + "request.yaml", true)));
            } else if (aggregatName.equalsIgnoreCase(apiName + "V35Rs")) {
                writer = new PrintWriter(new BufferedWriter(new FileWriter(yamlOutputPath + "response.yaml", true)));
            } else if (aggregatName.equalsIgnoreCase("DB")) {
                writer = new PrintWriter(new BufferedWriter(
                        new FileWriter(apiCodeGenPath + "/" + apiName + "/" + apiName + "_codeSQL.sql", true)));
            } else if (aggregatName.equalsIgnoreCase("API")) {
                writer = new PrintWriter(new BufferedWriter(
                        new FileWriter(apiCodeGenPath + "/" + apiName + "/" + apiName + "_codeJAVA.txt", true)));
            } else {//aggregate
                writer = new PrintWriter(new BufferedWriter(new FileWriter(yamlOutputPath + "aggregate.yaml", true)));
            }
            printValue(value, false);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException("Erreur d'écriture YAML : " + e.getMessage(), e);
        }
    }

    public static void printValue(String value, boolean useTabulation) {
        try {
            String tabulation = useTabulation ? tabulation = "    " : "";
            writer.write(tabulation + value + "\n");
        } catch (IOException ex) {
            // Report
        }
    }
    public static String getDefaultModuleDescriptor() {
        return "{\r\n" + "  \"modules\":[\r\n" + "    {\r\n" + "      \"moduleName\":\"Api basics\",\r\n"
                + "      \"expanded\": false,\r\n" + "      \"apis\":[\r\n" + "        {\r\n"
                + "          \"name\":\"Authentication\",\r\n" + "          \"file\": \"tokenAuthentication.yaml\",\r\n"
                + "          \"subApiList\": [\r\n" + "            {\r\n"
                + "              \"file\": \"tokenAuthentication.yaml\",\r\n"
                + "              \"name\": \"Authentication\",\r\n" + "              \"HttpVerb\": \"POST\"\r\n"
                + "            }]\r\n" + "        },\r\n" + "        {\r\n"
                + "          \"name\":\"Sorting and Pagination\",\r\n" + "          \"file\": \"paging.html\",\r\n"
                + "          \"staticContent\": true\r\n" + "        },\r\n" + "        {\r\n"
                + "          \"name\":\"Headers\",\r\n" + "          \"file\": \"headers.html\",\r\n"
                + "          \"staticContent\": true\r\n" + "        },\r\n" + "        {\r\n"
                + "          \"name\":\"Errors\",\r\n" + "          \"file\": \"error.html\",\r\n"
                + "          \"staticContent\": true\r\n" + "        },\r\n" + "        {\r\n"
                + "          \"name\":\"Versioning\",\r\n" + "          \"file\": \"versioning.html\",\r\n"
                + "          \"staticContent\": true\r\n" + "        }\r\n" + "      ]\r\n" + "    },\r\n" + "    {\r\n"
                + "      \"moduleName\": \"[PWC_SERVICE_ID]\",\r\n" + "      \"expanded\": true,\r\n"
                + "      \"apis\": [";
    }

    public static String getDefaultRequest() {
        return "# ---- TokenAuthentication definition\r\n" + "TokenAuthentication:\r\n" + "  type: object\r\n"
                + "  properties:\r\n" + "    providerLogin:\r\n" + "      type: string\r\n"
                + "      example: 'firstUser'\r\n" + "    providerPassword:\r\n" + "      type: string\r\n"
                + "      example: '$2a$10$AnRf8HJwhDOgvM/7PqXkNOnbyebzUPJFiFvN8wLDoLkKaYAK0dS1e'\r\n"
                + "    userLanguage:\r\n" + "      type: string\r\n" + "      example: 'en_US'\r\n"
                + "    requestInfo:\r\n" + "      type: object\r\n" + "      $ref: 'aggregate.yaml#/RequestInfo'\r\n"
                + "\r\n";
    }

    public static String getDefaultResponse() {
        return "# ---- TokenAuthenticationResponse response definition\r\n" + "TokenAuthenticationResponse:\r\n"
                + "  type: object\r\n" + "  properties:\r\n" + "    token:\r\n" + "      type: string\r\n"
                + "      description: <p>The access JWT Token </p>\r\n" + "    responseInfo:\r\n"
                + "      type: object\r\n" + "      $ref: 'aggregate.yaml#/ResponseInfo'\r\n" + "\r\n";
    }

    public static String getDefaultAggregate() {
        return "# ---- KeyValueV35\r\n" + "KeyValueV35:\r\n" + "  type: object\r\n" + "  properties:\r\n"
                + "    key:\r\n" + "      type: string\r\n" + "      example: ''\r\n" + "    data:\r\n"
                + "      type: string\r\n" + "      example: ''\r\n" + "    type:\r\n" + "      type: string\r\n"
                + "      example: ''\r\n" + "ResponseInfo:\r\n" + "  required:\r\n" + "    - responseUID\r\n"
                + "    - resultID\r\n" + "    - errorCode\r\n" + "  type: object\r\n" + "  properties:\r\n"
                + "    responseUID:\r\n" + "      type: string\r\n" + "      example: ''\r\n"
                + "      description: <p>Response Identifier. Echoed back by PowerCARD to the requester. It should contain the same value as the one sent in the request message.\r\n"
                + "        </p>\r\n" + "    resultID:\r\n" + "      type: string\r\n" + "      enum:\r\n"
                + "        - ProceedWithSuccess\r\n" + "        - ProceedWithSuccessMC\r\n" + "        - Error\r\n"
                + "        - SystemError\r\n"
                + "      description: <p>Refers to the result of processing in PowerCARD.\r\n"
                + "        The possible values are:<b>\r\n"
                + "        ProceedWithSuccess:<b> Call was ended successfully.\r\n"
                + "        ProceedWithSuccessMC:<b> Call made successfully, however the request will be inserted in a Maker Checker queue for further analysis.\r\n"
                + "        Error:<b> An error has occurred during the processing.\r\n"
                + "        SystemError:<b> An unknown system error occurred during the process. </p>\r\n"
                + "    errorCode:\r\n" + "      type: string\r\n" + "      example: ''\r\n"
                + "      description: <p>Indicate the error code used by the PowerCARD to tell you that program was experiencing a particular problem during the processing of the request.</p>\r\n"
                + "    errorDescription:\r\n" + "      type: string\r\n" + "      example: ''\r\n"
                + "      description: <p>If present, this field must contain the description of the error encountered</p>\r\n"
                + "RequestInfo:\r\n" + "  required:\r\n" + "    - requestUID\r\n" + "    - requestDate\r\n"
                + "  type: object\r\n" + "  properties:\r\n" + "    requestUID:\r\n" + "      type: string\r\n"
                + "      example: 'firstUser01'\r\n"
                + "      description: <p>Request Identifier. It is sent by the calling system as a universally unique identifier for the message. Used to match response with request messages.\r\n"
                + "        The generation mask should be the following:<b>\r\n"
                + "        XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX\r\n" + "        Example:<b>\r\n"
                + "        6948DF80-14BD-4E04-8842-7668D9C001F5\r\n" + "        </p>\r\n" + "    requestDate:\r\n"
                + "      type: string\r\n" + "      format: date-time\r\n"
                + "      description: <p>Date when the request was submitted</p>\r\n" + "    userID:\r\n"
                + "      type: string\r\n" + "      example: 'firstUser'\r\n"
                + "      description: <p>Represents the user Id who made the request.</p>\r\n";
    }

    public static String getDefaultHeader() {
        return "openapi: \"3.0.3\"\n" + "info:\n" + "  title: [API_MODULE] API documentation\n" + "  description: |\n"
                + "          [PWC_SERVICE_OVERVIEW]\n" + "  version: [PWC_RELEASE]\n" + "servers:\n"
                + "  - url: ${sandbox.backend.connectApiUrl}/rest\n" + "    description: Development server\n"
                + "paths:\n" + "  # --- /[API_NAME]/[API_VERSION]\n" + "  /[API_NAME]/[API_VERSION]:\n"
                + "    [API_HTTP_METHOD_lower]:\n" + "      description: |\r\n" + "        <h3>API Overview</h3>\r\n"
                + "          <p>[API_OVERVIEW]</p>\r\n" + "        \r\n" + "        <h3>Functional Description</h3>\r\n"
                + "        <img src=\"./docs/[PWC_SERVICE_ID]/[API_NAME].png\" />\r\n" + "      summary: |\r\n"
                + "          <p>[API_OVERVIEW]</p>\r\n" + "        \r\n" + "      tags:\n" + "        - [API_PRINTED_NAME]\n"
                + "      requestBody:\n" + "        required: true\n" + "        content:\n"
                + "          application/json:\n" + "            schema:\n"
                + "              $ref: '#/components/schemas/[API_NAME]V35Rq'\n" + "          application/xml:\n"
                + "            schema:\n" + "              $ref: '#/components/schemas/[API_NAME]V35Rq'\n"
                + "      responses:\n" + "        200:\n" + "          description: |\n" + "            Successfull\n"
                + "            Business Error Codes:\n" + "            <table>\n" + "[API_ERROR_LIST]"
                + "            </table>\n" + "          content:\n" + "            application/json:\n"
                + "              schema:\n" + "                  $ref: '#/components/schemas/[API_NAME]V35Rs'\n"
                + "            application/xml:\n" + "              schema:\n"
                + "                  $ref: '#/components/schemas/[API_NAME]V35Rs'\n" + "components:\n"
                + "  securitySchemes:\n" + "    bearerAuth:\n" + "      type: http\n" + "      scheme: bearer\n"
                + "      bearerFormat: JWT\n" + "      description: |\n" + "        <div>\n"
                + "          <h5>Api key authorization</h5>\n"
                + "          <p>JWT authorization header using Bearer scheme. Example: \"Authorization: Bearer {token}\"</p>\n"
                + "          <table>\n" + "            <tr><td>Name:</td><td>Authorization</td></tr>\n"
                + "            <tr><td>In:</td><td>Header</td></tr>\n" + "          </table>\n" + "        </div>\n"
                + "  schemas:\n" + "    # --- Import [API_NAME] request and response\n" + "    [API_NAME]V35Rq:\n"
                + "      $ref: 'request.yaml#/[API_NAME]V35Rq'\n" + "    [API_NAME]V35Rs:\n"
                + "      $ref: 'response.yaml#/[API_NAME]V35Rs'\n" + "security:\n" + "  - bearerAuth: []";
    }
}
