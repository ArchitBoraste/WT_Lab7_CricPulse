package com.cricpulse.dto;

import com.cricpulse.domain.Player;
import com.cricpulse.domain.PlayerRole;

public record PlayerDto(Long id, String name, PlayerRole role, Long teamId, String teamShort, String teamColor,
                        int battingOrder, String battingStyle, String bowlingStyle) {

    public static PlayerDto of(Player p) {
        return new PlayerDto(p.getId(), p.getName(), p.getRole(), p.getTeam().getId(), p.getTeam().getShortName(),
                p.getTeam().getColor(), p.getBattingOrder(), p.getBattingStyle(), p.getBowlingStyle());
    }
}
