package com.dium.demo.services.file;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileService {
    String saveFile(MultipartFile file) throws IOException;
    void deleteFile(String fileUrl);

    /**
     * Заменяет файл новым, если новый передан.
     *
     * @return ссылка на новый файл либо прежняя ссылка, если новый файл не передан
     */
    default String replaceFile(String currentUrl, MultipartFile newFile) throws IOException {
        if (newFile == null || newFile.isEmpty()) {
            return currentUrl;
        }
        if (currentUrl != null && !currentUrl.isBlank()) {
            deleteFile(currentUrl);
        }
        return saveFile(newFile);
    }
}
