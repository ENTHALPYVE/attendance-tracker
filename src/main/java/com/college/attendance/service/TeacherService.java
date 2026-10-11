package com.college.attendance.service;

import com.college.attendance.dto.TeacherDto;
import com.college.attendance.dto.TeacherRequest;
import com.college.attendance.exception.BadRequestException;
import com.college.attendance.exception.NotFoundException;
import com.college.attendance.model.Role;
import com.college.attendance.model.User;
import com.college.attendance.repository.ClassSessionRepository;
import com.college.attendance.repository.TimetableEntryRepository;
import com.college.attendance.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.college.attendance.util.Text.clean;

/** Teacher accounts. Passwords are stored only as BCrypt hashes. */
@Service
@Transactional
public class TeacherService {

    static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository userRepository;
    private final TimetableEntryRepository timetableRepository;
    private final ClassSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;

    public TeacherService(UserRepository userRepository, TimetableEntryRepository timetableRepository,
                          ClassSessionRepository sessionRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.timetableRepository = timetableRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<TeacherDto> list() {
        return userRepository.findByRoleOrderByName(Role.TEACHER).stream().map(TeacherDto::from).toList();
    }

    public TeacherDto create(TeacherRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("Another user already has the email " + email + ".");
        }
        String password = request.password() == null ? "" : request.password();
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new BadRequestException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        User teacher = new User(clean(request.name()), email, passwordEncoder.encode(password), Role.TEACHER);
        return TeacherDto.from(userRepository.save(teacher));
    }

    /** Updates name and email; the password is changed only if a new one is given. */
    public TeacherDto update(Long id, TeacherRequest request) {
        User teacher = find(id);
        String email = request.email().trim().toLowerCase();
        if (!email.equalsIgnoreCase(teacher.getEmail()) && userRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("Another user already has the email " + email + ".");
        }
        teacher.setName(clean(request.name()));
        teacher.setEmail(email);
        String password = request.password();
        if (password != null && !password.isEmpty()) {
            if (password.length() < MIN_PASSWORD_LENGTH) {
                throw new BadRequestException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
            }
            teacher.setPasswordHash(passwordEncoder.encode(password));
        }
        return TeacherDto.from(teacher);
    }

    /** A teacher who appears in the timetable or has sessions cannot be deleted (it would break history). */
    public void delete(Long id) {
        User teacher = find(id);
        if (timetableRepository.existsByTeacherId(id) || sessionRepository.existsByTeacherId(id)) {
            throw new BadRequestException(teacher.getName()
                    + " is assigned in the timetable or has taken sessions, so cannot be deleted. "
                    + "Reassign those first.");
        }
        userRepository.delete(teacher);
    }

    /** Finds a user with the TEACHER role (admins cannot be edited here). */
    public User find(Long id) {
        return userRepository.findById(id)
                .filter(u -> u.getRole() == Role.TEACHER)
                .orElseThrow(() -> new NotFoundException("Teacher not found."));
    }
}
