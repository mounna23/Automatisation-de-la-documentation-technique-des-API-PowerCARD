package org.example.projectpfa.service;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.projectpfa.model.FieldNode;
import org.example.projectpfa.source.ExcelInputSource;
import org.example.projectpfa.source.ConfluenceInputSource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.example.projectpfa.service.ExcelReaderService.*;
import static org.example.projectpfa.service.TreeBuilderService.buildPath;
import static org.example.projectpfa.service.TreeBuilderService.buildTree;
import static org.example.projectpfa.service.YamlGeneratorService.*;

@Service
public class GeneratorService {
    public static String inputFileList = "ICD_APIs_Corporate_3.5.xlsx";//ICD_APIs_Corporate_3.5.xlsx;ICD_APIs_Acquiring_3.5.xlsx";
    public static String apiRestrictredList = null;//"EnrollCardOnVFC;ManageCardRelationshipOnVFC;";
    public static boolean enableDebugLog = true;
    public static String outputPath = "C:\\Users\\hp\\Downloads\\repo\\";
    public static String inputPath = "C:\\Users\\hp\\Downloads\\repo\\";
    public static String jsonSampleDataPath = inputPath + "//json_sample//";
    public static String pptTemplatePath = inputPath + "//template//template//PPT//API_PPT.pptx";
    public static String staticTemplateDataPath = inputPath + "//template//template//static//";

    public static String dateFormat = "dd-MM-yyyy_HHmmss";
    public static Writer writer;

    public static boolean generateCodeEnabled = false;
    public static boolean generateYamlEnabled = true;
    public static boolean isFirstApi = true;
    public static boolean lastInsertOnModuleDescriptor = false;
    public static String simpleTypes = "c (;c(;n(;n (;date;datetime;num;numeric;double;long;boolean;time;alpha;alphanumeric";
    public static String yamlOutputPath = null;
    public static String apiCodeGenPath = null;
    public static String pptOutputPath = null;
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

    public static boolean isComplexType(String type) {
        for (String element : simpleTypes.split(";")) {
            if (type.toLowerCase().startsWith(element) || type.toLowerCase().equalsIgnoreCase("enum")
                    || type.toLowerCase().equalsIgnoreCase("closedenum")) {
                return false;
            }
        }
        return true;
    }

    public void generate(MultipartFile file,
                         boolean enableDebug,
                         String apiRestrictedList,
                         String OUTputPath) {
        try {
            SimpleDateFormat formatter = new SimpleDateFormat(dateFormat);

            // init des chemins (comme dans main)
            outputPath = OUTputPath + "/" + formatter.format(new Date()) + "/";
            enableDebugLog = enableDebug;
            apiRestrictredList = apiRestrictedList;

            // Vérification fichier
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Aucun fichier fourni");
            }

            // Sauvegarde temporaire du fichier
            File tempFile = File.createTempFile("uploaded-", file.getOriginalFilename());
            file.transferTo(tempFile);

            String filePath = tempFile.getAbsolutePath();

            // chemins avec placeholder (remplacés plus tard par setApiIdentification)
            yamlOutputPath = outputPath + "ws-portal/src/main/react/public/hps/3.5.5/[PWC_SERVICE_ID]/api/schemas/";
            apiCodeGenPath = outputPath + "code-gen/[PWC_SERVICE_ID]/";
            pptOutputPath = outputPath + "PPT/[PWC_SERVICE_ID]/";
            imgOutputPath = outputPath + "ws-portal/src/main/react/public/docs/[PWC_SERVICE_ID]/";

            // mêmes créations que ton main
            new File(yamlOutputPath).mkdirs();
            new File(pptOutputPath).mkdirs();

            System.out.println("##################################################");
            System.out.println("### ICD File   : " + filePath);

            isFirstApi = true;
            lastInsertOnModuleDescriptor = false;
            mapApiDBTypes = new HashMap<>();

            try (FileInputStream fis = new FileInputStream(tempFile);
                 XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    if (i == workbook.getNumberOfSheets() - 1) {
                        lastInsertOnModuleDescriptor = true;
                    }

                    // ta logique existante
                    runGeneration(workbook.getSheetAt(i), generateYamlEnabled, generateCodeEnabled);

                    // PPT comme avant
                    pptGeneratorService.generatePPT(apiName, apiOverview);
                }
            }

