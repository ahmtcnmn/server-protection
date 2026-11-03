package com.ahmtcnmn.agent.watcher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

import com.ahmtcnmn.agent.Libs.ColorLogger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;


public class ProcessLogWatcher {

    private final List<String> command;
    private final List<String> parsers;
    private final LogLineHandler handler;
    private Process process;
    private Thread readerThread;

    public ProcessLogWatcher(List<String> command, List<String> parsers, LogLineHandler handler) {
        this.command = command;
        this.parsers = parsers;
        this.handler = handler;
    }

    @PostConstruct
    public void start() {
        // ProcessBuilder ile process'i başlat
        // readerThread'i başlat — içinde BufferedReader ile satır satır oku
        // her satırda: handler.handle(...) → parser + detector'lar + Manager'a event
        try {
            // Örnek komut: Windows için "ping localhost -t", Mac/Linux için "ping localhost"
            ProcessBuilder builder = new ProcessBuilder(command);
            
            // Hata çıktılarını (stderr) normal çıktılara (stdout) yönlendirip tek kanaldan okuyoruz
            builder.redirectErrorStream(true); 
            
            process = builder.start();
            ColorLogger.info("İZLEME", "'" + String.join(" ", command) + "' çıktısı izleniyor → parser " + parsers);

            // 2. Okuma işlemini ayrı bir Thread'e devrediyoruz
            readerThread = new Thread(() -> {
                // Try-with-resources kullanarak BufferedReader'ın işi bitince otomatik kapanmasını sağlıyoruz
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String satir;
                    
                    // readLine() metodu yeni satır gelene kadar bekler.
                    // Eğer Thread'e interrupt sinyali gelirse döngüyü kırıp çıkıyoruz!
                    while (!Thread.currentThread().isInterrupted() && (satir = reader.readLine()) != null) {
                        handler.handle(command.get(0), satir, parsers);
                    }
                    
                } catch (Exception e) {
                    ColorLogger.debug("İZLEME", "Süreç okuması kesildi: " + e.getMessage());
                }
                
                if (!Thread.currentThread().isInterrupted()) {
                    ColorLogger.warn("İZLEME", "Süreç sonlandı, izleme durdu: '" + String.join(" ", command) + "'");
                }
            });
            readerThread.setDaemon(true);
            // Thread'i başlat
            readerThread.start();

        } catch (Exception e) {
            ColorLogger.error("İZLEME", "Süreç başlatılamadı '" + String.join(" ", command) + "': " + e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        // process.destroy(), thread'i durdur

        // Önce okuyucu Thread'e "Lütfen işini bırak ve kapan" sinyali gönderiyoruz
        if (readerThread != null && readerThread.isAlive()) {
            readerThread.interrupt();
        }

        // Sonra harici çalıştırdığımız süreci (Process) sonlandırıyoruz
        if (process != null && process.isAlive()) {
            process.destroy(); // İşletim sistemine programı kapatmasını söyler
            
            // Eğer inat edip kapanmazsa diye zorla kapatma seçeneği de vardır:
            // process.destroyForcibly(); 
        }
        
        ColorLogger.info("KAPAT", "İzleme durduruldu: '" + String.join(" ", command) + "'");
    }
}
    
