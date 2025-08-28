package org.example.projectpfa.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.example.projectpfa.service.GeneratorService.*;
import static org.example.projectpfa.service.YamlGeneratorService.printValue;

@Service
public class CodeGeneratorService {


    public static void generateCssType(String aggregatName, ArrayList<String> attributList) {
        if (aggregatName.equalsIgnoreCase("REQUESTINFO") || aggregatName.equalsIgnoreCase("RESPONSEINFO"))
            return;

        int counter = 1;
        for (String attribut : attributList) {
            if (counter == 1) {
                printValue("DB", "CREATE OR REPLACE TYPE CSS_"
                        + aggregatName.replace("List", "").replace("V35", "").toUpperCase() + "_V35 FORCE AS OBJECT (");
            }
            String[] row = attribut.split(";");
            printCssTypeRecord(apiName, row[1], row[2], row[3], row[4]);

            counter++;
        }
        printValue("DB", "\t" + StringUtils.rightPad("keyvalues", 30) + "\t\t" + "CSS_TAB_KEYVALUE_V35");
        printValue("DB", ")");
        printValue("DB", "/");
        printValue("DB", "");

    }

    public static void generateBtDesign(String aggregatName, ArrayList<String> attributList) {
        if (aggregatName.equalsIgnoreCase("REQUESTINFO") || aggregatName.equalsIgnoreCase("RESPONSEINFO"))
            return;

        int counter = 1;
        String cssTypeName = "";
        String extendedParent = "";
        for (String attribut : attributList) {
            if (counter == 1) {
                if (aggregatName.endsWith("Rq")) {
                    extendedParent = "com.acp.provider.message.api.AbstractMessageV35Rq";
                } else if (aggregatName.endsWith("Rs")) {
                    extendedParent = "com.acp.provider.message.api.AbstractMessageV35Rs";
                } else {
                    extendedParent = "com.acp.provider.message.api.AbstractAggregateV35";
                }
                printValue("API", "DataTransferObject " + aggregatName + " extends " + extendedParent + " {");
                cssTypeName = "ma.hps.jpub.css_" + aggregatName.replace("List", "").replace("V35", "").toLowerCase()
                        + "_v35";
                mapApiDBTypes.put(aggregatName, cssTypeName);
                printValue("API", "\thint=\"databaseName=" + cssTypeName + "\"");
            }
            String[] row = attribut.split(";");
            if (!(row[1].equalsIgnoreCase("requestinfo") || row[1].equalsIgnoreCase("responseinfo"))) {
                printBtDesignRecord(apiName, row[1], row[2], row[3], row[4]);
            }

            counter++;
        }

        printValue("API", "}");
        printValue("API", "");

    }

    public static void printBtDesignRecord(String apiName, String fieldName, String dataType, String parent,
                                           String occurrence) {
        if (enableDebugLog) {
            System.out.println("-apiName:" + apiName + "	-fieldName:" + fieldName + "	-dataType:" + dataType
                    + "	-parent:" + parent + "	-occurrence:" + occurrence);
        }
        String fieldType = "";
        String fieldLength = "";
        String complexType = "";
        fieldName = Introspector.decapitalize(fieldName);
        if (StringUtils.isNotEmpty(fieldName)) {
            String hintValue = "";
            fieldType = "";
            fieldLength = "";
            complexType = "";
            if (!isComplexType(dataType)) {
                if (occurrence.toLowerCase(Locale.ROOT).startsWith("requir")) {
                    hintValue = "NotEmpty,";
                }
                fieldType = dataType.trim();
                if (dataType.contains("(")) {
                    fieldType = dataType.substring(0, dataType.indexOf("(")).trim();
                    fieldLength = dataType.substring(dataType.indexOf("(") + 1, dataType.indexOf(")"));
                }
                if (fieldType.toLowerCase().equalsIgnoreCase("c")
                        || fieldType.toLowerCase().equalsIgnoreCase("closedenum")
                        || fieldType.toLowerCase().equalsIgnoreCase("enum")) {
                    fieldType = "String";
                    hintValue += "Alphameric,Length=" + fieldLength;
                } else if (fieldType.toLowerCase().startsWith("date")) {
                    hintValue += "CheckDateFormat";
                }
            } else {
                complexType = fieldName.trim().substring(0, 1).toUpperCase() + fieldName.trim().substring(1);
                hintValue += "type=" + complexType + "V35,map=" + mapApiDBTypes.get(complexType) + ",Valid";
            }
            if (enableDebugLog) {
                System.out.println(
                        "-fieldType:" + fieldType + "	-fieldLength:" + fieldLength + "	-hintValue:" + hintValue);
            }
            if (hintValue.toLowerCase().contains(fieldName.toLowerCase())) {
                printValue(parent, "\t" + complexType + "V35 " + fieldName + " hint=\"" + hintValue + "\";");
            } else {
                printValue(parent, "\t" + fieldType + " " + fieldName + " hint=\"" + hintValue + "\";");
            }
        }

    }

    public static void printCssTypeRecord(String apiName, String fieldName, String dataType, String parent,
                                          String occurrence) {
        String fieldType = "";
        String fieldLength = "";
        fieldName = Introspector.decapitalize(fieldName);
        String type = "";
        if (dataType.contains("(")) {
            fieldType = dataType.substring(0, dataType.indexOf("("));
            fieldLength = dataType.substring(dataType.indexOf("(") + 1, dataType.indexOf(")"));
        } else {

            fieldType = dataType;
        }
        if (fieldType.toLowerCase().startsWith("c") && !fieldType.toLowerCase().startsWith("css")
                && !fieldType.toLowerCase().startsWith("complex")) {
            type += "VARCHAR2(" + fieldLength + ")";
        } else if (fieldType.toLowerCase().startsWith("date")) {
            type += "DATE";
        } else if (fieldType.toLowerCase().startsWith("complex")) {
            type = "CSS_" + fieldName + "_V35";
        } else {

            type += fieldType;
        }

        printValue(parent, "\t" + StringUtils.rightPad(fieldName, 30) + "\t\t" + type.toUpperCase() + ",");

    }
}
