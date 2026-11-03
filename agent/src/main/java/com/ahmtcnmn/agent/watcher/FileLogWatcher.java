package com.ahmtcnmn.agent.watcher;

import java.io.File;
import java.time.Duration;
import java.util.List;

import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;
import com.ahmtcnmn.agent.Libs.ColorLogger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;


public class FileLogWatcher extends TailerListenerAdapter {
    
    private final String filePath;
    private final List<String> parsers;
    private final LogLineHandler handler;

    private Tailer tailer;

    public FileLogWatcher(String filePath, List<String> parsers, LogLineHandler handler) {
        this.filePath = filePath;
        this.parsers = parsers;
        this.handler = handler;
    }

    private boolean fileMissingLogged = false;

    @PostConstruct
    public void start() {
        File file = new File(filePath);
        this.tailer = Tailer.builder()
                .setFile(file)
                .setTailerListener(this)          
                .setDelayDuration(Duration.ofMillis(1000))
                .setTailFromEnd(true)
                .get();
        ColorLogger.info("İZLEME", filePath + " izleniyor → parser " + parsers);
    }
    
    @Override
    public void handle(String line) {
        fileMissingLogged = false;
        handler.handle(filePath, line, parsers);
    } 

    @Override
    public void fileNotFound() {
        
        if (!fileMissingLogged) {
            ColorLogger.warn("İZLEME", "Dosya bulunamadı, oluşması bekleniyor: " + filePath);
            fileMissingLogged = true;
        }
    }

    @Override
    public void fileRotated() {
        ColorLogger.info("İZLEME", "Dosya döndürüldü (rotate), baştan okunuyor: " + filePath);
    }

    @Override
    public void handle(Exception ex) {
        ColorLogger.error("İZLEME", filePath + " okunamadı: " + ColorLogger.cut(ex.getMessage(), 80));
    }

    @PreDestroy
    private void stopLogWatcher() {
        if (tailer != null) {
            tailer.close();            // ← uygulama/restart kapanırken temizle
            ColorLogger.info("KAPAT", "İzleme durduruldu: " + filePath);
        }
    }
    
}
