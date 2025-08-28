package org.example.projectpfa.service;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.projectpfa.service.GeneratorService.*;

@Service
public class ExcelReaderService {

    public static boolean isRowEmpty(Row row) {
        boolean isEmpty = true;
        String data = "";
        for (Cell cell : row) {
            cell.setCellType(CellType.STRING);
            data = data.concat(cell.getStringCellValue());
        }
        if (!data.trim().isEmpty()) {
            isEmpty = false;
        }
        return isEmpty;
    }

    public static String getCellValue(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception ex) {
                        return "";
                    }
                }
            case ERROR:
            case BLANK:
            default:
                return "";
        }

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
}
