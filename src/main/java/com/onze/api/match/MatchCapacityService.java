package com.onze.api.match;

import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class MatchCapacityService {

    private final MatchAttendanceRepository attendanceRepository;
    private final MatchRentalGoalkeeperRepository rentalGoalkeeperRepository;
    private final MatchGuestRepository guestRepository;

    public MatchCapacityService(
            MatchAttendanceRepository attendanceRepository,
            MatchRentalGoalkeeperRepository rentalGoalkeeperRepository,
            MatchGuestRepository guestRepository) {
        this.attendanceRepository = attendanceRepository;
        this.rentalGoalkeeperRepository = rentalGoalkeeperRepository;
        this.guestRepository = guestRepository;
    }

    public long occupiedSpots(UUID matchId) {
        return attendanceRepository.countByMatchIdAndStatus(matchId, AttendanceStatus.GOING)
                + rentalGoalkeeperRepository.countByMatchId(matchId)
                + guestRepository.countByMatchId(matchId);
    }

    public boolean isFull(FootballMatch match) {
        return occupiedSpots(match.getId()) >= match.getMaxPlayers();
    }
}
