package com.ahmtcnmn.agent.watcher;

import java.io.File;
import java.time.Duration;

import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;
import com.ahmtcnmn.agent.Libs.ColorLogger;
import com.ahmtcnmn.agent.client.HeartbeatTask;
import com.ahmtcnmn.agent.detector.Interface.Detector;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;


public class FileLogWatcher extends TailerListenerAdapter {
    
    private final Detector detector;
    private final HeartbeatTask heartbeatTask;   
    private final String filePath;

    private Tailer tailer;

    public FileLogWatcher(String filePath, Detector detector, HeartbeatTask heartbeatTask) {
        this.filePath = filePath;
        this.detector = detector;
        this.heartbeatTask = heartbeatTask;
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
        ColorLogger.info("İZLEME", filePath + " izleniyor → " + detector.eventType());
    }
    
    @Override
    public void handle(String line) {
        fileMissingLogged = false;
        ColorLogger.debug("LOG", filePath + ": " + ColorLogger.cut(line, 120));
        if (detector.isSuspicious(line)) {
            ColorLogger.warn("TESPİT", detector.eventType() + " | " + ColorLogger.cut(line, 100));
            heartbeatTask.sendDetectedEvent(line, detector.eventType());
        }
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
