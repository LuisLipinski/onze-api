-- Preserve an existing on-field goalkeeper in each team and move any extras
-- from older generated lineups to the bench.
INSERT INTO match_team_reserves (assignment_id, match_id, created_at)
SELECT goalkeeper.id, goalkeeper.match_id, NOW()
FROM (
    SELECT assignment.id, assignment.match_id,
           ROW_NUMBER() OVER (
               PARTITION BY assignment.match_id, assignment.team_number
               ORDER BY CASE WHEN reserve.assignment_id IS NULL THEN 0 ELSE 1 END,
                        CASE WHEN assignment.participant_type = 'RENTAL_GOALKEEPER' THEN 0 ELSE 1 END,
                        assignment.created_at,
                        assignment.id
           ) AS goalkeeper_number
    FROM match_team_assignments assignment
    LEFT JOIN match_team_reserves reserve ON reserve.assignment_id = assignment.id
    WHERE assignment.assigned_role = 'GOALKEEPER'
) goalkeeper
WHERE goalkeeper.goalkeeper_number > 1
ON CONFLICT (assignment_id) DO NOTHING;
