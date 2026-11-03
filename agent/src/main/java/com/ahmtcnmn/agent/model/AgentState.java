package com.ahmtcnmn.agent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Agent'ın kalıcı küçük durum bilgileri (anahtar-değer). Ör. decision stream imleci. */
@Entity
@Table(name = "agent_state")
@Getter
@Setter
@NoArgsConstructor
public class AgentState {

    public static final String DECISION_CURSOR = "decision.lastChangeId";

    @Id
    @Column(name = "state_key", length = 64)
    private String key;

    @Column(name = "state_value", nullable = false)
    private String value;

    public AgentState(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
