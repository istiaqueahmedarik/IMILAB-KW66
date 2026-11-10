package dev.kw66.glass;
import java.io.File;
public final class LocalAi implements AiEngine {
    public boolean available(){return false;}
    public void begin(){}
    public String reply(File model,String instruction,String prompt){throw new IllegalStateException("Install the Gemma build to use a local model. Core keeps the watch companion lightweight.");}
    public void close(){}
}
