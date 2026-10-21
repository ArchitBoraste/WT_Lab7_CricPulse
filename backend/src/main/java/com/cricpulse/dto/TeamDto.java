package com.cricpulse.dto;

import com.cricpulse.domain.Team;

public record TeamDto(Long id, String name, String shortName, String color, String homeGround) {

    public static TeamDto of(Team t) {
        return t == null ? null : new TeamDto(t.getId(), t.getName(), t.getShortName(), t.getColor(), t.getHomeGround());
    }
}
