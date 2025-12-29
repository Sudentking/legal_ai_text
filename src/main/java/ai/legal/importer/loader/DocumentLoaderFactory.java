package ai.legal.importer.loader;

import java.io.File;

/**
 * 根据文件后缀选择合适的文档加载器。
 */
public class DocumentLoaderFactory {

    private DocumentLoaderFactory() {
    }

    public static DocumentLoader forFile(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".docx")) {
            return new WordDocumentLoader();
        }
        if (name.endsWith(".pdf")) {
            return new PdfDocumentLoader();
        }
        // 默认为 txt
        return new TxtDocumentLoader();
    }
}
