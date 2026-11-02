package com.ahmtcnmn.agent.client;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ahmtcnmn.agent.Dto.Command.DtoCommand;
import com.ahmtcnmn.agent.Dto.Command.DtoCommandResultRequest;
import com.ahmtcnmn.agent.Libs.ColorLogger;
import com.ahmtcnmn.agent.detector.Interface.CommandExecutor;
import com.ahmtcnmn.agent.service.MyRestClient;
import com.ahmtcnmn.agent.service.QueueService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CommandPollTask {
    
    private final RestClient restClient;
    private final MyRestClient myRestClient;
    private final QueueService queueService;
    private final List<CommandExecutor> commandExecutors;
    private final ManagerConnection managerConnection;

    @Value("${manager.api-key}")
    private String apiKey;

    @Scheduled(fixedRate=10000)
    public void pollAndExecuteCommands(){
        DtoCommand[] pendingCommands;
        try{
            pendingCommands= restClient.get()
                .uri("/api/commands/pending")
                .header("X-API-KEY",apiKey)
                .retrieve()
                .body(DtoCommand[].class);
            managerConnection.up();
        }
        catch(Exception e){
            // Manager kapalıysa bunu ManagerConnection bir kez yazar; her 10 sn tekrar basmıyoruz
            managerConnection.down(e);
            return;
        }

        if (pendingCommands == null) return;
        for(DtoCommand command: pendingCommands){
            handleCommand(command);
        }
    }

    private void handleCommand(DtoCommand command) {
        String name = "#" + command.id() + " " + command.commandType();
        ColorLogger.info("KOMUT", name + " alındı (" + ColorLogger.cut(command.payload(), 40) + ")");

        Optional<CommandExecutor> executorOpt = commandExecutors.stream()
            .filter(executor -> executor.supports(command.commandType()))
            .findFirst();
        if(executorOpt.isEmpty()){
            ColorLogger.warn("KOMUT", name + " bilinmeyen komut, FAILED olarak bildiriliyor");
            sendResult(command.id(), "FAILED", "Bilinmeyen komut: " + command.commandType());
            return;
        }
        try{
            String result = executorOpt.get().execute(command.payload());
            ColorLogger.success("KOMUT", name + " tamamlandı → " + ColorLogger.cut(result, 60));
            sendResult(command.id(), "COMPLETED", result);
        }
        catch(Exception e){
            ColorLogger.error("KOMUT", name + " başarısız: " + ColorLogger.cut(e.getMessage(), 80));
            sendResult(command.id(), "FAILED", "Komut çalıştırma başarısız: " + e.getMessage());
        }
    }

    private void sendResult(Long commandId, String status, String result) {
        String uri = "/api/commands/" + commandId + "/result";
        try {
            myRestClient.postRestClient(uri, new DtoCommandResultRequest(status, result));
            ColorLogger.debug("KOMUT", "#" + commandId + " sonucu Manager'a iletildi (" + status + ")");
        } catch (Exception e) {
            managerConnection.down(e);
            queueService.savePendingRequest(uri, new DtoCommandResultRequest(status, result));
        }
    }
    

}
