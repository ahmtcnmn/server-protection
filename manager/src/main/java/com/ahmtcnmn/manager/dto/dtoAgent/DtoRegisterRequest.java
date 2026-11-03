package com.ahmtcnmn.manager.dto.dtoAgent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DtoRegisterRequest(
    @NotBlank(message = "Hostname boş olamaz")
    @Size(max = 100, message = "Hostname en fazla 100 karakter olabilir")
    @Pattern(regexp = "^\\s*[A-Za-z0-9._-]+\\s*$", message = "Hostname yalnız harf, rakam, nokta, tire ve alt çizgi içerebilir")
    String hostName
) {}
