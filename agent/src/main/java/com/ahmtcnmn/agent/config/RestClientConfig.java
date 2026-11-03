package com.ahmtcnmn.agent.config;

import java.net.http.HttpClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Manager'a giden HTTPS istemcisi. Manager'ın sertifikası self-signed olduğu için JDK'nın varsayılan
 * truststore'u ona güvenmez; yalnız "manager" SSL bundle'ındaki sertifikaya güvenilir (spring.ssl.bundle.pem.manager).
 * Hostname de doğrulanır: sertifika CN=localhost olduğundan base-url localhost olmalı (ters SSH tüneli zaten öyle).
 */
@Configuration
public class RestClientConfig {

    @Value("${manager.base-url}")
    private String managerBaseUrl;

    @Bean
    public RestClient restClient(SslBundles sslBundles) {
        HttpClient httpClient = HttpClient.newBuilder()
            .sslContext(sslBundles.getBundle("manager").createSslContext())
            .build();

        ClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);

        return RestClient.builder()
            .baseUrl(managerBaseUrl)
            .requestFactory(requestFactory)
            .build();
    }
}
