package github.chains;

import com.google.cloud.translate.v2.Translate;
import com.google.cloud.translate.v2.TranslateOptions;
import com.google.cloud.translate.v2.Translation;
import com.google.cloud.translate.v2.Language;
import com.google.cloud.translate.v2.Detection;
import com.google.cloud.translate.v2.TranslateException;

public class TestTranslateV2 {
    public void testOldApi() {
        try {
            Translate translate = TranslateOptions.getDefaultInstance().getService();
            
            // Old v2 API usage
            Translation translation = translate.translate(
                "Hello world",
                Translate.TranslateOption.targetLanguage("es"),
                Translate.TranslateOption.sourceLanguage("en")
            );
            
            Detection detection = translate.detect("Hola mundo");
            Language language = detection.getLanguage();
            
            System.out.println("Translated: " + translation.getTranslatedText());
            System.out.println("Detected language: " + language.getCode());
        } catch (TranslateException e) {
            System.err.println("Translation error: " + e.getMessage());
        }
    }
}