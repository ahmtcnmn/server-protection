package com.ahmtcnmn.manager.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ahmtcnmn.manager.model.User;
import com.ahmtcnmn.manager.repository.UserRepository;

import lombok.RequiredArgsConstructor;


@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.initial-username}")
    private String adminUsername;

    @Value("${admin.initial-password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername(adminUsername).isEmpty()) {
            // id elle verilmez: @GeneratedValue(IDENTITY) üretir. setId(1L) boş DB'de
            // Hibernate'in kaydı "var olan" sanıp merge etmesine ve çökmesine yol açıyordu.
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setRole("ADMIN");
            userRepository.save(admin);
            System.out.println(">>> Admin kullanıcı oluşturuldu");
        }
    }
}
