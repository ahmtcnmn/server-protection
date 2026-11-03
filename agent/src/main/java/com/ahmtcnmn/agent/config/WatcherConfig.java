package com.ahmtcnmn.agent.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ahmtcnmn.agent.watcher.FileLogWatcher;
import com.ahmtcnmn.agent.watcher.LogLineHandler;
import com.ahmtcnmn.agent.watcher.ProcessLogWatcher;

/**
 * İzlenen log kaynakları ve her kaynağın parser'ları (rules/parsers altındaki name'ler; sırayla denenir).
 * Hangi kuralların çalışacağını parser belirler: detector YAML'ındaki "parsers" listesi.
 */
@Configuration
public class WatcherConfig {

    @Bean
    public FileLogWatcher nginxLogWatcher(
            @Value("${agent.log-file-path}") String filePath,
            @Value("${agent.log-parsers:nginx-access}") List<String> parsers,
            LogLineHandler handler) {
        return new FileLogWatcher(filePath, parsers, handler);
    }

    @Bean
    public FileLogWatcher dbLogWatcher(
            @Value("${agent.db-log-file-path}") String filePath,
            @Value("${agent.db-log-parsers:raw-line}") List<String> parsers,
            LogLineHandler handler) {
        return new FileLogWatcher(filePath, parsers, handler);
    }

    @Bean
    public ProcessLogWatcher processLogWatcher(
            @Value("${agent.process-command}") String command,
            @Value("${agent.process-parsers:raw-line}") List<String> parsers,
            LogLineHandler handler) {
        return new ProcessLogWatcher(List.of(command.split(" ")), parsers, handler);
    }
}
