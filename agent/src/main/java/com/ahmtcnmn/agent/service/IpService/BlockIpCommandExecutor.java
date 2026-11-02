package com.ahmtcnmn.agent.service.IpService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ahmtcnmn.agent.Dto.responder.DtoEventRequest;
import com.ahmtcnmn.agent.Libs.ColorLogger;
import com.ahmtcnmn.agent.client.ManagerConnection;
import com.ahmtcnmn.agent.detector.Interface.CommandExecutor;
import com.ahmtcnmn.agent.service.MyRestClient;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BlockIpCommandExecutor implements CommandExecutor {

    private final IpBlockManager ipBlockManager;
    private final MyRestClient myRestClient;
    private final UfwFirewall ufwFirewall;

    @Value("${manager.event-url-sql}") 
    private String managerEventUrl;


    @Override
    public boolean supports(String commandType) {
        return "BLOCK_IP".equalsIgnoreCase(commandType);
    }

    @Override
    public String execute(String payload) throws Exception {
        String ip = payload == null ? null : payload.trim();
        // Aynı IP için gelen tekrar komutlarda ufw'yi yeniden çalıştırma
        if (ip != null && ipBlockManager.isBlocked(ip)) {
            return "Zaten engelli: " + ip;
        }
        UfwFirewall.Result result = ufwFirewall.block(ip);

        if (result.ok()) {
            ipBlockManager.recordBlock(ip);
            ColorLogger.success("ENGEL", ip + " ufw ile engellendi (" + ipBlockManager.blockDurationText()
                    + " sonra kaldırılacak) | ufw: " + ColorLogger.cut(result.output(), 50));
            killOpenConnections(ip);
            notifyBlocked(ip);
            return "IP engellendi: " + ip;
        } else {
            throw new RuntimeException("ufw başarısız (exit " + result.exitCode() + "): "
                    + ColorLogger.cut(result.output(), 80));
        }
    }
    // Engel konduktan sonra açık kalan bağlantıları keser; başarısız olsa da engel geçerli kalır
    private void killOpenConnections(String ip) {
        try {
            UfwFirewall.Result kill = ufwFirewall.killConnections(ip);
            if (kill.ok()) {
                // ss -K çıktısı: başlık satırı + kesilen her bağlantı için bir satır
                long count = kill.output().lines().skip(1).filter(l -> !l.isBlank()).count();
                ColorLogger.success("ENGEL", ip + " için açık bağlantı kesildi: " + count);
            } else {
                ColorLogger.warn("ENGEL", "Açık bağlantılar kesilemedi (exit " + kill.exitCode() + "): "
                        + ColorLogger.cut(kill.output(), 60));
            }
        } catch (Exception e) {
            ColorLogger.warn("ENGEL", "Açık bağlantılar kesilemedi: " + ColorLogger.cut(e.getMessage(), 60));
        }
    }

    private void notifyBlocked(String ip) {
        try {
            myRestClient.postRestClient(managerEventUrl, DtoEventRequest.builder()
                .eventType("IP_BLOCKED")
                .severity(3)
                .rawData("Otomatik olarak engellendi: " + ip)
                .sourceIp(ip)
                .build());
            ColorLogger.debug("ENGEL", "IP_BLOCKED event'i Manager'a bildirildi: " + ip);
        } catch (Exception e) {
            ColorLogger.warn("ENGEL", "IP_BLOCKED bildirimi gönderilemedi: " + ManagerConnection.describe(e));
            // 📝 isterseniz queueService.savePendingRequest(...) ile kuyruğa da alabilirsiniz
        }
    }
    
}
