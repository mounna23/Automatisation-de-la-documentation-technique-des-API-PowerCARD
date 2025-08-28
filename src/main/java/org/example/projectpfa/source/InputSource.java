package org.example.projectpfa.source;

import org.springframework.web.multipart.MultipartFile;

public interface InputSource {
    void process(MultipartFile file, boolean enableDebug, String apiRestrictedList) throws Exception;
}
