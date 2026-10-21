package com.cricpulse.dto;

import com.cricpulse.domain.ExtraType;
import com.cricpulse.domain.MatchFormat;
import com.cricpulse.domain.TossDecision;
import com.cricpulse.domain.WicketType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class Requests {

    private Requests() {
    }

    public record CreateMatchRequest(
            @NotNull Long teamAId,
            @NotNull Long teamBId,
            @Size(max = 120) String venue,
            @NotNull MatchFormat format,
            /* null = start as soon as possible */
            Instant startTime,
            /* null = true */
            Boolean autoSimulate) {
    }

    public record StartMatchRequest(Long tossWinnerId, TossDecision decision) {
    }

    /**
     * One delivery entered by a scorer.
     * <ul>
     *   <li>{@code runsOffBat}: runs credited to the batter (0-6)</li>
     *   <li>{@code extraRuns}: runs run/conceded as byes on top of the one-run wide/no-ball penalty,
     *       or the bye / leg-bye count</li>
     * </ul>
     */
    public record DeliveryRequest(
            @Min(0) @Max(6) int runsOffBat,
            ExtraType extraType,
            @Min(0) @Max(6) int extraRuns,
            boolean wicket,
            WicketType wicketType,
            Long dismissedPlayerId,
            Long fielderId,
            Long bowlerId,
            @Size(max = 500) String commentary) {

        public ExtraType extraTypeOrNone() {
            return extraType == null ? ExtraType.NONE : extraType;
        }
    }

    public record SimulationControlRequest(Boolean paused, Boolean autoSimulate) {
    }
}
