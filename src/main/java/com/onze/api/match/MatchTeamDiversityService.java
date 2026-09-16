package com.onze.api.match;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.onze.api.match.TeamDiversityOptimizer.Decision;
import com.onze.api.match.TeamDiversityOptimizer.WeightedHistory;
import com.onze.api.match.TeamModels.MatchTeamsResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchTeamDiversityService {

    private static final int RECENT_MATCH_LIMIT = 5;

    private final FootballMatchRepository matchRepository;
    private final MatchTeamAssignmentRepository assignmentRepository;
    private final MatchTeamGenerationHistoryRepository historyRepository;

    public MatchTeamDiversityService(
            FootballMatchRepository matchRepository,
            MatchTeamAssignmentRepository assignmentRepository,
            MatchTeamGenerationHistoryRepository historyRepository) {
        this.matchRepository = matchRepository;
        this.assignmentRepository = assignmentRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public DiversityResult apply(
            UUID matchId,
            MatchTeamsResponse generated,
            MatchTeamsResponse previous) {
        FootballMatch match = matchRepository.findById(matchId)
                .orElseThrow(MatchService.MatchNotFoundException::new);

        List<MatchTeamGenerationHistory> currentHistory = historyRepository
                .findAllByMatchIdOrderByGenerationNumberDesc(matchId);
        int generationNumber = currentHistory.stream()
                .mapToInt(MatchTeamGenerationHistory::getGenerationNumber)
                .max()
                .orElse(0) + 1;

        List<String> currentSignatures = currentHistory.stream()
                .map(MatchTeamGenerationHistory::getDivisionSignature)
                .toList();
        String previousSignature = TeamDiversityOptimizer.signature(previous);
        List<WeightedHistory> recentHistory = recentHistory(match.getGroupId(), matchId);

        Decision decision = TeamDiversityOptimizer.choose(
                generated,
                previousSignature,
                currentSignatures,
                recentHistory,
                matchId,
                generationNumber);

        if (decision.changedFromGenerated()) {
            applyTeams(matchId, decision.teamByParticipant());
        }

        historyRepository.save(new MatchTeamGenerationHistory(
                matchId,
                match.getGroupId(),
                generationNumber,
                decision.signature()));

        String notice = null;
        if (previousSignature != null && decision.repeatedPrevious()) {
            notice = "Não foi encontrada outra combinação com equilíbrio equivalente.";
        } else if (previousSignature == null && decision.repeatedRecent()) {
            notice = "Para preservar o equilíbrio, foi necessário repetir uma divisão recente.";
        }

        return new DiversityResult(decision.changedFromGenerated(), notice);
    }

    private List<WeightedHistory> recentHistory(UUID groupId, UUID currentMatchId) {
        List<MatchTeamGenerationHistory> history = historyRepository
                .findAllByGroupIdOrderByCreatedAtDesc(groupId);
        Map<UUID, MatchTeamGenerationHistory> latestByMatch = new LinkedHashMap<>();
        for (MatchTeamGenerationHistory item : history) {
            if (item.getMatchId().equals(currentMatchId)) {
                continue;
            }
            latestByMatch.putIfAbsent(item.getMatchId(), item);
            if (latestByMatch.size() >= RECENT_MATCH_LIMIT) {
                break;
            }
        }

        List<MatchTeamGenerationHistory> recent = new ArrayList<>(latestByMatch.values());
        List<WeightedHistory> result = new ArrayList<>(recent.size());
        int weight = recent.size();
        for (MatchTeamGenerationHistory item : recent) {
            result.add(new WeightedHistory(item.getDivisionSignature(), weight--));
        }
        return List.copyOf(result);
    }

    private void applyTeams(UUID matchId, Map<String, Integer> teamByParticipant) {
        List<MatchTeamAssignment> assignments = assignmentRepository
                .findAllByMatchIdOrderByTeamNumberAscCreatedAtAsc(matchId);
        Map<String, MatchTeamAssignment> byParticipant = assignments.stream()
                .collect(Collectors.toMap(
                        assignment -> participantKey(
                                assignment.getParticipantType(),
                                assignment.getParticipantId()),
                        Function.identity()));

        for (Map.Entry<String, Integer> entry : teamByParticipant.entrySet()) {
            MatchTeamAssignment assignment = byParticipant.get(entry.getKey());
            if (assignment != null && assignment.getTeamNumber() != entry.getValue()) {
                assignment.rebalanceToTeam(entry.getValue());
            }
        }
        assignmentRepository.saveAll(assignments);
    }

    private String participantKey(TeamParticipantType type, UUID participantId) {
        return type.name() + ":" + participantId;
    }

    public record DiversityResult(boolean changedFromGenerated, String notice) {
    }
}
