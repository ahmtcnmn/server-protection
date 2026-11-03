package com.ahmtcnmn.manager.dto.Dashboard;

public record DtoDashboardSummary(long totalAgents,
    long onlineAgents,
    long eventsTotal,
    long criticalEventsToday,
    long activeBlocks) {
    
}
