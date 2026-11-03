package com.ahmtcnmn.agent.detector.Interface;

import java.util.List;

import com.ahmtcnmn.engine.Detection;
import com.ahmtcnmn.engine.ParsedLine;

/**
 * Tespit stratejisi. İmza kuralları YAML'dan gelir (YamlDetector); regex'le ifade edilemeyen tespitler
 * (ör. ML) bu arayüzü implement eden ayrı Java sınıfları olur. Her satır tüm Detector bean'lerinden geçer.
 */
public interface Detector {
    List<Detection> detect(ParsedLine line);
}
