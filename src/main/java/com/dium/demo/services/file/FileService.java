package com.dium.demo.services.file;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileService {
    String saveFile(MultipartFile file) throws IOException;
    void deleteFile(String fileUrl);
}
