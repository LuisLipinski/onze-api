package com.onze.api.match;

import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class MatchCapacityService {

    private final MatchAttendanceRepository attendanceRepository;
    private final MatchRentalGoalkeeperRepository rentalGoalkeeperRepository;

    public MatchCapacityService(
            MatchAttendanceRepository attendanceRepository,
            MatchRentalGoalkeeperRepository rentalGoalkeeperRepository) {
        this.attendanceRepository = attendanceRepository;
        this.rentalGoalkeeperRepository = rentalGoalkeeperRepository;
    }

    public long occupiedSpots(UUID matchId) {
        return attendanceRepository.countByMatchIdAndStatus(matchId, AttendanceStatus.GOING)
                + rentalGoalkeeperRepository.countByMatchId(matchId);
    }

    public boolean isFull(FootballMatch match) {
        return occupiedSpots(match.getId()) >= match.getMaxPlayers();
    }
}
