package com.onze.api.match;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.onze.api.match.MatchModels.CreateMatchRequest;
import com.onze.api.match.MatchModels.AddMatchReplacementRequest;
import com.onze.api.match.MatchModels.AddRentalGoalkeeperRequest;
import com.onze.api.match.MatchModels.BulkResolvePaymentSettlementsRequest;
import com.onze.api.match.MatchModels.MatchResponse;
import com.onze.api.match.MatchModels.PlayerCreditResponse;
import com.onze.api.match.MatchModels.ResolvePaymentSettlementRequest;
import com.onze.api.match.MatchModels.UpdateAttendanceRequest;
import com.onze.api.match.MatchModels.UpdateMatchGoalkeeperRequest;
import com.onze.api.match.MatchModels.UpdateMatchPlayerConfigurationRequest;
import com.onze.api.match.MatchModels.AddGuestRequest;
import com.onze.api.match.MatchModels.UpdateGuestTechnicalProfileRequest;
import com.onze.api.match.MatchModels.GuestTechnicalProfileResponse;
import com.onze.api.match.TeamModels.MatchTeamsResponse;
import com.onze.api.match.TeamModels.MatchTeamImageResponse;
import com.onze.api.match.TeamModels.UpdateTeamAssignmentRequest;
import com.onze.api.match.LiveMatchModels.LiveMatchStateResponse;
import com.onze.api.match.LiveMatchModels.LiveMatchSummaryResponse;
import com.onze.api.match.LiveMatchModels.UpdateLiveScoreRequest;
import com.onze.api.match.LiveMatchModels.CreateGoalEventRequest;
import com.onze.api.match.LiveMatchModels.CreateGoalEventResponse;
import com.onze.api.match.LiveMatchModels.CreateCardEventRequest;
import com.onze.api.match.LiveMatchModels.CreateCardEventResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MatchController {

    private final MatchService matchService;
    private final MatchLifecycleService lifecycleService;
    private final MatchTeamService teamService;
    private final MatchTeamImageService teamImageService;
    private final MatchPlayerConfigurationService playerConfigurationService;
    private final LiveMatchService liveMatchService;
    private final LiveMatchFeedService liveMatchFeedService;
    private final LiveMatchStreamService liveMatchStreamService;

    public MatchController(
            MatchService matchService,
            MatchLifecycleService lifecycleService,
            MatchTeamService teamService,
            MatchTeamImageService teamImageService,
            MatchPlayerConfigurationService playerConfigurationService,
            LiveMatchService liveMatchService,
            LiveMatchFeedService liveMatchFeedService,
            LiveMatchStreamService liveMatchStreamService) {
        this.matchService = matchService;
        this.lifecycleService = lifecycleService;
        this.teamService = teamService;
        this.teamImageService = teamImageService;
        this.playerConfigurationService = playerConfigurationService;
        this.liveMatchService = liveMatchService;
        this.liveMatchFeedService = liveMatchFeedService;
        this.liveMatchStreamService = liveMatchStreamService;
    }

    @PutMapping("/api/matches/{matchId}/live/start")
    public MatchResponse startLiveMatch(Authentication authentication, @PathVariable UUID matchId) {
        liveMatchService.start(authentication.getName(), matchId);
        return matchService.get(authentication.getName(), matchId);
    }

    @PutMapping("/api/matches/{matchId}/live/finish")
    public MatchResponse finishLiveMatch(Authentication authentication, @PathVariable UUID matchId) {
        liveMatchService.finish(authentication.getName(), matchId);
        return matchService.get(authentication.getName(), matchId);
    }

    @PutMapping("/api/matches/{matchId}/live/reset")
    public MatchResponse resetLiveMatch(Authentication authentication, @PathVariable UUID matchId) {
        liveMatchService.reset(authentication.getName(), matchId);
        return matchService.get(authentication.getName(), matchId);
    }

    @GetMapping("/api/matches/{matchId}/live")
    public ResponseEntity<LiveMatchStateResponse> getLiveMatch(
            Authentication authentication,
            @PathVariable UUID matchId,
            @RequestParam(required = false) Long version) {
        return liveMatchService.getIfChanged(authentication.getName(), matchId, version)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/api/matches/live")
    public List<LiveMatchSummaryResponse> listLiveMatches(Authentication authentication) {
        return liveMatchFeedService.list(authentication.getName());
    }

    @GetMapping(value = "/api/matches/live/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamLiveMatches(
            Authentication authentication,
            @RequestParam(required = false) UUID matchId) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(liveMatchStreamService.subscribe(authentication.getName(), matchId));
    }

    @PutMapping("/api/matches/{matchId}/live/score")
    public LiveMatchStateResponse updateLiveScore(
            Authentication authentication,
            @PathVariable UUID matchId,
            @Valid @RequestBody UpdateLiveScoreRequest request) {
        return liveMatchService.updateScore(
                authentication.getName(), matchId, request.sideNumber(), request.score());
    }

    @PostMapping("/api/matches/{matchId}/live/goals")
    public ResponseEntity<CreateGoalEventResponse> createGoal(
            Authentication authentication,
            @PathVariable UUID matchId,
            @Valid @RequestBody CreateGoalEventRequest request) {
        CreateGoalEventResponse response = liveMatchService.createGoal(authentication.getName(), matchId,
                request.scorerAssignmentId(), request.assistAssignmentId(), request.penalty());
        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/api/matches/{matchId}/live/cards")
    public ResponseEntity<CreateCardEventResponse> createCard(
            Authentication authentication,
            @PathVariable UUID matchId,
            @Valid @RequestBody CreateCardEventRequest request) {
        CreateCardEventResponse response = liveMatchService.createCard(authentication.getName(), matchId,
                request.playerAssignmentId(), request.cardType());
        return ResponseEntity.status(201).body(response);
    }

    @DeleteMapping("/api/matches/{matchId}/live/goals/{eventId}")
    public LiveMatchStateResponse deleteGoal(
            Authentication authentication,
            @PathVariable UUID matchId,
            @PathVariable UUID eventId) {
        return liveMatchService.deleteGoal(authentication.getName(), matchId, eventId);
    }

    @DeleteMapping("/api/matches/{matchId}/live/cards/{eventId}")
    public LiveMatchStateResponse deleteCard(
            Authentication authentication,
            @PathVariable UUID matchId,
            @PathVariable UUID eventId) {
        return liveMatchService.deleteCard(authentication.getName(), matchId, eventId);
    }

    @PostMapping("/api/groups/{groupId}/matches")
    public ResponseEntity<MatchResponse> create(
            Authentication authentication,
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateMatchRequest request) {
        MatchResponse response = matchService.create(authentication.getName(), groupId, request);
        return ResponseEntity.created(URI.create("/api/matches/" + response.id())).body(response);
    }

    @GetMapping("/api/matches/upcoming")
    public List<MatchResponse> listUpcoming(Authentication authentication) {
        lifecycleService.openDueAttendances();
        return matchService.listUpcoming(authentication.getName());
    }

    @GetMapping("/api/groups/{groupId}/matches")
    public List<MatchResponse> listForGroup(
            Authentication authentication,
            @PathVariable UUID groupId) {
        lifecycleService.openDueAttendances();
        return matchService.listForGroup(authentication.getName(), groupId);
    }

    @GetMapping("/api/groups/{groupId}/credits")
    public List<PlayerCreditResponse> listPlayerCredits(
            Authentication authentication,
            @PathVariable UUID groupId) {
        lifecycleService.openDueAttendances();
        return matchService.listPlayerCredits(authentication.getName(), groupId);
    }

    @GetMapping("/api/matches/{matchId}")
    public MatchResponse get(
            Authentication authentication,
            @PathVariable UUID matchId) {
        lifecycleService.openDueAttendances();
        return matchService.get(authentication.getName(), matchId);
    }

    @PutMapping("/api/matches/{matchId}/attendance")
    public MatchResponse updateAttendance(
            Authentication authentication,
            @PathVariable UUID matchId,
            @Valid @RequestBody UpdateAttendanceRequest request) {
        lifecycleService.openDueAttendances();
        return matchService.updateAttendance(authentication.getName(), matchId, request.status());
    }

    @PutMapping("/api/matches/{matchId}/payment/reported")
    public MatchResponse reportPayment(
            Authentication authentication,
            @PathVariable UUID matchId) {
        lifecycleService.openDueAttendances();
        return matchService.reportPayment(authentication.getName(), matchId);
    }

    @PutMapping("/api/matches/{matchId}/payments/{playerUserId}/confirm")
    public MatchResponse confirmPayment(
            Authentication authentication,
            @PathVariable UUID matchId,
            @PathVariable UUID playerUserId) {
        lifecycleService.openDueAttendances();
        return matchService.confirmPayment(
                authentication.getName(),
                matchId,
                playerUserId);
    }

    @PutMapping("/api/matches/{matchId}/payments/{playerUserId}/settlement")
    public MatchResponse resolvePaymentSettlement(
            Authentication authentication,
            @PathVariable UUID matchId,
            @PathVariable UUID playerUserId,
            @Valid @RequestBody ResolvePaymentSettlementRequest request) {
        return matchService.resolvePaymentSettlement(
                authentication.getName(),
                matchId,
                playerUserId,
                request.resolution());
    }

    @PutMapping("/api/matches/{matchId}/payment-settlements")
    public MatchResponse resolvePaymentSettlements(
            Authentication authentication,
            @PathVariable UUID matchId,
            @Valid @RequestBody BulkResolvePaymentSettlementsRequest request) {
        return matchService.resolvePaymentSettlements(
                authentication.getName(),
                matchId,
                request.playerUserIds(),
                request.resolution());
    }

    @PutMapping("/api/matches/{matchId}/replacements/{departedUserId}")
    public MatchResponse addReplacement(
            Authentication authentication,
            @PathVariable UUID matchId,
            @PathVariable UUID departedUserId,
            @Valid @RequestBody AddMatchReplacementRequest request) {
        lifecycleService.openDueAttendances();
        return matchService.addReplacement(
                authentication.getName(),
                matchId,
                departedUserId,
                request.replacementUserId());
    }

    @PutMapping("/api/matches/{matchId}/goalkeepers/{playerUserId}")
    public MatchResponse updateGoalkeeper(
            Authentication authentication,
            @PathVariable UUID matchId,
            @PathVariable UUID playerUserId,
            @Valid @RequestBody UpdateMatchGoalkeeperRequest request) {
        lifecycleService.openDueAttendances();
        return matchService.updateGoalkeeper(
                authentication.getName(),
                matchId,
                playerUserId,
                request.isGoalkeeper());
    }

    @PostMapping("/api/matches/{matchId}/rental-goalkeepers")
    public MatchResponse addRentalGoalkeeper(
            Authentication authentication,
            @PathVariable UUID matchId,
            @Valid @RequestBody AddRentalGoalkeeperRequest request) {
        lifecycleService.openDueAttendances();
        return matchService.addRentalGoalkeeper(
                authentication.getName(),
                matchId,
                request.displayName());
    }

    @DeleteMapping("/api/matches/{matchId}/rental-goalkeepers/{rentalGoalkeeperId}")
    public MatchResponse removeRentalGoalkeeper(
            Authentication authentication,
            @PathVariable UUID matchId,
            @PathVariable UUID rentalGoalkeeperId) {
        return matchService.removeRentalGoalkeeper(
                authentication.getName(),
                matchId,
                rentalGoalkeeperId);
    }

    @PutMapping("/api/matches/{matchId}/player-configuration")
    public MatchResponse updatePlayerConfiguration(
            Authentication authentication,
            @PathVariable UUID matchId,
            @Valid @RequestBody UpdateMatchPlayerConfigurationRequest request) {
        return playerConfigurationService.update(
                authentication.getName(),
                matchId,
                request.modality(),
                request.minimumPlayers(),
                request.maxPlayers());
    }

    @PostMapping("/api/matches/{matchId}/guests")
    public MatchResponse addGuest(
            Authentication authentication,
            @PathVariable UUID matchId,
            @Valid @RequestBody AddGuestRequest request) {
        return matchService.addGuest(
                authentication.getName(),
                matchId,
                request.displayName(),
                request.primaryPosition(),
                request.secondaryPosition(),
                request.ratings());
    }

    @DeleteMapping("/api/matches/{matchId}/guests/{guestId}")
    public MatchResponse removeGuest(
            Authentication authentication,
            @PathVariable UUID matchId,
            @PathVariable UUID guestId) {
        return matchService.removeGuest(authentication.getName(), matchId, guestId);
    }

    @GetMapping("/api/matches/{matchId}/guests/{guestId}/technical-profile")
    public GuestTechnicalProfileResponse getGuestTechnicalProfile(
            Authentication authentication,
            @PathVariable UUID matchId,
            @PathVariable UUID guestId) {
        return matchService.getGuestTechnicalProfile(authentication.getName(), matchId, guestId);
    }

    @PutMapping("/api/matches/{matchId}/guests/{guestId}/technical-profile")
    public GuestTechnicalProfileResponse updateGuestTechnicalProfile(
            Authentication authentication,
            @PathVariable UUID matchId,
            @PathVariable UUID guestId,
            @Valid @RequestBody UpdateGuestTechnicalProfileRequest request) {
        return matchService.updateGuestTechnicalProfile(
                authentication.getName(), matchId, guestId, request.ratings());
    }

    @PostMapping("/api/matches/{matchId}/teams/generate")
    public MatchTeamsResponse generateTeams(
            Authentication authentication,
            @PathVariable UUID matchId) {
        return teamService.generate(authentication.getName(), matchId);
    }

    @GetMapping("/api/matches/{matchId}/teams")
    public MatchTeamsResponse getTeams(
            Authentication authentication,
            @PathVariable UUID matchId) {
        return teamService.get(authentication.getName(), matchId);
    }

    @GetMapping("/api/matches/{matchId}/teams/images")
    public List<MatchTeamImageResponse> getTeamImages(
            Authentication authentication,
            @PathVariable UUID matchId) {
        return teamImageService.list(authentication.getName(), matchId);
    }

    @PostMapping(
            value = "/api/matches/{matchId}/teams/{teamNumber}/image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MatchTeamImageResponse uploadTeamImage(
            Authentication authentication,
            @PathVariable UUID matchId,
            @PathVariable int teamNumber,
            @RequestPart("image") MultipartFile image) {
        return teamImageService.upload(authentication.getName(), matchId, teamNumber, image);
    }

    @PutMapping("/api/matches/{matchId}/teams/assignments/{assignmentId}")
    public MatchTeamsResponse updateTeamAssignment(
            Authentication authentication,
            @PathVariable UUID matchId,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody UpdateTeamAssignmentRequest request) {
        return teamService.updateAssignment(
                authentication.getName(), matchId, assignmentId,
                request.teamNumber(), request.assignedRole());
    }

    @DeleteMapping("/api/matches/{matchId}")
    public ResponseEntity<Void> cancelOccurrence(
            Authentication authentication,
            @PathVariable UUID matchId) {
        matchService.cancelOccurrence(authentication.getName(), matchId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/match-series/{seriesId}")
    public ResponseEntity<Void> endSeries(
            Authentication authentication,
            @PathVariable UUID seriesId) {
        matchService.endSeries(authentication.getName(), seriesId);
        return ResponseEntity.noContent().build();
    }
}
