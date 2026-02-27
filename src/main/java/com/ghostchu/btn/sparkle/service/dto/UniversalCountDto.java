package com.ghostchu.btn.sparkle.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UniversalCountDto {
    private long days30;
    private long days15;
    private long days7;
    private long days3;
    private long days1;
}
