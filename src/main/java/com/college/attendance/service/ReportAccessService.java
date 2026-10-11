package com.college.attendance.service;

import com.college.attendance.exception.ForbiddenException;
import com.college.attendance.model.SessionType;
import com.college.attendance.repository.ClassSessionRepository;
import com.college.attendance.repository.TimetableEntryRepository;
import com.college.attendance.security.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Who may see which report.
 *  - Admin: every class, every batch.
 *  - Teacher: a class in which they teach theory -> the whole class;
 *             a class in which they only take labs -> only those batches.
 * "Teaches" means: appears in the timetable or has taken a session there (e.g. as a substitute).
 */
@Service
@Transactional(readOnly = true)
public class ReportAccessService {

    /** Batch ids a user may see in a class; ALL_BATCHES means no restriction. */
    public static final Set<Long> ALL_BATCHES = Collections.unmodifiableSet(new HashSet<>());

    private final TimetableEntryRepository entryRepository;
    private final ClassSessionRepository sessionRepository;

    public ReportAccessService(TimetableEntryRepository entryRepository, ClassSessionRepository sessionRepository) {
        this.entryRepository = entryRepository;
        this.sessionRepository = sessionRepository;
    }

    /**
     * classId -> visible batch ids (ALL_BATCHES for the whole class). Null for an admin (everything).
     */
    public Map<Long, Set<Long>> teacherScope(AppUserPrincipal user) {
        if (user.isAdmin()) {
            return null;
        }
        Map<Long, Set<Long>> scope = new HashMap<>();
        List<Object[]> rows = new ArrayList<>(entryRepository.teachingOf(user.getId()));
        rows.addAll(sessionRepository.teachingOf(user.getId()));
        for (Object[] row : rows) {
            Long classId = (Long) row[0];
            if (row[1] == SessionType.THEORY) {
                scope.put(classId, ALL_BATCHES);
            } else if (scope.get(classId) != ALL_BATCHES) {
                scope.computeIfAbsent(classId, k -> new HashSet<>()).add((Long) row[2]);
            }
        }
        return scope;
    }

    /** Visible batches of one class; throws 403 if the user may not see the class at all. */
    public Set<Long> visibleBatches(AppUserPrincipal user, Long classId) {
        Map<Long, Set<Long>> scope = teacherScope(user);
        if (scope == null) {
            return ALL_BATCHES;
        }
        Set<Long> batches = scope.get(classId);
        if (batches == null) {
            throw new ForbiddenException("You can only see reports of classes you teach.");
        }
        return batches;
    }

    public static boolean isVisible(Set<Long> visibleBatches, Long batchId) {
        return visibleBatches == ALL_BATCHES || visibleBatches.contains(batchId);
    }
}
