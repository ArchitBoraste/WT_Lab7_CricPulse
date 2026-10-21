package com.cricpulse.dto;

import com.cricpulse.domain.Player;

public record PlayerRefDto(Long id, String name, String teamShort) {

    public static PlayerRefDto of(Player p) {
        return p == null ? null : new PlayerRefDto(p.getId(), p.getName(), p.getTeam().getShortName());
    }
}
