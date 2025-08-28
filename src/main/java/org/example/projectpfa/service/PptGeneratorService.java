package org.example.projectpfa.service;

import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.example.projectpfa.service.GeneratorService.*;

@Service
public class PptGeneratorService {
    public static void generatePPT(String apiName, String apiOverview) throws IOException {

        try (FileInputStream fis = new FileInputStream(new File(pptTemplatePath));
             XMLSlideShow ppt = new XMLSlideShow(fis)) {

            for (XSLFSlide slide : ppt.getSlides()) {
                XSLFGroupShape groupShape = (XSLFGroupShape) slide.getShapes().get(0);
                recursivePptReplacement(groupShape);
            }
            // Save the updated file
            try (FileOutputStream out = new FileOutputStream(pptOutputPath + "/" + apiName + ".pptx")) {
                ppt.write(out);
            }
            System.out.println("PPT saved to: " + pptOutputPath + "/" + apiName + ".pptx");

        }
        //generate screenshot used in yaml files
        generatePPTScreenShoot(pptOutputPath + "/" + apiName + ".pptx");
    }

    public static void recursivePptReplacement(XSLFGroupShape groupShape) {
        for (XSLFShape shape : groupShape.getShapes()) {
            if (shape instanceof XSLFGroupShape) {
                recursivePptReplacement((XSLFGroupShape) shape);
            } else if (shape instanceof XSLFAutoShape) {
                XSLFAutoShape autoShape = (XSLFAutoShape) shape;

                for (XSLFTextParagraph paragraph : autoShape.getTextParagraphs()) {
                    for (XSLFTextRun run : paragraph.getTextRuns()) {
                        String text = run.getRawText();
                        if (text != null) {
                            // Calcul de l'abréviation apiOverview
                            String apiAbrev = apiOverview;
                            if (apiOverview.contains("."))
                                apiAbrev = apiOverview.substring(0, apiOverview.indexOf("."));

                            // Remplacement des placeholders dans l'ordre et mise à jour du style
                            if (text.contains("[API_NAME]")) {
                                run.setText(text.replace("[API_NAME]", apiName));
                                text = run.getRawText(); // mise à jour du texte courant
                            }

                            if (text.contains("[API_OVERVIEW]")) {
                                run.setText(text.replace("[API_OVERVIEW]", apiAbrev));
                                text = run.getRawText();
                            }

                            if (text.contains("[API_RESOURCES]")) {
                                String replacedText = text.replace("[API_RESOURCES]", apiResources.replace(";", "\n"));
                                run.setText(replacedText);

                                // Mise à jour du style selon le contenu de apiResources
                                run.setFontSize(10.0);
                                run.setItalic(false);
                                run.setFontColor(Color.DARK_GRAY);




                            }
                        }
                    }
                }
            }
        }
    }


    public static void generatePPTScreenShoot(String pptxFile) {

        // Load the PPTX file
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(pptxFile);

            XMLSlideShow ppt = new XMLSlideShow(fis);
            fis.close();

            // Create output directory if it doesn't exist
            new File(imgOutputPath).mkdirs();

            Dimension pgsize = ppt.getPageSize();
            double scale = 3.0;  // Ajuste ici la qualité (2.0, 3.0, etc.)

            int width = (int) (pgsize.width * scale);
            int height = (int) (pgsize.height * scale);
            int slideNumber = 1;

            for (XSLFSlide slide : ppt.getSlides()) {
                BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = img.createGraphics();
                graphics.scale(scale, scale);

                // Optional: set rendering hints for better quality
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

                graphics.setPaint(Color.WHITE);

                // Clear background
                //graphics.setPaint(Color.white);
                graphics.fill(new Rectangle(pgsize));

                // Render slide
                slide.draw(graphics);
                graphics.dispose();

                // Save image
                File imageFile = new File(imgOutputPath + "/" + apiName + ".png");
                ImageIO.write(img, "png", imageFile);
                System.out.println("PPT screenshot Saved: " + imageFile.getAbsolutePath());

                slideNumber++;
            }

            ppt.close();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
