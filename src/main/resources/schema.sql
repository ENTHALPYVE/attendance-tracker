-- Attendance Tracker database schema (MySQL 8).
-- Runs on every start-up; IF NOT EXISTS / INSERT IGNORE make it safe to re-run.
-- Enum-like columns are stored as VARCHAR holding the Java enum name.

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,           -- ADMIN / TEACHER
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'TEACHER'))
);

CREATE TABLE IF NOT EXISTS classes (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,                     -- e.g. SE-CO-A
    CONSTRAINT uq_classes_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS batches (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT      NOT NULL,
    name     VARCHAR(20) NOT NULL,                 -- B1 / B2 / B3
    CONSTRAINT uq_batches_class_name UNIQUE (class_id, name),
    CONSTRAINT fk_batches_class FOREIGN KEY (class_id) REFERENCES classes (id)
);

CREATE TABLE IF NOT EXISTS students (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    roll_no  VARCHAR(20)  NOT NULL,
    name     VARCHAR(100) NOT NULL,
    class_id BIGINT       NOT NULL,
    batch_id BIGINT       NOT NULL,
    CONSTRAINT uq_students_class_roll UNIQUE (class_id, roll_no),
    CONSTRAINT fk_students_class FOREIGN KEY (class_id) REFERENCES classes (id),
    CONSTRAINT fk_students_batch FOREIGN KEY (batch_id) REFERENCES batches (id)
);

CREATE TABLE IF NOT EXISTS subjects (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    code       VARCHAR(20)  NOT NULL,              -- e.g. AOA
    name       VARCHAR(100) NOT NULL,              -- e.g. Analysis of Algorithms
    has_theory BOOLEAN      NOT NULL DEFAULT TRUE,
    has_lab    BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_subjects_code UNIQUE (code)
);

-- The weekly timetable. THEORY rows are for the whole class (batch_id NULL),
-- LAB rows are for one batch.
CREATE TABLE IF NOT EXISTS timetable_entries (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    class_id     BIGINT      NOT NULL,
    day_of_week  VARCHAR(10) NOT NULL,             -- MONDAY ... SATURDAY
    slot_no      INT         NOT NULL,
    start_time   TIME        NOT NULL,
    end_time     TIME        NOT NULL,
    subject_id   BIGINT      NOT NULL,
    session_type VARCHAR(10) NOT NULL,             -- THEORY / LAB
    batch_id     BIGINT      NULL,
    teacher_id   BIGINT      NOT NULL,
    room         VARCHAR(30) NULL,
    CONSTRAINT uq_tt_slot UNIQUE (class_id, day_of_week, slot_no, batch_id),
    CONSTRAINT fk_tt_class   FOREIGN KEY (class_id)   REFERENCES classes (id),
    CONSTRAINT fk_tt_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_tt_batch   FOREIGN KEY (batch_id)   REFERENCES batches (id),
    CONSTRAINT fk_tt_teacher FOREIGN KEY (teacher_id) REFERENCES users (id),
    CONSTRAINT chk_tt_type CHECK (
        (session_type = 'THEORY' AND batch_id IS NULL) OR
        (session_type = 'LAB'    AND batch_id IS NOT NULL))
);

-- An actual dated lecture/lab. Created from the timetable when a date is opened,
-- or added by a teacher as an extra session (timetable_entry_id NULL).
-- start_time, end_time and room are copied from the timetable entry so that
-- extra sessions (no entry) can still show a time and room.
CREATE TABLE IF NOT EXISTS sessions (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    timetable_entry_id BIGINT      NULL,
    class_id           BIGINT      NOT NULL,
    subject_id         BIGINT      NOT NULL,
    session_type       VARCHAR(10) NOT NULL,       -- THEORY / LAB
    batch_id           BIGINT      NULL,
    teacher_id         BIGINT      NOT NULL,       -- may differ from the entry (substitute)
    session_date       DATE        NOT NULL,
    slot_no            INT         NOT NULL,
    start_time         TIME        NULL,
    end_time           TIME        NULL,
    room               VARCHAR(30) NULL,
    status             VARCHAR(10) NOT NULL DEFAULT 'PENDING',  -- PENDING / TAKEN / CANCELLED
    CONSTRAINT uq_sessions_slot UNIQUE (class_id, session_date, slot_no, batch_id),
    CONSTRAINT fk_sessions_entry   FOREIGN KEY (timetable_entry_id) REFERENCES timetable_entries (id) ON DELETE SET NULL,
    CONSTRAINT fk_sessions_class   FOREIGN KEY (class_id)   REFERENCES classes (id),
    CONSTRAINT fk_sessions_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_sessions_batch   FOREIGN KEY (batch_id)   REFERENCES batches (id),
    CONSTRAINT fk_sessions_teacher FOREIGN KEY (teacher_id) REFERENCES users (id),
    CONSTRAINT chk_sessions_status CHECK (status IN ('PENDING', 'TAKEN', 'CANCELLED')),
    INDEX idx_sessions_teacher_date (teacher_id, session_date),
    INDEX idx_sessions_class_date (class_id, session_date)
);

CREATE TABLE IF NOT EXISTS attendance (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT      NOT NULL,
    student_id BIGINT      NOT NULL,
    status     VARCHAR(10) NOT NULL,               -- PRESENT / ABSENT
    CONSTRAINT uq_attendance UNIQUE (session_id, student_id),
    CONSTRAINT fk_attendance_session FOREIGN KEY (session_id) REFERENCES sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT chk_attendance_status CHECK (status IN ('PRESENT', 'ABSENT'))
);

CREATE TABLE IF NOT EXISTS holidays (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    holiday_date DATE         NOT NULL,
    reason       VARCHAR(150) NOT NULL,
    CONSTRAINT uq_holidays_date UNIQUE (holiday_date)
);

-- Simple key/value settings. ("key" and "value" are reserved words in MySQL,
-- hence the setting_ prefix.)
CREATE TABLE IF NOT EXISTS settings (
    setting_key   VARCHAR(50)  PRIMARY KEY,
    setting_value VARCHAR(100) NOT NULL
);

INSERT IGNORE INTO settings (setting_key, setting_value) VALUES ('threshold', '75');
INSERT IGNORE INTO settings (setting_key, setting_value) VALUES ('count_free_slots_as_present', 'false');
