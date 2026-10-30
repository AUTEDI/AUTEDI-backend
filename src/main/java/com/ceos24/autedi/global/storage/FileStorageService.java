package com.ceos24.autedi.global.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    StoredFile upload(MultipartFile file, String subDir);

    void delete(String fileUrl);
}
