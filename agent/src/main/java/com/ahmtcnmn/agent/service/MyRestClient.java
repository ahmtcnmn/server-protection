package com.ahmtcnmn.agent.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.ahmtcnmn.agent.Libs.ColorLogger;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyRestClient {
    private final RestClient restClient;

    @Value("${manager.api-key}")
    private String apiKey;

    // Başarı/başarısızlık mesajını çağıran sınıf yazar (ne gönderildiğini o biliyor).
    // Burada yalnız HTTP detayı var, o da agent.log.debug=true ise görünür.
    public <T> void postRestClient(String uri, T body) {
        long start = System.currentTimeMillis();
        ResponseEntity<Void> response = restClient.post()
                .uri(uri)
                .header("X-API-KEY", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        ColorLogger.debug("HTTP", "POST " + uri + " → " + response.getStatusCode().value()
                + " (" + (System.currentTimeMillis() - start) + " ms)");
    }

    public void postRawJson(String uri, String rawJsonBody) {
        long start = System.currentTimeMillis();
        ResponseEntity<Void> response = restClient.post()
            .uri(uri)
            .header("X-API-KEY", apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(rawJsonBody)
            .retrieve()
            .toBodilessEntity();
        ColorLogger.debug("HTTP", "POST " + uri + " (kuyruktan) → " + response.getStatusCode().value()
                + " (" + (System.currentTimeMillis() - start) + " ms)");
    }

}
