package org.example.projectpfa.source;

import org.example.projectpfa.model.FieldNode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.util.FileSystemUtils;

import java.beans.Introspector;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
@Service("ConfluenceInputSource")
public class ConfluenceInputSource implements InputSource {


    @Override
    public void process(MultipartFile file, boolean enableDebug, String apiRestrictedList) throws Exception {

    }


        static String inputFileList = "/Users/ed-daoudisalma/Documents/repo/CA3-EnrollCardOnVFC-190825-161923.pdf";
        static String inputPath = "";
        static String outputPath = "";
        static String yamlOutputPath=null;
        static String pptOutputPath=null;
        static boolean enableDebugLog = false;

        public static String staticTemplateDataPath = inputPath + "/Users/ed-daoudisalma/Documents/repo/template/static/";
        public static boolean generateCodeEnabled = false;
        public static boolean generateYamlEnabled = true;
        public static boolean isFirstApi = true;
        public static boolean lastInsertOnModuleDescriptor = false;
        public static String simpleTypes = "c (;c(;n(;n (;date;datetime;num;numeric;double;long;boolean;time;alpha;alphanumeric";

        public static String apiCodeGenPath = null;

        public static String imgOutputPath = null;
        public static String pwcRelease = "";
        public static String pwcModule = "";
        public static String pwcService = "";
        public static String pwcServiceId = "";
        public static String pwcServiceOverview = "";
        public static String apiName = "";
        public static String apiHttpMethod = "";
        public static String apiVersion = "";
        public static String apiOverview = "";
        public static String apiResources = "";
        public static Boolean alreadyPrinted = false;

        public static Map<String, String> mapApiDBTypes = new HashMap<>();

