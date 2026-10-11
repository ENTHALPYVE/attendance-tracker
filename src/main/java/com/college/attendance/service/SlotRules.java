package com.college.attendance.service;

import com.college.attendance.model.ClassSession;
import com.college.attendance.model.SessionType;

import java.util.List;

/**
 * Who may share a slot of a class on one date:
 *  - a THEORY session needs the slot to be empty (it is for the whole class);
 *  - a LAB session needs no theory session in the slot and no other session for its batch.
 */
final class SlotRules {

    private SlotRules() {
    }

    /** True if a new session of this type / batch fits next to the sessions already in the slot. */
    static boolean fits(List<ClassSession> inSlot, SessionType type, Long batchId) {
        if (type == SessionType.THEORY) {
            return inSlot.isEmpty();
        }
        return inSlot.stream().noneMatch(s ->
                s.getSessionType() == SessionType.THEORY
                        || (s.getBatch() != null && s.getBatch().getId().equals(batchId)));
    }
}
