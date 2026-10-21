package com.cricpulse;

import com.cricpulse.domain.MatchFormat;
import com.cricpulse.domain.Team;
import com.cricpulse.dto.Requests.CreateMatchRequest;
import com.cricpulse.repository.PlayerRepository;
import com.cricpulse.repository.TeamRepository;
import com.cricpulse.service.MatchService;
import com.cricpulse.service.MatchUpdateExecutor;
import com.cricpulse.simulation.MatchSimulator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiTest {

    @Autowired MockMvc mvc;
    @Autowired TeamRepository teams;
    @Autowired PlayerRepository players;
    @Autowired MatchService matches;
    @Autowired MatchUpdateExecutor executor;
    @Autowired MatchSimulator simulator;

    @Test
    void createStartScoreAndReadMatch() throws Exception {
        Team a = TestTeams.create(teams, players);
        Team b = TestTeams.create(teams, players);

        String body = """
                {"teamAId": %d, "teamBId": %d, "format": "T10", "autoSimulate": false}
                """.formatted(a.getId(), b.getId());
        String created = mvc.perform(post("/api/matches").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.summary.venue").value(a.getHomeGround()))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(created.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));

        mvc.perform(post("/api/matches/{id}/start", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("LIVE"))
                .andExpect(jsonPath("$.live.striker.name").exists());

        mvc.perform(post("/api/matches/{id}/deliveries", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runsOffBat\": 4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.innings[0].runs").value(4))
                .andExpect(jsonPath("$.recentBalls[0].outcome").value("4"));

        mvc.perform(get("/api/matches/{id}/commentary", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].commentary", containsString("FOUR")));

        mvc.perform(get("/api/matches/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.live[*].id", hasItem((int) id)));
    }

    @Test
    void validationAndRuleErrorsAreReported() throws Exception {
        mvc.perform(post("/api/matches").contentType(MediaType.APPLICATION_JSON).content("{\"format\":\"T20\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.teamAId").exists());

        mvc.perform(get("/api/matches/999999")).andExpect(status().isNotFound());

        Team a = TestTeams.create(teams, players);
        Team b = TestTeams.create(teams, players);
        Long id = matches.create(new CreateMatchRequest(a.getId(), b.getId(), null, MatchFormat.T20, null, false)).summary().id();
        mvc.perform(post("/api/matches/{id}/deliveries", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runsOffBat\": 1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("not live")));

        mvc.perform(post("/api/matches/{id}/deliveries", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runsOffBat\": 9}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statsReflectPlayedMatches() throws Exception {
        Team a = TestTeams.create(teams, players);
        Team b = TestTeams.create(teams, players);
        Long id = matches.create(new CreateMatchRequest(a.getId(), b.getId(), null, MatchFormat.T20, null, false)).summary().id();
        matches.start(id, null);
        executor.update(id, m -> simulator.fastForward(m, 2000));

        mvc.perform(get("/api/stats/leaderboard?limit=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mostRuns", hasSize(5)))
                .andExpect(jsonPath("$.mostWickets[0].wickets").isNumber());

        mvc.perform(get("/api/stats/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveries", greaterThan(100)));

        Long opener = players.findByTeamIdOrderByBattingOrderAsc(a.getId()).get(0).getId();
        mvc.perform(get("/api/players/{id}", opener))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").value(1))
                .andExpect(jsonPath("$.batting.innings").value(1))
                .andExpect(jsonPath("$.recent", hasSize(1)));

        mvc.perform(get("/api/teams/{id}/players", a.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(11)));
    }
}
