package org.example.projectpfa.source;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service("ConfluenceInputSource")
public class ConfluenceInputSource implements InputSource {


    @Override
    public void process(MultipartFile file, boolean enableDebug, String apiRestrictedList) throws Exception {

    }
}