            // nettoyage
            tempFile.delete();

        } catch (Exception e) {
            e.printStackTrace(); // même comportement que ton main
        }
    }


    private final ExcelInputSource excelInputSource;
    private final ConfluenceInputSource confluenceInputSource;
    private final ExcelReaderService excelReaderService;
    private final YamlGeneratorService yamlGeneratorService;
    private final CodeGeneratorService codeGeneratorService;
    private final PptGeneratorService pptGeneratorService;
    private final YamlFromJsonListService yamlFromJsonListService;
    private final YamlFromJsonService yamlFromJsonService;
    
    public GeneratorService(ExcelInputSource excelInputSource,
                            ConfluenceInputSource confluenceInputSource,
                            ExcelReaderService excelReaderService,
                            YamlGeneratorService yamlGeneratorService,
                            CodeGeneratorService codeGeneratorService,
                            PptGeneratorService pptGeneratorService, YamlFromJsonListService yamlFromJsonListService, YamlFromJsonService yamlFromJsonService) {
        this.excelInputSource = excelInputSource;
        this.confluenceInputSource = confluenceInputSource;
        this.excelReaderService = excelReaderService;
        this.yamlGeneratorService = yamlGeneratorService;
        this.codeGeneratorService = codeGeneratorService;
        this.pptGeneratorService = pptGeneratorService;
        this.yamlFromJsonListService = yamlFromJsonListService;
        this.yamlFromJsonService = yamlFromJsonService;
    }


    public static void runGeneration(XSSFSheet sheet, boolean yamlEnabled, boolean cssEnabled) {
        String msgType = null;
        try {
            Map<String, List<FieldNode>> complexTypes = new LinkedHashMap<String, List<FieldNode>>();
            Map<String, String> errorCodeList = new LinkedHashMap<String, String>();

            //Request
            Iterator<Row> rowIterator = sheet.iterator();
            Map<String, List<FieldNode>> nodeMap = new HashMap<>();
            Map<String, String> fieldNameToParentPath = new HashMap<>();

            List<FieldNode> roots = new ArrayList<>();
            int counterMsg = 0;
            int generalRowId = 0;
            Iterator<Row> generalIterator = sheet.iterator();
            while (generalIterator.hasNext()) {
                Row parentRow = generalIterator.next();
                generalRowId = parentRow.getRowNum();
                if (parentRow.getCell(1) != null && parentRow.getCell(1).getStringCellValue() != null) {
                    setApiIdentification(parentRow);
                }
                if (parentRow != null && parentRow.getCell(1) != null
                        && parentRow.getCell(1).getStringCellValue().equalsIgnoreCase("Field Name")) {
                    counterMsg++;
                    if (counterMsg == 1) {
                        msgType = apiName + "V35Rq";
                        break;
                    }
                }
            }
            for (int i = generalRowId + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String fieldName = StringUtils.capitalize(getCellValue(row, 1));
                String dataType = getCellValue(row, 2);
                String parent = StringUtils.capitalize(getCellValue(row, 3));
                String occurrence = getCellValue(row, 4);
                String fieldDescription = getCellValue(row, 5);

                if (row.getCell(1) != null && row.getCell(1).getStringCellValue().equalsIgnoreCase("Field Name"))
                    break;

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
            FieldNode root = buildTree("-", nodeMap);
            roots.add(root);
            complexTypes.put(msgType, roots);

            //Response
            rowIterator = sheet.iterator();
            nodeMap = new LinkedHashMap<>();
            fieldNameToParentPath = new HashMap<>();
            roots = new ArrayList<>();
            counterMsg = 0;
            generalRowId = 0;
            generalIterator = sheet.iterator();
            while (generalIterator.hasNext()) {
                Row parentRow = generalIterator.next();
                generalRowId = parentRow.getRowNum();
                if (parentRow != null && parentRow.getCell(1) != null
                        && parentRow.getCell(1).getStringCellValue().equalsIgnoreCase("Field Name")) {
                    counterMsg++;
                    if (counterMsg == 2) {
                        msgType = apiName + "V35Rs";
                        break;
                    }
                }
            }
            for (int i = generalRowId + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String fieldName = StringUtils.capitalize(getCellValue(row, 1));
                String dataType = getCellValue(row, 2);
                String parent = StringUtils.capitalize(getCellValue(row, 3));
                String occurrence = getCellValue(row, 4);
                String fieldDescription = getCellValue(row, 5);

                if (row.getCell(1) != null && row.getCell(1).getStringCellValue().equalsIgnoreCase("Field Name"))
                    break;

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
            generalIterator = sheet.iterator();
            while (generalIterator.hasNext()) {
                Row parentRow = generalIterator.next();
                generalRowId = parentRow.getRowNum();

                if (parentRow != null && parentRow.getCell(4) != null
                        && parentRow.getCell(4).getStringCellValue().equalsIgnoreCase("Error code")) {
                    rowIterator = sheet.iterator();
                    String errorCode = null;
                    String errorDesc = null;

                    while (rowIterator.hasNext()) {
                        Row row = rowIterator.next();
                        int parentRowId = row.getRowNum();
                        if (row != null && !isRowEmpty(row) && row.getCell(4) != null && row.getCell(4).getStringCellValue().equalsIgnoreCase("Impacted Tables"))
                            break;
                        if (row != null && !isRowEmpty(row) && row.getCell(4) != null && generalRowId < parentRowId) {
                            if (row.getCell(5) == null)
                                break;

                            errorCode = row.getCell(4).getStringCellValue();
                            errorDesc = row.getCell(5).getStringCellValue();
                            if (errorCode != null)
                                errorCodeList.put(errorCode, errorDesc);
                        }

                    }
                } else if (parentRow != null && parentRow.getCell(4) != null
                        && parentRow.getCell(4).getStringCellValue().equalsIgnoreCase("Impacted Tables")) {
                    rowIterator = sheet.iterator();
                    String tableName = null;
                    apiResources = "";
                    if (parentRow.getCell(5) != null)
                        tableName = parentRow.getCell(5).getStringCellValue();
                    if (tableName != null)
                        apiResources = tableName + ";";
                    while (rowIterator.hasNext()) {
                        Row row = rowIterator.next();
                        int parentRowId = row.getRowNum();
                        if (row != null && !isRowEmpty(row) && row.getCell(5) != null && generalRowId < parentRowId) {
                            tableName = row.getCell(5).getStringCellValue();
                            if (tableName != null)
                                apiResources += tableName + ";";
                        }

                    }
                }

            }
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
                        YamlGeneratorService.printYamlAggregate(entry.getKey(), entry.getValue(), printedMainAggregates);

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

    public static void setApiIdentification(Row parentRow) {
        String titleInfo = parentRow.getCell(1).getStringCellValue();
        switch (titleInfo.toUpperCase()) {
            case "PWC RELEASE":
                pwcRelease = parentRow.getCell(2).getStringCellValue().trim();
                break;
            case "PWC MODULE":
                pwcModule = parentRow.getCell(2).getStringCellValue().trim();
                break;
            case "PWC SERVICE":
                pwcService = parentRow.getCell(2).getStringCellValue().trim();
                break;
            case "PWC SERVICE OVERVIEW":
                pwcServiceOverview = parentRow.getCell(2).getStringCellValue().trim();
                break;
            case "PWC SERVICE ID":
                if (!StringUtils.equalsIgnoreCase(pwcServiceId, parentRow.getCell(2).getStringCellValue().trim()) && parentRow.getCell(2).getStringCellValue() != null) {
                    pwcServiceId = parentRow.getCell(2).getStringCellValue().trim();
                    apiCodeGenPath = apiCodeGenPath.replace("[PWC_SERVICE_ID]", pwcServiceId);
                    yamlOutputPath = yamlOutputPath.replace("[PWC_SERVICE_ID]", pwcServiceId);
                    pptOutputPath = pptOutputPath.replace("[PWC_SERVICE_ID]", pwcServiceId);
                    imgOutputPath = imgOutputPath.replace("[PWC_SERVICE_ID]", pwcServiceId);
                    System.out.println("######OutPutPath:");
                    System.out.println("########DB/BtDesign:" + apiCodeGenPath);
                    System.out.println("########YAML	   :" + yamlOutputPath);
                    System.out.println("########PPT        :" + pptOutputPath);
                    System.out.println("########IMG        :" + imgOutputPath);
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
                if (!StringUtils.equalsIgnoreCase(apiName, parentRow.getCell(2).getStringCellValue().trim()) && parentRow.getCell(2).getStringCellValue() != null) {
                    apiName = parentRow.getCell(2).getStringCellValue().trim();
                    System.out.println("##################################################");
                    System.out.println("###GenerateYaml for API:" + apiName);
                }
                break;
            case "API HTTP METHOD":
                apiHttpMethod = getCellValue(parentRow, 2);
                break;
            case "API VERSION":
                apiVersion = getCellValue(parentRow, 2);
                break;
            case "API OVERVIEW":
            case "OVERVIEW":
                apiOverview = getCellValue(parentRow, 2);
                break;
            default:
                break;
        }
    }

    public static boolean isApiEnabledToBeGenerated(String apiName) {
        if (apiRestrictredList != null && !apiRestrictredList.trim().isEmpty()) {
            String[] apiToGenerateList = apiRestrictredList.split("[;,]");
            for (String api : apiToGenerateList) {
                if (api.equalsIgnoreCase(apiName.trim())) {
                    System.out.println("✅ API autorisée : " + apiName);
                    return true;
                }
            }
            return false;
        }
        // Pas de restriction → on génère tout
        return true;
    }
}
