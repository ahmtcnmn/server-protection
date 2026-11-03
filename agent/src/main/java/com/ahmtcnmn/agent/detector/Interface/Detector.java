package com.ahmtcnmn.agent.detector.Interface;

public interface Detector {
    boolean isSuspicious(String logLine);
    String eventType();

    /** Event'in önem derecesi: 1 (düşük) – 5 (kritik). Panelde renk ve dashboard'daki "critical" sayısı buna göre. */
    int severity();
}