        public static void main(String[] args) {

            try {
                String dateFormat = "yyyyMMdd-HHmmss";
                SimpleDateFormat formatter = new SimpleDateFormat(dateFormat);

                if (args != null && args.length > 0) {
                    inputFileList = args[0];
                    if (args.length > 1) {
                        enableDebugLog = StringUtils.equalsIgnoreCase("true", args[1]);
                    }
                    outputPath = "./APIGenerator-Results/" + formatter.format(new Date()) + "/";
                    inputPath = "./";
                } else { // Exécution depuis IDE
                    // Fix: Use proper path construction
                    outputPath = System.getProperty("user.dir") + File.separator + "APIGenerator-Results"
                            + File.separator + formatter.format(new Date()) + File.separator;
                    enableDebugLog = true;
                }

                if (StringUtils.isNotEmpty(inputFileList) && !inputFileList.contains(";"))
                    inputFileList += ";";

                String[] filesList = inputFileList.split(";");
                if (filesList != null && filesList.length > 0) {
                    for (String inputFileName : filesList) {
                        String filePath = inputPath + inputFileName;

                        // Fix: Create output directories first
                        File outputDir = new File(outputPath);
                        if (!outputDir.exists()) {
                            outputDir.mkdirs();
                        }

                        yamlOutputPath = outputPath + "schemas/";
                        pptOutputPath = outputPath + "ppt/";

                        new File(yamlOutputPath).mkdirs();
                        new File(pptOutputPath).mkdirs();

                        System.out.println("##################################################");
                        System.out.println("### ICD File   : " + filePath);
                        extractApiMetadataFromPdf(filePath);
                        // Extraction des champs en FieldNode
                        List<FieldNode> fields = extractFieldsFromPdf(filePath);
                        generateYaml(fields, yamlOutputPath);
                        runGeneration(inputFileName,true,true);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * Extraction des champs du PDF en FieldNode
         */
        public static List<FieldNode> extractFieldsFromPdf(String filePath) {
            List<FieldNode> fieldNodes = new ArrayList<>();
            try {
                PDDocument document = PDDocument.load(new File(filePath));
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                document.close();

                // Nettoyer le texte
                text = text.replaceAll("([a-zA-Z])\\s*\\n\\s*([a-zA-Z])", "$1$2");

                // Trouver la section des champs de requête
                Pattern requestSectionPattern = Pattern.compile("Request Message.*?Response Message", Pattern.DOTALL);
                Matcher requestSectionMatcher = requestSectionPattern.matcher(text);

                if (requestSectionMatcher.find()) {
                    String requestSection = requestSectionMatcher.group();
                    System.out.println("=== REQUEST SECTION FOUND ===");

                    // Pattern pour les champs de requête
                    Pattern fieldPattern = Pattern.compile(
                            "(\\d+)\\s+(\\w+)\\s+([A-Za-z0-9()]+)\\s+([A-Za-z0-9]*)\\s+([A-Za-z()]+)\\s*(.*?)(?=\\d+\\s+\\w+|#|$)",
                            Pattern.DOTALL
                    );

                    Matcher fieldMatcher = fieldPattern.matcher(requestSection);

                    while (fieldMatcher.find()) {
                        String number = fieldMatcher.group(1).trim();
                        String fieldName = fieldMatcher.group(2).trim();
                        String dataType = fieldMatcher.group(3).trim();
                        String parent = fieldMatcher.group(4).trim();
                        String occurrence = fieldMatcher.group(5).trim();
                        String description = fieldMatcher.group(6).trim();

                        // Nettoyer la description
                        description = description.replaceAll("\\s+", " ").trim();

                        if ("required".equalsIgnoreCase(occurrence) || "mandatory".equalsIgnoreCase(occurrence)
                                || occurrence.toLowerCase().contains("required")) {
                            FieldNode node = new FieldNode(fieldName, dataType, parent, occurrence, description);
                            fieldNodes.add(node);
                            System.out.println("✅ Request field: " + fieldName + " (Parent: " + parent + ", Type: " + dataType + ")");
                        }
                    }
                }

                // Trouver la section des champs de réponse
                Pattern responseSectionPattern = Pattern.compile("Response Message.*?Specific error codes", Pattern.DOTALL);
                Matcher responseSectionMatcher = responseSectionPattern.matcher(text);

                if (responseSectionMatcher.find()) {
                    String responseSection = responseSectionMatcher.group();
                    System.out.println("=== RESPONSE SECTION FOUND ===");

                    // Pattern pour les champs de réponse
                    Pattern fieldPattern = Pattern.compile(
                            "(\\d+)\\s+(\\w+)\\s+([A-Za-z0-9()]+)\\s+([A-Za-z0-9]*)\\s+([A-Za-z()]+)\\s*(.*?)(?=\\d+\\s+\\w+|#|$)",
                            Pattern.DOTALL
                    );

                    Matcher fieldMatcher = fieldPattern.matcher(responseSection);

                    while (fieldMatcher.find()) {
                        String number = fieldMatcher.group(1).trim();
                        String fieldName = fieldMatcher.group(2).trim();
                        String dataType = fieldMatcher.group(3).trim();
                        String parent = fieldMatcher.group(4).trim();
                        String occurrence = fieldMatcher.group(5).trim();
                        String description = fieldMatcher.group(6).trim();

                        description = description.replaceAll("\\s+", " ").trim();

                        if ("required".equalsIgnoreCase(occurrence) || "mandatory".equalsIgnoreCase(occurrence)
                                || occurrence.toLowerCase().contains("required")) {
                            FieldNode node = new FieldNode(fieldName, dataType, parent, occurrence, description);
                            fieldNodes.add(node);
                            System.out.println("✅ Response field: " + fieldName + " (Parent: " + parent + ", Type: " + dataType + ")");
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return fieldNodes;
        }
        public static void generateYaml(List<FieldNode> fields, String outputPath) {
            Map<String, List<FieldNode>> groupedByParent = new LinkedHashMap<>();

            // Grouper par parent
            for (FieldNode f : fields) {
                String parent = StringUtils.isNotBlank(f.getParent()) ? f.getParent() : "Root";
                groupedByParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(f);
            }

            for (Map.Entry<String, List<FieldNode>> entry : groupedByParent.entrySet()) {
                String parentName = entry.getKey();
                List<FieldNode> parentFields = entry.getValue();

                StringBuilder yaml = new StringBuilder();
                yaml.append(parentName).append(":\n");
                yaml.append("  type: object\n");

                // Liste des champs requis
                List<String> requiredFields = parentFields.stream()
                        .filter(f -> "required".equalsIgnoreCase(f.getOccurrence()))
                        .map(f -> decapitalize(f.getFieldName()))
                        .collect(Collectors.toList());

                if (!requiredFields.isEmpty()) {
                    yaml.append("  required:\n");
                    for (String field : requiredFields) {
                        yaml.append("    - ").append(field).append("\n");
                    }
                }

                // Propriétés
                yaml.append("  properties:\n");
                for (FieldNode f : parentFields) {
                    String fieldName = decapitalize(f.getFieldName());
                    String dataType = mapDataType(f.getDataType());

                    yaml.append("    ").append(fieldName).append(":\n");
                    yaml.append("      type: ").append(dataType).append("\n");

                    // Format pour les dates
                    if (f.getDataType().toLowerCase().contains("date")) {
                        yaml.append("      format: date-time\n");
                    }

                    // Enum pour ResultID
                    if ("ResultID".equalsIgnoreCase(f.getFieldName())) {
                        yaml.append("      enum:\n");
                        yaml.append("        - ProceedWithSuccess\n");
                        yaml.append("        - ProceedWithSuccessMC\n");
                        yaml.append("        - Error\n");
                        yaml.append("        - SystemError\n");
                    }

                    // Description
                    if (StringUtils.isNotBlank(f.getFieldDescription())) {
                        yaml.append("      description: |\n");
                        yaml.append("        ").append(f.getFieldDescription().trim()).append("\n");
                    }

                    // Exemple pour les champs non-date
                    if (!f.getDataType().toLowerCase().contains("date")) {
                        yaml.append("      example: 'example_").append(fieldName).append("'\n");
                    }
                }

                // Écrire le fichier
                try {
                    File file = new File(outputPath + parentName + ".yaml");
                    Files.createDirectories(file.getParentFile().toPath());

                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(yaml.toString());
                    }

                    System.out.println("✅ YAML généré : " + file.getAbsolutePath());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        public static void printYamlAggregate(String aggregatName, List<FieldNode> attributList, List<String> printedMainAggregates) {
            alreadyPrinted = false;

            // Vérifier null pour printedMainAggregates et aggregatName
            if (printedMainAggregates != null && aggregatName != null) {
                for (String printedAgg : printedMainAggregates) {
                    if (printedAgg.equalsIgnoreCase(aggregatName + "_" + apiName)) {
                        alreadyPrinted = true;
                        break;
                    }
                }
            }

            if (!alreadyPrinted) {
                printValue(aggregatName, "# ---- " + StringUtils.capitalize(aggregatName) + " definition");
                printValue(aggregatName, StringUtils.capitalize(aggregatName) + ":");
                printValue(aggregatName, "      type: object");

                String requiredElements = "";
                boolean requiredElementsExist = false;

                // Sécuriser attributList et children
                if (attributList != null && !attributList.isEmpty() && attributList.get(0) != null) {
                    List<FieldNode> children = attributList.get(0).getChildren();
                    if (children != null) {
                        for (FieldNode child : children) {
                            if (child != null && child.getOccurrence() != null && child.getOccurrence().toLowerCase().contains("required")) {
                                requiredElements += Introspector.decapitalize(child.getFieldName()) + ";";
                                requiredElementsExist = true;
                            }
                        }

                        if (requiredElementsExist) {
                            printValue(aggregatName, "      required:");
                            for (String requiredElmt : requiredElements.split(";")) {
                                if (requiredElmt.toLowerCase().startsWith(pwcServiceId.toLowerCase())) {
                                    requiredElmt = requiredElmt.replace(pwcServiceId, pwcServiceId.toLowerCase());
                                }
                                printValue(aggregatName, "    - " + requiredElmt);
                            }
                        }

                        printValue(aggregatName, "      properties:");
                        for (FieldNode node : children) {
                            if (node != null) {
                                recursivePrint(aggregatName, node, printedMainAggregates);
                            }
                        }
                    }
                }

                if (printedMainAggregates != null) {
                    printedMainAggregates.add(aggregatName + "_" + apiName);
                }
            }
        }


        public static Map<String, String> buildFieldPaths(List<FieldNode> fields) {
            Map<String, String> fieldToParent = new HashMap<>();
            for (FieldNode f : fields) {
                fieldToParent.put(StringUtils.uncapitalize(f.getFieldName()), StringUtils.uncapitalize(f.getParent()));
            }

            Map<String, String> fieldToPath = new HashMap<>();
            for (FieldNode f : fields) {
                StringBuilder path = new StringBuilder(StringUtils.uncapitalize(f.getFieldName()));
                String parent = StringUtils.uncapitalize(f.getParent());
                while (parent != null && !parent.equals("-")) {
                    path.insert(0, parent + ".");
                    parent = fieldToParent.get(parent);
                }
                fieldToPath.put(StringUtils.uncapitalize(f.getFieldName()), path.toString());
            }
            return fieldToPath;
        }
        public static void recursivePrint(String aggregatName, FieldNode node,List<String> printedAggregates) {
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
//                            printValue(aggregatName,"    " + fieldName + ":");
//                            printValue(aggregatName,"        $ref: 'aggregate.yaml#/" +StringUtils.capitalize(fieldName) + apiName + "'");
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
                                                        printValue(aggregatName, "      $ref: 'aggregate.yaml#/kenza" + StringUtils.capitalize(fieldName) + apiName + "'");
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
//                                                printValue(aggregatName,"      $ref: 'aggregate.yaml#/kenza" + StringUtils.capitalize(fieldName) + apiName + "'");
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
                                printValue(aggregatName,"      $ref: 'aggregate.yaml#/rabia" + StringUtils.capitalize(fieldName) + apiName + "'");
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
        public static boolean isComplexType(String type) {
            for (String element : simpleTypes.split(";")) {
                if (type.toLowerCase().startsWith(element) || type.toLowerCase().equalsIgnoreCase("enum")
                        || type.toLowerCase().equalsIgnoreCase("closedenum")) {
                    return false;
                }
            }
            return true;
        }

        public static Writer writer;
        public static void printValue(String aggregatName, String value) {
            try {
                if ( aggregatName != null && aggregatName.equalsIgnoreCase(apiName + "V35Rq")) {
                    writer = new PrintWriter(new BufferedWriter(new FileWriter(yamlOutputPath + "request.yaml", true)));
                } else if ( aggregatName != null && aggregatName.equalsIgnoreCase(apiName + "V35Rs")) {
                    writer = new PrintWriter(new BufferedWriter(new FileWriter(yamlOutputPath + "response.yaml", true)));
                } else if (aggregatName != null && aggregatName.equalsIgnoreCase("DB")) {
                    writer = new PrintWriter(new BufferedWriter(
                            new FileWriter(apiCodeGenPath + "/" + apiName + "/" + apiName + "_codeSQL.sql", true)));
                } else if (aggregatName != null && aggregatName.equalsIgnoreCase("API")) {
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




        private static String decapitalize(String s) {
            if (s == null || s.isEmpty()) {
                return s;
            }
            return s.substring(0, 1).toLowerCase() + s.substring(1);
        }

        private static String mapDataType(String pdfType) {
            if (pdfType == null) return "string";

            pdfType = pdfType.toLowerCase();

            if (pdfType.contains("date")) return "string";
            if (pdfType.contains("enum")) return "string";
            if (pdfType.startsWith("c(")) return "string"; // C(36) etc.
            if (pdfType.startsWith("n(")) return "number"; // N(10) etc.
            if (pdfType.contains("number") || pdfType.contains("numeric")) return "number";
            if (pdfType.contains("boolean")) return "boolean";

            return "string"; // fallback
        }
        public static void extractApiMetadataFromPdf(String filePath) {
            try {
                PDDocument document = PDDocument.load(new File(filePath));
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                document.close();

                System.out.println("=== ANALYZING PDF FOR METADATA ===");

                // Extraire le nom de l'API depuis le titre
                Pattern apiNamePattern = Pattern.compile("^(\\w+)\\s*\\n", Pattern.MULTILINE);
                Matcher apiNameMatcher = apiNamePattern.matcher(text);
                if (apiNameMatcher.find()) {
                    apiName = apiNameMatcher.group(1).trim();
                    System.out.println("API Name found: " + apiName);
                }

                // Extraire la version de l'API depuis l'URI
                Pattern versionPattern = Pattern.compile("/\\w+/(V\\d+)", Pattern.MULTILINE);
                Matcher versionMatcher = versionPattern.matcher(text);
                if (versionMatcher.find()) {
                    apiVersion = versionMatcher.group(1).trim();
                    System.out.println("API Version found: " + apiVersion);
                }

                // Extraire la méthode HTTP
                Pattern methodPattern = Pattern.compile("(POST|GET|PUT|DELETE)\\s*-\\s*PowerCARD", Pattern.MULTILINE);
                Matcher methodMatcher = methodPattern.matcher(text);
                if (methodMatcher.find()) {
                    apiHttpMethod = methodMatcher.group(1).trim();
                    System.out.println("HTTP Method found: " + apiHttpMethod);
                }

                // Extraire l'overview de l'API
                Pattern overviewPattern = Pattern.compile("API Overview\\s*:\\s*\\n([^#].*?)(?=Functional Description|$)", Pattern.DOTALL);
                Matcher overviewMatcher = overviewPattern.matcher(text);
                if (overviewMatcher.find()) {
                    apiOverview = overviewMatcher.group(1).trim()
                            .replaceAll("\\s+", " ") // Normaliser les espaces
                            .replaceAll("\\n", " "); // Supprimer les sauts de ligne
                    System.out.println("API Overview found: " + apiOverview);
                }

                // Déduire le service ID du nom de l'API
                if (apiName != null && apiName.startsWith("EnrollCard")) {
                    pwcServiceId = "EnrollCard";
                    System.out.println("Service ID deduced: " + pwcServiceId);
                }

                // Déduire le module
                pwcModule = "VFCModule";
                pwcRelease = "3.5.5";

                System.out.println("=== METADATA EXTRACTION COMPLETE ===");
                System.out.println("API Name: " + apiName);
                System.out.println("API Version: " + apiVersion);
                System.out.println("HTTP Method: " + apiHttpMethod);
                System.out.println("Service ID: " + pwcServiceId);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        public static void runGeneration(String  filePath, boolean yamlEnabled, boolean cssEnabled) throws IOException {

            String msgType = null;
            try {
                Map<String, List<FieldNode>> complexTypes = new LinkedHashMap<String, List<FieldNode>>();
                Map<String, String> errorCodeList = new LinkedHashMap<String, String>();
                JSONArray rows = new JSONArray();
                Iterator<Object> rowIterator = rows.iterator();
                //Request Iterator<Row> rowIterator = sheet.iterator();
                Map<String, List<FieldNode>> nodeMap = new HashMap<>();
                Map<String, String> fieldNameToParentPath = new HashMap<>();
                PDDocument document = PDDocument.load(new File(filePath));
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                List<FieldNode> roots = new ArrayList<>();
                int counterMsg = 0;

                List<String> lines = Arrays.asList(text.split("\\r?\\n"));
                Iterator<String> generalIterator = lines.iterator();

                //
                text = text.replaceAll("([a-zA-Z])\\s*\\n\\s*([a-zA-Z])", "$1$2");

                // Improved regex pattern to handle the PDF structure better
                // This pattern looks for number + field name + type + optional parent + occurrence/description
                Pattern pattern = Pattern.compile(
                        "(\\d+)\\s+([A-Za-z0-9]+)\\s+([A-Za-z()0-9]+)\\s+([A-Za-z0-9]*)\\s*([\\s\\S]*?)(?=\\n\\d+|\\Z)"
                );



                int generalRowId = 0; // remplace getRowNum()
                //
                while (generalIterator.hasNext()) {
                    String parentRow = generalIterator.next(); // ✅ c'est une String

                    System.out.println("Traitement de la ligne " + generalRowId + ": " + parentRow);

                    generalRowId++;

                    String[] columns = parentRow.split("\\|"); // ou ton séparateur
                    if (columns.length > 1 && columns[1] != null && !columns[1].isEmpty()) {
                        try {
                            JSONObject jsonRow = new JSONObject();
                            jsonRow.put("cells", new JSONArray(Arrays.asList(parentRow.split("\\|"))));
                            setApiIdentification(jsonRow);
                        } catch (Exception e) {
                            System.out.println("Failed to parse row as JSON: " + parentRow);
                            e.printStackTrace();
                        } // ici tu passes la String
                    }

                    if (columns.length > 1 && "Field Name".equalsIgnoreCase(columns[1].trim())) {
                        counterMsg++;
                        if (counterMsg == 1) {
                            msgType = apiName + "V35Rq";
                            break;
                        }
                    }
                }
                for (int i = generalRowId + 1; i < lines.size(); i++) {
                    String rowLine = lines.get(i);
                    String[] columns = rowLine.split("\\|"); // adapte le séparateur

                    if (columns.length <= 1) continue;

                    String fieldName = StringUtils.capitalize(columns[1].trim());
                    String dataType = columns.length > 2 ? columns[2].trim() : null;
                    String parent = columns.length > 3 ? StringUtils.capitalize(columns[3].trim()) : null;
                    String occurrence = columns.length > 4 ? columns[4].trim() : null;
                    String fieldDescription = columns.length > 5 ? columns[5].trim() : null;

                    if ("Field Name".equalsIgnoreCase(columns[1].trim())) break;

                    FieldNode node = new FieldNode(fieldName, dataType, parent, occurrence, fieldDescription);

                    String parentFullPath = parent == null || parent.equals("-") ? "-" : fieldNameToParentPath.getOrDefault(parent, parent);
                    String currentFullPath = buildPath(parentFullPath, fieldName);
                    fieldNameToParentPath.put(fieldName, currentFullPath);
                    nodeMap.computeIfAbsent(parentFullPath, k -> new ArrayList<>()).add(node);
                }
                FieldNode root = buildTree("-", nodeMap);
                roots.add(root);
                complexTypes.put(msgType, roots);

                //Response
                // Initialisation
                nodeMap = new LinkedHashMap<>();
                fieldNameToParentPath = new HashMap<>();
                roots = new ArrayList<>();
                counterMsg = 0;
                generalRowId = 0;

// Supposons que 'rows' soit un JSONArray représentant toutes les lignes de la table Confluence


// 1️⃣ Détecter le msgType "V35Rs"
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject parentRow = rows.getJSONObject(i); // <-- ici pas de cast
                    JSONArray cells = parentRow.getJSONArray("cells");

                    if (cells.length() > 1 && "Field Name".equalsIgnoreCase(cells.getJSONObject(1).getString("value"))) {
                        counterMsg++;
                        if (counterMsg == 2) {
                            msgType = apiName + "V35Rs";
                            break;
                        }
                    }
                }

// 2️⃣ Construire les FieldNodes à partir de l'index général

                for (int i = generalRowId + 1; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    JSONArray cells = row.getJSONArray("cells");

                    if (cells.length() <= 1) continue;

                    String fieldName = StringUtils.capitalize(cells.length() > 1 ? cells.getJSONObject(1).getString("value").trim() : null);
                    String dataType = cells.length() > 2 ? cells.getJSONObject(2).getString("value").trim() : null;
                    String parent = cells.length() > 3 ? StringUtils.capitalize(cells.getJSONObject(3).getString("value").trim()) : null;
                    String occurrence = cells.length() > 4 ? cells.getJSONObject(4).getString("value").trim() : null;
                    String fieldDescription = cells.length() > 5 ? cells.getJSONObject(5).getString("value").trim() : null;

                    if ("Field Name".equalsIgnoreCase(fieldName)) break;

                    FieldNode node = new FieldNode(fieldName, dataType, parent, occurrence, fieldDescription);

                    // Get parent's full path from fieldNameToParentPath or use "-" as root
                    String parentFullPath = parent == null || parent.equals("-") ? "-" : fieldNameToParentPath.getOrDefault(parent, parent);
                    // Build current node full path
                    String currentFullPath = buildPath(parentFullPath, fieldName);
                    // Save mapping for this field
                    fieldNameToParentPath.put(fieldName, currentFullPath);
                    // Then put the node into parentPathMap under parentFullPath
                    nodeMap.computeIfAbsent(parentFullPath, k -> new ArrayList<>()).add(node);
                }
                root = buildTree("-", nodeMap);
                roots.add(root);
                complexTypes.put(msgType, roots);

                //ErrorCode+ImpactedTables


                while (generalIterator.hasNext()) {
                    String parentRow = generalIterator.next(); // ✅ c'est bien une String
                    int generalRowIdd = generalRowId; // ou une autre logique, car rows.toList().indexOf(parentRow) ne marche pas avec String

                    // Ici tu n'as PAS de "cells", il faut découper toi-même la ligne
                    String[] columns = parentRow.split("\\|");

                    // Vérifier si c'est la ligne "Error code"
                    if (columns.length > 4 && "Error code".equalsIgnoreCase(columns[4].trim())) {

                        String errorCode = null;
                        String errorDesc = null;

                        while (rowIterator.hasNext()) {
                            JSONObject row = (JSONObject) rowIterator.next();
                            int parentRowId = rows.toList().indexOf(row);
                            JSONArray rowCells = row.getJSONArray("cells");

                            if (rowCells.length() > 4 && "Impacted Tables".equalsIgnoreCase(rowCells.getJSONObject(4).getString("value").trim())
                                    && generalRowIdd < parentRowId) {
                                break;
                            }

                            if (rowCells.length() > 5 && generalRowIdd < parentRowId) {
                                errorCode = rowCells.getJSONObject(4).getString("value").trim();
                                errorDesc = rowCells.getJSONObject(5).getString("value").trim();
                                if (errorCode != null && !errorCode.isEmpty()) {
                                    errorCodeList.put(errorCode, errorDesc);
                                }
                            }
                        }

                        // Vérifier si c'est la ligne "Impacted Tables"
                    } else if (columns.length > 4 && "Impacted Tables".equalsIgnoreCase(columns[4].trim())) {

                        String tableName = null;
                        String apiResources = "";

//                    if (cells.length() > 5 && cells.getJSONObject(5).getString("value") != null)
//                        tableName = cells.getJSONObject(5).getString("value").trim();
                        if (tableName != null)
                            apiResources = tableName + ";";

                        while (rowIterator.hasNext()) {
                            JSONObject row = (JSONObject) rowIterator.next();
                            int parentRowId = rows.toList().indexOf(row);
                            JSONArray rowCells = row.getJSONArray("cells");

                            if (rowCells.length() > 5 && generalRowIdd < parentRowId) {
                                tableName = rowCells.getJSONObject(5).getString("value").trim();
                                if (tableName != null && !tableName.isEmpty())
                                    apiResources += tableName + ";";
                            }
                        }
                    }
                }
                System.out.println("c est fait");
                if (isApiEnabledToBeGenerated(apiName)) {
                    if (yamlEnabled) {

                        // module-descriptor.json
                        writer = new PrintWriter(
                                new BufferedWriter(new FileWriter(yamlOutputPath + "module-descriptor.json", true)));
                        if (isFirstApi) {
                            FileSystemUtils.copyRecursively(new File(staticTemplateDataPath), new File(yamlOutputPath));
                            printValue(getDefaultModuleDescriptor().replace("[API_MODULE]", pwcModule).replace("[PWC_SERVICE_ID]",
                                            pwcService.replaceAll("[\\\r]+", "\r            <br>").replaceAll("[\\\n]+",
                                                    "\n            <br>")),
                                    false);
                        }

                        printValue("{\r\n" + "          \"name\":\"" + getApiNameToPrint(apiName, pwcServiceId) + "\",\r\n" + "          \"file\": \"" + apiName
                                + ".yaml\",\r\n" + "          \"subApiList\": [\r\n" + "            {\r\n"
                                + "              \"file\": \"" + apiName + ".yaml\",\r\n" + "              \"name\": \"" + getApiNameToPrint(apiName, pwcServiceId)
                                + " " + apiVersion + "\",\r\n" + "              \"HttpVerb\": \"" + apiHttpMethod + "\"\r\n"
                                + "            }\r\n" + "          ]\r\n" + "        }"
                                + ((!lastInsertOnModuleDescriptor) ? "," : ""), false);

                        if (lastInsertOnModuleDescriptor)
                            printValue("]\r\n" + "    }\r\n" + "  ]\r\n" + "}", false);

                        writer.close();

                        // default template request
                        writer = new PrintWriter(new BufferedWriter(new FileWriter(yamlOutputPath + "request.yaml", true)));
                        if (isFirstApi)
                            printValue(getDefaultRequest(), false);
                        writer.close();

                        // default template response
                        writer = new PrintWriter(new BufferedWriter(new FileWriter(yamlOutputPath + "response.yaml", true)));
                        if (isFirstApi)
                            printValue(getDefaultResponse(), false);
                        writer.close();

                        // default template aggregate
                        writer = new PrintWriter(new BufferedWriter(new FileWriter(yamlOutputPath + "aggregate.yaml", true)));
                        if (isFirstApi)
                            printValue(getDefaultAggregate(), false);
                        writer.close();

                        writer = new PrintWriter(new BufferedWriter(new FileWriter(yamlOutputPath + apiName + ".yaml", true)));

                        String errorListForYamlFile = "";
                        for (Map.Entry<String, String> entry : errorCodeList.entrySet()) {
                            if (entry != null && entry.getKey() != null) {
                                errorListForYamlFile += "              <tr><td>" + entry.getKey() + "</td><td>"
                                        + entry.getValue() + "</td></tr>\n";
                            }
                        }
                        printValue(getDefaultHeader().replace("[API_MODULE]", pwcModule)
                                .replace("[PWC_SERVICE_OVERVIEW]",
                                        pwcServiceOverview.replaceAll("[\\\r]+", "\r            <br>").replaceAll("[\\\n]+",
                                                "\n            <br>"))
                                .replace("[PWC_SERVICE_ID]", pwcServiceId).replace("[PWC_RELEASE]", pwcRelease)
                                .replace("[API_PRINTED_NAME]", getApiNameToPrint(apiName, pwcServiceId))
                                .replace("[API_NAME]", apiName)
                                .replace("[API_VERSION]", apiVersion)
                                .replace("[API_HTTP_METHOD_lower]", apiHttpMethod.toLowerCase())
                                .replace("[API_OVERVIEW]",
                                        apiOverview.replaceAll("[\\\r]+", "\r            <br>").replaceAll("[\\\n]+",
                                                "\n            <br>"))
                                .replace("[API_ERROR_LIST]", errorListForYamlFile), false);
                        writer.close();


                        ArrayList<String> printedMainAggregates = new ArrayList<String>();
                        for (Map.Entry<String, List<FieldNode>> entry : complexTypes.entrySet()) {
                            printYamlAggregate(entry.getKey(), entry.getValue(), printedMainAggregates);

                        }
//                    generateDynamicJsonSamples(complexTypes);
                    }

                    isFirstApi = false;
                }
            }


            // Catch block to handle exceptions
            catch (Exception e) {

                // Display the exception along with line number
                // using printStackTrace() method
                e.printStackTrace();
            }
        }


        public static String apiRestrictredList = null;
        public static boolean isApiEnabledToBeGenerated(String apiName) {
            if (apiRestrictredList != null && !apiRestrictredList.trim().isEmpty()) {
                String[] apiToGenerateList = apiRestrictredList.split("[;,]");
                System.out.println("📋 Liste des APIs autorisées : " + Arrays.toString(apiToGenerateList));
                for (String api : apiToGenerateList) {
                    if (api.equalsIgnoreCase(apiName.trim())) {
                        return true;
                    }
                }

                return false;
            }
            return true;
        }
        public static String getApiNameToPrint(String apiConcatenatedName,String serviceID) {
            StringBuffer buffer = new StringBuffer();
            Pattern p = Pattern.compile("([A-Z][a-z]*)");
            Matcher m = p.matcher(serviceID);
            while ( m.find() )
                buffer.append(m.group() + " ");
            String serviceIDSplited= buffer.toString().trim();

            buffer = new StringBuffer();
            m = p.matcher(apiConcatenatedName);
            while ( m.find() )
                buffer.append(m.group() + " ");
            return buffer.toString().trim().replace(serviceIDSplited, serviceID);
        }
        public static void setApiIdentification(JSONObject parentRow) {
            JSONArray cells = parentRow.getJSONArray("cells");

            if (cells.length() <= 2) return; // Vérifie qu'il y a au moins 2 colonnes

            String titleInfo = cells.getJSONObject(1).getString("value").trim();

            switch (titleInfo.toUpperCase()) {
                case "PWC RELEASE":
                    pwcRelease = cells.getJSONObject(2).getString("value").trim();
                    break;

                case "PWC MODULE":
                    pwcModule = cells.getJSONObject(2).getString("value").trim();
                    break;

                case "PWC SERVICE":
                    pwcService = cells.getJSONObject(2).getString("value").trim();
                    break;

                case "PWC SERVICE OVERVIEW":
                    pwcServiceOverview = cells.getJSONObject(2).getString("value").trim();
                    break;

                case "PWC SERVICE ID":
                    String newServiceId = cells.getJSONObject(2).getString("value").trim();
                    if (!StringUtils.equalsIgnoreCase(pwcServiceId, newServiceId) && newServiceId != null) {
                        pwcServiceId = newServiceId;
                        apiCodeGenPath = apiCodeGenPath.replace("[PWC_SERVICE_ID]", pwcServiceId);
                        yamlOutputPath = yamlOutputPath.replace("[PWC_SERVICE_ID]", pwcServiceId);
                        pptOutputPath = pptOutputPath.replace("[PWC_SERVICE_ID]", pwcServiceId);
                        imgOutputPath = imgOutputPath.replace("[PWC_SERVICE_ID]", pwcServiceId);

                        System.out.println("######OutPutPath:");
                        System.out.println("########DB/BtDesign:" + apiCodeGenPath);
                        System.out.println("########YAML      :" + yamlOutputPath);
                        System.out.println("########PPT       :" + pptOutputPath);
                        System.out.println("########IMG       :" + imgOutputPath);

                        try {
                            Files.createDirectories(Paths.get(yamlOutputPath));
                            Files.createDirectories(Paths.get(pptOutputPath));
                            Files.createDirectories(Paths.get(imgOutputPath));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;

                case "API NAME":
                    String newApiName = cells.getJSONObject(2).getString("value").trim();
                    if (!StringUtils.equalsIgnoreCase(apiName, newApiName) && newApiName != null) {
                        apiName = newApiName;
                        System.out.println("##################################################");
                        System.out.println("### GenerateYaml for API: " + apiName);
                    }
                    break;

                case "API HTTP METHOD":
                    apiHttpMethod = cells.getJSONObject(2).getString("value").trim();
                    break;

                case "API VERSION":
                    apiVersion = cells.getJSONObject(2).getString("value").trim();
                    break;

                case "API OVERVIEW":
                case "OVERVIEW":
                    apiOverview = cells.getJSONObject(2).getString("value").trim();
                    break;

                default:
                    break;
            }
        }
        public static FieldNode buildTree(String parentPath, Map<String, List<FieldNode>> map) {
            List<FieldNode> children = map.get(parentPath);
            if (children == null) return null;


            // Since root may be multiple fields, return a dummy root node
            FieldNode dummyRoot = new FieldNode(parentPath, "Complex","Root", "Required", "Root node");
            for (FieldNode child : children) {
                String childPath = buildPath(parentPath, child.getFieldName());
                if (ConfluenceInputSource.isComplexType(child.getDataType())) {
                    FieldNode subtree = buildTree(childPath, map);
                    if (subtree != null) {
                        child.getChildren().addAll(subtree.getChildren());
                    }
                }
                dummyRoot.addChild(child);
            }

            return dummyRoot;
        }
        public static String buildPath(String parentPath, String fieldName) {
            if (parentPath == null || parentPath.isEmpty() || parentPath.equals("-")) {
                return fieldName;
            } else {
                return parentPath + "." + fieldName;
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





