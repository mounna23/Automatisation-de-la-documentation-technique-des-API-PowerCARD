package org.example.projectpfa.source;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.example.projectpfa.service.GeneratorService;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
public class ExcelInputSource implements InputSource {

    @Override
    public void process(MultipartFile file, boolean enableDebug, String apiRestrictedList) throws Exception {

        GeneratorService.enableDebugLog = enableDebug;
        GeneratorService.apiRestrictredList = apiRestrictedList;

        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {

            // Pour chaque feuille Excel (ici on prend toutes les feuilles)
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                XSSFSheet sheet = workbook.getSheetAt(i);

                if (GeneratorService.enableDebugLog) {
                    System.out.println("Traitement de la feuille : " + sheet.getSheetName());
                }

                // Appel à runGeneration pour traiter la feuille
                GeneratorService.runGeneration(sheet,
                        GeneratorService.generateYamlEnabled,
                        GeneratorService.generateCodeEnabled);
            }

        } catch (IOException e) {
            throw new Exception("Erreur lors de la lecture du fichier Excel : " + e.getMessage(), e);
        }
    }
}
