package com.cricpulse;

import com.cricpulse.domain.*;
import com.cricpulse.dto.MatchDtos.*;
import com.cricpulse.dto.Requests.*;
import com.cricpulse.repository.PlayerRepository;
import com.cricpulse.repository.TeamRepository;
import com.cricpulse.service.MatchService;
import com.cricpulse.service.MatchUpdateExecutor;
import com.cricpulse.service.ScoringException;
import com.cricpulse.simulation.MatchSimulator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ScoringEngineTest {

    @Autowired TeamRepository teams;
    @Autowired PlayerRepository players;
    @Autowired MatchService matches;
    @Autowired MatchUpdateExecutor executor;
    @Autowired MatchSimulator simulator;

    private Long newLiveMatch(MatchFormat format) {
        Team a = TestTeams.create(teams, players);
        Team b = TestTeams.create(teams, players);
        Long id = matches.create(new CreateMatchRequest(a.getId(), b.getId(), null, format, null, false)).summary().id();
        matches.start(id, new StartMatchRequest(a.getId(), TossDecision.BAT));
        return id;
    }

    private static DeliveryRequest runs(int r) {
        return new DeliveryRequest(r, ExtraType.NONE, 0, false, null, null, null, null, null);
    }

    @Test
    void oddRunsRotateStrikeAndOverEndSwapsAgain() {
        Long id = newLiveMatch(MatchFormat.T20);
        MatchDetailDto d = matches.detail(id);
        Long opener1 = d.live().striker().playerId();
        Long opener2 = d.live().nonStriker().playerId();

        d = matches.recordDelivery(id, runs(1));
        assertThat(d.live().striker().playerId()).isEqualTo(opener2);
        assertThat(d.live().needsBowler()).isFalse();

        for (int i = 0; i < 5; i++) d = matches.recordDelivery(id, runs(0));
        // End of over: strike changes ends and a new bowler is needed.
        assertThat(d.live().striker().playerId()).isEqualTo(opener1);
        assertThat(d.live().needsBowler()).isTrue();
        assertThat(d.summary().innings().get(0).overs()).isEqualTo("1.0");
        assertThat(d.live().bowler().maidens()).isZero();
        assertThat(d.live().bowler().runs()).isEqualTo(1);
    }

    @Test
    void wideAddsPenaltyButIsNotABallFaced() {
        Long id = newLiveMatch(MatchFormat.T20);
        MatchDetailDto d = matches.recordDelivery(id,
                new DeliveryRequest(0, ExtraType.WIDE, 0, false, null, null, null, null, null));
        InningsScoreDto score = d.summary().innings().get(0);
        assertThat(score.runs()).isEqualTo(1);
        assertThat(score.legalBalls()).isZero();
        assertThat(d.live().striker().balls()).isZero();
        assertThat(d.scorecards().get(0).extras().wides()).isEqualTo(1);
        assertThat(d.recentBalls().get(0).outcome()).isEqualTo("Wd");
    }

    @Test
    void noBallCreditsBatterAndCountsAsBallFaced() {
        Long id = newLiveMatch(MatchFormat.T20);
        MatchDetailDto d = matches.recordDelivery(id,
                new DeliveryRequest(4, ExtraType.NO_BALL, 0, false, null, null, null, null, null));
        assertThat(d.summary().innings().get(0).runs()).isEqualTo(5);
        assertThat(d.summary().innings().get(0).legalBalls()).isZero();
        assertThat(d.live().striker().runs()).isEqualTo(4);
        assertThat(d.live().striker().balls()).isEqualTo(1);
        assertThat(d.live().bowler().runs()).isEqualTo(5);
    }

    @Test
    void wicketBringsInNextBatterAndCreditsBowler() {
        Long id = newLiveMatch(MatchFormat.T20);
        Long opener = matches.detail(id).live().striker().playerId();
        MatchDetailDto d = matches.recordDelivery(id,
                new DeliveryRequest(0, ExtraType.NONE, 0, true, WicketType.BOWLED, null, null, null, null));
        assertThat(d.summary().innings().get(0).wickets()).isEqualTo(1);
        assertThat(d.live().striker().playerId()).isNotEqualTo(opener);
        assertThat(d.live().bowler().wickets()).isEqualTo(1);
        assertThat(d.scorecards().get(0).fallOfWickets()).hasSize(1);
        assertThat(d.scorecards().get(0).batting().get(0).dismissal()).startsWith("b ");
    }

    @Test
    void illegalRequestsAreRejected() {
        Long id = newLiveMatch(MatchFormat.T20);
        assertThatThrownBy(() -> matches.recordDelivery(id,
                new DeliveryRequest(2, ExtraType.WIDE, 0, false, null, null, null, null, null)))
                .isInstanceOf(ScoringException.class);
        assertThatThrownBy(() -> matches.recordDelivery(id,
                new DeliveryRequest(0, ExtraType.WIDE, 0, true, WicketType.LBW, null, null, null, null)))
                .isInstanceOf(ScoringException.class);

        // Same bowler cannot bowl two overs in a row.
        MatchDetailDto d = null;
        for (int i = 0; i < 6; i++) d = matches.recordDelivery(id, runs(0));
        Long lastBowler = d.live().lastBowlerId();
        assertThatThrownBy(() -> matches.recordDelivery(id,
                new DeliveryRequest(0, ExtraType.NONE, 0, false, null, null, null, lastBowler, null)))
                .isInstanceOf(ScoringException.class);
        // The rejected ball left no trace.
        assertThat(matches.detail(id).summary().innings().get(0).legalBalls()).isEqualTo(6);
    }

    @Test
    void scheduledMatchCannotBeScored() {
        Team a = TestTeams.create(teams, players);
        Team b = TestTeams.create(teams, players);
        Long id = matches.create(new CreateMatchRequest(a.getId(), b.getId(), null, MatchFormat.T10, null, false)).summary().id();
        assertThatThrownBy(() -> matches.recordDelivery(id, runs(1))).isInstanceOf(ScoringException.class);
    }

    @Test
    void simulatedMatchesProduceConsistentScorecards() {
        for (int n = 0; n < 8; n++) {
            Long id = newLiveMatch(n % 2 == 0 ? MatchFormat.T20 : MatchFormat.T10);
            MatchDetailDto d = executor.update(id, m -> simulator.fastForward(m, 2000));

            assertThat(d.summary().status()).isEqualTo(MatchStatus.COMPLETED);
            assertThat(d.summary().result()).isNotBlank();
            assertThat(d.playerOfMatch()).isNotNull();
            assertThat(d.scorecards()).hasSize(2);
            assertThat(d.live()).isNull();

            ScorecardDto first = d.scorecards().get(0);
            ScorecardDto second = d.scorecards().get(1);
            assertThat(second.target()).isEqualTo(first.runs() + 1);

            for (ScorecardDto card : d.scorecards()) {
                int batRuns = card.batting().stream().mapToInt(BatterDto::runs).sum();
                int bowlRuns = card.bowling().stream().mapToInt(BowlerDto::runs).sum();
                int bowlerWickets = card.bowling().stream().mapToInt(BowlerDto::wickets).sum();
                int legalBalls = d.summary().innings().get(card.number() - 1).legalBalls();

                assertThat(batRuns + card.extras().total()).isEqualTo(card.runs());
                assertThat(bowlRuns).isEqualTo(card.runs() - card.extras().byes() - card.extras().legByes());
                assertThat(bowlerWickets).isLessThanOrEqualTo(card.wickets());
                assertThat(card.wickets()).isLessThanOrEqualTo(10);
                assertThat(legalBalls).isLessThanOrEqualTo(d.summary().oversPerInnings() * 6);
                assertThat(card.fallOfWickets()).hasSize(card.wickets());
                assertThat(card.overSummaries().get(card.overSummaries().size() - 1).cumulativeRuns()).isEqualTo(card.runs());
                assertThat(card.batting().size() + card.didNotBat().size()).isEqualTo(11);
                // No bowler exceeds the quota of overs.
                card.bowling().forEach(b -> assertThat(Integer.parseInt(b.overs().split("\\.")[0]))
                        .isLessThanOrEqualTo(d.summary().oversPerInnings() / 5));
            }

            // Second innings stops as soon as the target is reached.
            if (second.runs() >= second.target()) {
                assertThat(d.summary().winnerId()).isEqualTo(second.battingTeam().id());
            } else if (second.runs() < first.runs()) {
                assertThat(d.summary().winnerId()).isEqualTo(first.battingTeam().id());
            }
        }
    }
}
