package dev.kw66.glass;
import java.io.File;
public interface AiEngine {
    boolean available();
    void begin();
    String reply(File model,String instruction,String prompt) throws Exception;
    void close();
}
