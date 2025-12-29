package ai.legal.importer.loader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * TXT 文档加载器（UTF-8）。
 */
public class TxtDocumentLoader implements DocumentLoader {

    @Override
    public String load(File file) throws IOException {
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }
}
