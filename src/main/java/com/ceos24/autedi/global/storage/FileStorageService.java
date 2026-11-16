package com.ceos24.autedi.global.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    StoredFile upload(MultipartFile file, String subDir);

    Resource load(String fileUrl);

    void delete(String fileUrl);
}
