package ai.legal.importer.loader;

import java.io.File;
import java.io.IOException;

/**
 * 文档加载器接口：将 Word/PDF/TXT 文件内容读取为字符串。
 */
public interface DocumentLoader {
    /**
     * 读取文件内容。
     *
     * @param file 待读取文件
     * @return 文本内容
     * @throws IOException 读取异常
     */
    String load(File file) throws IOException;
}
