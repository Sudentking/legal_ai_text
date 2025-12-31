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
            DocumentLoader loader = tryInstantiate("ai.legal.importer.loader.WordDocumentLoader");
            if (loader != null) {
                return loader;
            }
            throw new IllegalStateException("缺少 Word 解析依赖（poi-ooxml），无法导入 .docx 文件");
        }
        if (name.endsWith(".pdf")) {
            DocumentLoader loader = tryInstantiate("ai.legal.importer.loader.PdfDocumentLoader");
            if (loader != null) {
                return loader;
            }
            throw new IllegalStateException("缺少 PDF 解析依赖（pdfbox），无法导入 .pdf 文件");
        }
        // 默认为 txt
        return new TxtDocumentLoader();
    }

    private static DocumentLoader tryInstantiate(String className) {
        if (className == null || className.isBlank()) {
            return null;
        }
        try {
            Class<?> c = Class.forName(className);
            Object o = c.getDeclaredConstructor().newInstance();
            if (o instanceof DocumentLoader dl) {
                return dl;
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
