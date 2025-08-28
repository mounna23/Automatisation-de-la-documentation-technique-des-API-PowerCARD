package org.example.projectpfa.controller;

import org.example.projectpfa.service.GeneratorService;
import org.example.projectpfa.service.YamlFromJsonListService;
import org.example.projectpfa.service.YamlFromJsonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

@RestController
@RequestMapping("/api/generator")
public class GeneratorController {

    private static final Logger logger = LoggerFactory.getLogger(GeneratorController.class);
    private final GeneratorService generatorService;
    private final YamlFromJsonService yamlFromJsonService;
    private final YamlFromJsonListService YamlFromJsonListService;

    public GeneratorController(GeneratorService generatorService, YamlFromJsonService yamlFromJsonService, YamlFromJsonListService yamlFromJsonListService) {
        this.generatorService = generatorService;
        this.yamlFromJsonService = yamlFromJsonService;
        this.YamlFromJsonListService = yamlFromJsonListService;
    }
    @PostMapping("/generate")
    public ResponseEntity<String> generate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "apiRestrictedList", required = false) String apiRestrictedList,
            @RequestParam(value = "outputPath", required = false) String outputPath) {

        try {
            if (file == null || file.isEmpty()) {
                logger.error("Aucun fichier fourni");
                return ResponseEntity.badRequest().body("❌ Erreur : Aucun fichier fourni");
            }

            // 🔒 Vérification d’extension autorisée
            String originalName = file.getOriginalFilename();
            if (originalName == null) {
                return ResponseEntity.badRequest().body("❌ Erreur : Nom de fichier invalide");
            }

            String lowerName = originalName.toLowerCase();
            if (!(lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") || lowerName.endsWith(".json"))) {
                logger.error("Extension non autorisée : {}", originalName);
                return ResponseEntity.badRequest()
                        .body("❌ Erreur : Extension non autorisée (" + originalName + "). " +
                                "Extensions valides : .xlsx, .xls, .json");
            }

            // Mettre à jour le chemin si fourni depuis le frontend
            if (outputPath != null && !outputPath.trim().isEmpty()) {
                GeneratorService.outputPath = outputPath.endsWith("/") || outputPath.endsWith("\\") ?
                        outputPath : outputPath + "/";
            }

            logger.info("📂 Fichier reçu : {} | Chemin sortie : {}",
                    file.getOriginalFilename(), GeneratorService.outputPath);

            generatorService.generate(file, true, apiRestrictedList, outputPath);

            return ResponseEntity.ok("✅ Génération terminée avec succès\nChemin : " + GeneratorService.outputPath);

        } catch (Exception e) {
            logger.error("Erreur lors de la génération", e);
            return ResponseEntity.internalServerError()
                    .body("❌ Erreur : " + (e.getMessage() != null ? e.getMessage() : "Erreur inconnue"));
        }
    }

    @PostMapping("/process")
    public ResponseEntity<String> processYaml(
            @RequestParam String jsonPath,
            @RequestParam String yamlPath,
            @RequestParam(defaultValue = "aggregate_filled.yaml") String outputPath
    ) {
        try {
            yamlFromJsonService.processAndSave(jsonPath, yamlPath, outputPath);
            return ResponseEntity.ok("✅ Fichier généré avec succès : " + outputPath);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("❌ Erreur : " + e.getMessage());
        }
    }
    @PostMapping("/processlist")
    public ResponseEntity<String> processYamlList(
            @RequestParam String jsonPath,
            @RequestParam String yamlPath,
            @RequestParam(defaultValue = "aggregate_filled_list.yaml") String outputPath
    ) {
        try {
            YamlFromJsonListService.processAndSave(jsonPath, yamlPath, outputPath);
            return ResponseEntity.ok("✅ Fichier généré avec succès : " + outputPath);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("❌ Erreur : " + e.getMessage());
        }
    }
}
