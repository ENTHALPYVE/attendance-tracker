# Attendance Tracker

College attendance management web app: B.E. Computer Engineering mini project, University of Mumbai.

Teachers mark attendance on their phones: **theory** lectures for the whole class and **lab** sessions
batch-wise (B1 / B2 / B3 in different labs at the same time). The app works out subject-wise (theory, lab,
combined), day-wise and overall percentages, flags defaulters, and exports reports to PDF, Excel and CSV.
It runs in any browser and installs as an app on Android, iPhone / iPad, Windows and macOS (see section 7).

| | |
|---|---|
| **Backend** | Java 17+, Spring Boot 3 (Web, Data JPA, Security, Validation), MySQL 8 |
| **Frontend** | plain HTML, CSS and JavaScript (`fetch()` to a REST API), Chart.js, Lucide icons, IBM Plex Sans |
| **Files** | Apache POI (Excel import / export), OpenPDF (PDF reports) |
| **Devices** | responsive layout + installable PWA (manifest, service worker); Android APK via a Trusted Web Activity (`android/`) |

---

## 1. Setup

### Requirements

- **JDK 17 or newer** (`java -version`)
- **MySQL 8** on `localhost:3306`
- Maven is **not** needed: the project includes the Maven Wrapper (`mvnw.cmd` / `mvnw`).

### MySQL

The database `attendance_tracker` is created automatically on the first start, so only a MySQL login is needed.
Use `root`, or create a user:

```sql
CREATE USER 'attendance'@'localhost' IDENTIFIED BY 'attendance';
GRANT ALL PRIVILEGES ON attendance_tracker.* TO 'attendance'@'localhost';
```

### Settings (`src/main/resources/application.properties`)

Do not put passwords in this file (it goes to GitHub); see "Your own MySQL password" below.

| Property | Meaning |
|---|---|
| `spring.datasource.url` | MySQL connection (`createDatabaseIfNotExist=true`); host, port, database, user and password come from `MYSQL...` environment variables, with local defaults |
| `app.seed.admin-password`, `app.seed.teacher-password` | demo passwords, from `ADMIN_PASSWORD` / `TEACHER_PASSWORD` (default `admin123` / `teacher123`) |
| `spring.sql.init.mode=always` | runs `schema.sql` on every start; safe because it uses `CREATE TABLE IF NOT EXISTS` |
| `app.seed-demo-data=true` | fills an **empty** database with demo data (once) |

### Run

```bash
mvnw.cmd spring-boot:run        # Windows
./mvnw spring-boot:run          # Linux / macOS
```

Open <http://localhost:8080>.

**On a phone** (same Wi-Fi): open `http://<your-PC's-IP>:8080` (find the IP with `ipconfig`), allow port 8080
in the Windows firewall if asked. The browser menu's **Add to Home Screen** puts the app on the home screen.
The full offline-capable install (service worker) needs HTTPS or `localhost`; over plain `http://` on the
network the app works the same, only without the caching.

### Your own MySQL password

Create a file **`local.properties`** in the project folder (it is in `.gitignore`, so it never goes to GitHub):

```properties
spring.datasource.username=root
spring.datasource.password=your-mysql-password
```

### Deploy online for free (Render + Aiven)

The app reads all server settings from environment variables and has a `Dockerfile`, so it runs on any
host that builds Docker images. Free setup: the app on **Render** (free web service), the database on
**Aiven** (free MySQL, no credit card).

1. **GitHub:** create an empty repository (no README), then in the project folder:
   `git remote add origin https://github.com/<you>/attendance-tracker.git` and `git push -u origin main`.
2. **Aiven** ([aiven.io](https://aiven.io)): sign up -> **Create service** -> **MySQL** -> **Free plan**
   -> choose a region near you -> create. When it is running, the **Overview** page shows
   **Host, Port, User** (`avnadmin`), **Password** and **Database name** (`defaultdb`).
3. **Render** ([render.com](https://render.com)): sign in with GitHub -> **New** -> **Blueprint** -> choose the
   repository. Render reads `render.yaml` (a free Docker web service) and asks for the values:

   | Variable | Value |
   |---|---|
   | `MYSQLHOST`, `MYSQLPORT`, `MYSQLPASSWORD` | from the Aiven Overview page |
   | `ADMIN_PASSWORD` | a password of your choice for `admin@college.edu` |
   | `TEACHER_PASSWORD` | a password of your choice for the demo teachers |
   | `ANDROID_CERT_SHA256` | optional: the Android app's key fingerprint (see `android/README.md`) |

   (`ADMIN_PASSWORD` / `TEACHER_PASSWORD` are only used to create the demo accounts on the very first start.)
   (`MYSQLDATABASE=defaultdb`, `MYSQLUSER=avnadmin` and `COOKIE_SECURE=true` are already in `render.yaml`.)
   Instead of the `MYSQL...` variables you can set the whole connection as `DB_URL`
   (`jdbc:mysql://<host>:<port>/defaultdb?sslMode=REQUIRED&serverTimezone=Asia/Kolkata&rewriteBatchedStatements=true`),
   `DB_USERNAME` and `DB_PASSWORD`; when set, these win over the `MYSQL...` ones.
4. The first build takes about 5-10 minutes. The app is then live at `https://attendance-tracker-xxxx.onrender.com`
   (HTTPS, so "Add to Home Screen" installs it fully on phones). The demo data and logins are created
   on the first start; later starts keep the data.
5. Every `git push` deploys the new version.
6. **Own domain** (`attandtrackinger.qd.je`, registered at DigitalPlat FreeDomain): `render.yaml` already adds it to
   the Render service. In Render open the service -> **Settings** -> **Custom Domains** to see the DNS record it
   wants, then add that record in the DigitalPlat dashboard (DNS management of the domain). For a domain like
   this one (no `www.` in front) that is an **A record** for `@` pointing to the IP address Render shows.
   Click **Verify** in Render; the HTTPS certificate follows within minutes, and the app is at
   `https://attandtrackinger.qd.je`.

**Free plan limits:** the Render service sleeps after 15 minutes without visitors; the next visit wakes it,
which takes 1-2 minutes (open the link a few minutes before a demo). Aiven may power off a free database
that is not used for a long time (it sends an email first). If the database is off or unreachable, the app
cannot start and Render reports a failed deploy: power the service on again in Aiven, then click
**Manual Deploy -> Restart service** in Render. Free plans are for demos, not for a whole college.

**Keep it awake (free):** create a free [UptimeRobot](https://uptimerobot.com) HTTP(s) monitor for
`https://<your-site>/health` with a 5-minute interval. `/health` runs a tiny database query, so Render does
not put the app to sleep and Aiven does not power the database off as unused; UptimeRobot also emails you if
the site goes down. One always-on service uses about 744 of Render's 750 free hours a month, so keep only
one free Render service.

**Paid alternative (no sleeping): Railway.** New Project -> Deploy from GitHub repo, then Create -> Database ->
MySQL, and in the app's Variables set `MYSQLHOST=${{MySQL.MYSQLHOST}}` (and the same for `MYSQLPORT`,
`MYSQLDATABASE`, `MYSQLUSER`, `MYSQLPASSWORD`) plus `ADMIN_PASSWORD`, `TEACHER_PASSWORD`, `COOKIE_SECURE=true`;
then Settings -> Networking -> Generate Domain.

### Tests

```bash
mvnw.cmd test
```

105 tests. They use an in-memory H2 database in MySQL mode with the same `schema.sql` and demo data,
so MySQL is not needed for the tests.

### Default logins

| Role | Email | Password |
|---|---|---|
| Admin (HOD) | `admin@college.edu` | `admin123` |
| Teacher - AOA | `anita.deshmukh@college.edu` | `teacher123` |
| Teacher - DSGT | `rahul.kulkarni@college.edu` | `teacher123` |
| Teacher - Maths | `sneha.patil@college.edu` | `teacher123` |
| Teacher - COA | `vikram.joshi@college.edu` | `teacher123` |
| Teacher - Java Lab | `meera.iyer@college.edu` | `teacher123` |
| Teacher - EDP | `sanjay.shinde@college.edu` | `teacher123` |
| Teacher - ESE | `kavita.nair@college.edu` | `teacher123` |
| Teacher - AOA / COA labs | `amit.bhosale@college.edu` | `teacher123` |

### Demo data (`config/DataSeeder.java`)

- class **SE-CO-A**, batches **B1, B2, B3**, 20 students each (roll nos. 01-60)
- subjects AOA, DSGT, Maths, COA (theory + lab), Java, EDP, ESE (lab / practical)
- a Monday-Friday timetable. **Monday is the example day of the brief:**
  slots 1-3 AOA, DSGT, Maths for all; slot 4 B1 AOA Lab, B2 COA Lab, B3 Java Lab; slot 5 B1 free, B2 EDP, B3 ESE
- about 6 weeks of random attendance (a few students are defaulters), one holiday, a few cancelled sessions

To start again: `DROP DATABASE attendance_tracker;` and restart the app.

---

## 2. Using the app

### Admin (HOD / coordinator)

| Menu | What it does |
|---|---|
| **Classes** | add / rename / delete classes; open a class to manage **batches** and **students** (add, edit, move to another batch, remove) and **Upload** students from Excel / CSV |
| **Timetable** | weekly grid per class (days x slots); a slot holds one theory lecture **or** one lab per batch; warns when a teacher would be in two places at once |
| **Subjects / Teachers** | add, edit, delete (refused when it would break existing records) |
| **Settings** | attendance threshold (default 75%), "count free slots as present", semester start, **holidays** |
| **Day view** | any class on any date; click a session to open it, or a planned lecture up to 14 days ahead to **give it to a substitute** |
| **Reports** | class reports, student pages, PDF / Excel (see below) |

**Student upload:** an `.xlsx`, `.xls` or `.csv` file with the headings **Roll No, Name, Batch**.
A preview marks every problem (missing values, a roll number twice in the file or already in the class,
unknown batch). Nothing is saved until every row is correct. A template can be downloaded in the dialog.

### Teacher

1. **Today** shows "My sessions today" in time order (all classes): time, subject, Theory / Lab, class, batch, room, status.
2. Tap a session: theory lists the whole class, a lab only its batch, sorted by roll number.
   **Everyone starts as Present - tap a student to mark Absent** (tap again to undo).
   The count "18 present · 2 absent" is always visible. **Save** marks the session Done.
3. A saved session can be reopened and edited. **Mark as cancelled** for a lecture that did not happen.
   **Extra session** for a lecture that is not in the timetable.
4. Below the sessions, **My classes** has a card per subject / batch taught: average %, defaulters,
   sessions taken and sessions still **to mark** (tap a date to mark it).

A teacher can mark **only the sessions assigned to them** (checked in the backend) and sees reports only
for the classes / batches they teach.

### Reports

- **Class report**: tabs **Day, Week, Month, Overall, Custom**; one row per student with the % for each
  subject (tick **Theory / Lab** for T / L / C columns), overall %, status badge, "needs X more" / "can miss Y".
  Filter by batch, **Defaulters only**, click a heading to sort.
- **Downloads**: **PDF**, **Excel** (class report with theory / lab / combined per subject), **CSV**
  (one row per student, opens in any spreadsheet), **Defaulters (Excel)**, and **All classes (PDF)**.
- **Print**: the Print button (or Ctrl+P) prints just the report: menus, buttons and filters are hidden
  and the table headings repeat on every page.
- **Student page** (click a name): overall %, a **calendar** coloured by each day's %, subject table,
  a bar chart per subject and a weekly trend line chart (Chart.js).

---

## 3. Attendance rules

All the arithmetic is in **`service/calc/AttendanceCalculator.java`** (no database, no Spring), unit-tested in
`AttendanceCalculatorTest` with the examples from the brief.

Only sessions marked **Done (TAKEN)** count. Pending and cancelled sessions are ignored.

| Rule | Formula |
|---|---|
| Theory % | theory sessions attended / theory sessions held for the class |
| Lab % | lab sessions attended / lab sessions held for the student's batch |
| Combined % | (theory attended + lab attended) / (theory held + lab held) |
| Defaulter in a subject | theory % **or** lab % below the threshold |
| Day % | sessions attended that day / sessions held that day for the student's batch |
| Free slot | a slot where the student's batch has no session (B1 in slot 5 on Monday). If "count free slots as present" is on, each free slot counts as one attended session in day-wise and overall figures |
| Week / month / overall % | total attended / total held in the range |
| Status | below threshold = **Defaulter**, threshold to threshold + 5 = **At risk**, above = **Safe** |
| Sessions needed to reach 75% | `ceil(3t - 4p)` (t = held, p = present) |
| Sessions that can be missed at 75% | `floor(4p/3 - t)` |

Example (B3 student, Monday): 5 sessions attended = 100%; misses Java Lab = 4/5 = 80%.

For other thresholds T the general forms are used: needed = `ceil((T*t - 100p) / (100 - T))`,
can miss = `floor((100p - T*t) / T)`. Percentages are **cut** to one decimal (74.96 shows as 74.9, never as
"75.0" next to a Defaulter badge), and status is decided with whole numbers (`100*p < T*t`), so there are
no rounding errors.

---

## 4. How it is built

### Layers

```
Browser (HTML + JS)  --fetch JSON-->  Controller  -->  Service  -->  Repository  -->  MySQL
                                       (REST API)     (rules)       (Spring Data JPA)
```

- **Controllers** only receive requests and return DTOs; they never contain business rules.
- **Services** hold the rules (permissions, timetable rules, calculation, reports).
- **Repositories** are Spring Data JPA interfaces; queries are generated from method names or written in JPQL.
- **DTOs** (Java records) are what the browser sees; entities never leave the backend
  (for example the password hash is never sent).

### Project structure

```
src/main/java/com/college/attendance
├── config/        SecurityConfig (login, roles, CSRF), DataSeeder (demo data), ClockConfig
├── controller/    REST controllers (/api/...)
├── service/       business logic
│   └── calc/      AttendanceCalculator - all the formulas
├── repository/    Spring Data JPA repositories
├── model/         JPA entities (one per table) and enums
├── dto/           request / response records (dto/report: report shapes)
├── security/      logged-in user, user lookup, CSRF cookie filter, login rate limiting
├── exception/     error types and the JSON error handler
└── util/          roll-number sorting ("2" before "10"), text clean-up

src/main/resources
├── schema.sql              all tables
├── application.properties
└── static/                 the frontend
    ├── index.html          Today (teacher sessions + dashboard / admin summary)
    ├── session.html        marking attendance
    ├── class-day.html      day view (slots x batches)
    ├── reports.html        class report      student.html  student page
    ├── admin/*.html        classes, class, timetable, subjects, teachers, settings
    ├── css/app.css         the whole design (colour tokens at the top)
    ├── js/                 api.js (fetch + CSRF + error messages), layout.js (menu, change password),
    │                       ui.js (dialogs), sessions.js, pwa.js (service worker + offline bar)
    ├── js/pages/           one script per page (no inline scripts, see Security)
    ├── manifest.json, sw.js, offline.html, icons/   PWA

android/                    the Android app (Trusted Web Activity): twa-manifest.json, setup and build scripts
```

### Important classes (for the viva)

| Class | Role |
|---|---|
| `AttendanceCalculator` | every formula, pure functions |
| `ReportService` | loads a class's sessions and marks for a period and builds the day / range / student reports |
| `SessionGenerator` | creates a day's sessions from the timetable when the date is opened |
| `AttendanceService` | the student list of a session and saving the marks |
| `SessionService` | permission check (own session or admin), cancel / reopen, extra sessions, substitutes |
| `TimetableService` | timetable cell rules and teacher clash check |
| `StudentImportService` | reading Excel / CSV, the preview with errors, saving all-or-nothing |
| `ReportPdfService`, `ReportExcelService`, `ReportCsvService` | PDF (OpenPDF), Excel (Apache POI) and CSV files |
| `SecurityConfig` | session login, BCrypt, role rules, CSRF, security headers |
| `LoginRateLimitFilter`, `FailedAttemptLimiter` | lock a login for 15 minutes after too many wrong passwords |
| `AccountService` | changing your own password |
| `GlobalExceptionHandler` | every error as `{"message": ...}`; database outage = 503, details only in the log |

### Database

| Table | Holds |
|---|---|
| `users` | staff logins (ADMIN / TEACHER), BCrypt `password_hash` |
| `classes`, `batches`, `students` | SE-CO-A, B1-B3, students (`UNIQUE (class_id, roll_no)`) |
| `subjects` | code, name, `has_theory`, `has_lab` |
| `timetable_entries` | the weekly plan; THEORY rows have `batch_id` NULL, LAB rows a batch |
| `sessions` | a dated lecture / lab: PENDING / TAKEN / CANCELLED; `timetable_entry_id` NULL for extra sessions |
| `attendance` | one row per student per session: PRESENT / ABSENT (`UNIQUE (session_id, student_id)`) |
| `holidays` | dates on which the timetable sessions are cancelled |
| `settings` | threshold, count_free_slots_as_present, semester_start |

**How sessions come into being:** sessions are not created in advance. When a date is opened (a teacher's day,
the day view, a report, the dashboard), `SessionGenerator` creates that day's missing sessions for every class
from the timetable: never for future dates or before the semester start, as CANCELLED on a holiday, and
without duplicates. Changing a timetable entry removes its *unmarked* sessions from today onwards so they are
re-created; sessions already taken are never touched.

### Security

- Only staff log in; students are records. Login is Spring Security form login at `POST /api/auth/login`;
  the user is kept in the HTTP session (`JSESSIONID` cookie). Passwords are BCrypt hashes.
- `/api/admin/**` is for ADMIN only (`SecurityConfig`). Finer rules are in the services:
  a teacher may change only sessions assigned to them, and sees reports only for the classes / batches they teach.
- **CSRF**: the server sends a token in the `XSRF-TOKEN` cookie; `api.js` sends it back in the `X-XSRF-TOKEN`
  header on every POST / PUT / DELETE.
- Input is validated with Bean Validation (`@NotBlank`, `@Size`, ...); errors come back as
  `{"message": "...", "fieldErrors": {...}}` and are shown next to the fields.
- **SQL injection**: all database access goes through Spring Data JPA / prepared statements with parameters;
  no SQL is built by joining strings with user input.
- **Password guessing**: 5 wrong passwords for one email from one IP address, or 20 for one email from any
  address, lock that login for 15 minutes (HTTP 429). Unknown emails are treated the same, so the answer never
  reveals which emails exist. Changing your own password needs the current one and is limited the same way.
- **Security headers**: a Content-Security-Policy allows scripts only from this site and the two CDNs
  (Lucide, Chart.js), which also carry Subresource Integrity hashes; inline scripts, `eval`, plugins and
  framing are blocked. Also Referrer-Policy, Permissions-Policy, nosniff, X-Frame-Options and (over HTTPS) HSTS.
  This is why every page's script lives in `js/pages/`; a test fails if an inline script is added.
- **Offline**: attendance is never saved "later" in the background. Without a connection the app says so;
  unsaved marks are kept on the device (student ids only) until they are saved, discarded or the user logs out.
- **CSV files**: cells starting with `= + - @` get a leading `'` so a spreadsheet cannot run them as formulas.

### Main API endpoints

| Endpoint | Purpose |
|---|---|
| `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me` | login / logout / current user |
| `POST /api/auth/password` | change your own password (`{"currentPassword", "newPassword"}`) |
| `GET /api/me/sessions?date=` | a teacher's sessions for a date |
| `GET /api/classes/{id}/day?date=` | one class on one date |
| `GET` / `PUT /api/sessions/{id}/attendance` | student list / save (`{"absentStudentIds": [...]}`) |
| `POST /api/sessions/{id}/cancel`, `/reopen`; `POST /api/sessions/extra` | cancel, reopen, extra session |
| `GET /api/reports/classes/{id}/day`, `/range?period=WEEK\|MONTH\|OVERALL\|CUSTOM` | class reports |
| `GET /api/reports/students/{id}` | student page |
| `GET /api/reports/{day,range}.pdf`, `/{day,range,defaulters}.xlsx`, `/{day,range}.csv` | downloads (leave out `classId` for all classes) |
| `GET /.well-known/assetlinks.json` | links the Android app to the website (public) |
| `GET /api/dashboard/teacher` | teacher dashboard cards |
| `/api/admin/classes`, `/batches`, `/students`, `/subjects`, `/teachers`, `/timetable` | admin management |
| `/api/admin/holidays`, `PUT /api/admin/settings`, `/api/admin/sessions/...` | holidays, rules, substitutes |

---

## 5. Design decisions and notes

- **Theory and lab are one subject** with two session types, so the app can show theory %, lab % and combined %.
- **Marking is "everyone present, tap the absent ones"**: on a normal day a teacher taps only a few names.
  Only the absent students are sent to the server.
- **Who counts as "held" for a student:** the taken sessions in which the student was marked. Attendance is
  saved for everyone in the list at that time, so this equals "class theory + own batch's labs", and a
  student added later is not counted absent for sessions before they joined.
- `sessions` also stores `start_time`, `end_time` and `room` (copied from the timetable), so extra sessions
  have them too. `settings` uses `setting_key` / `setting_value` and `holidays` uses `holiday_date`
  because `key`, `value` and `date` are reserved / awkward words in SQL.
- In MySQL a UNIQUE key treats NULLs as different, so `UNIQUE (class_id, session_date, slot_no, batch_id)` does
  not stop two theory sessions (batch NULL) in one slot; the services check this (`SlotRules`).
- **Holidays** cancel the timetable sessions of that date (extra sessions are left alone); deleting the
  holiday reopens them (Done again if attendance had been saved).
- **Substitutes:** the admin can give any session to another teacher, including a lecture up to 14 days ahead
  (the session is then created early with the substitute; on the day the timetable does not create it again).
- The design follows the brief: warm off-white background, white cards, one muted green accent, status colours
  only for status, thin borders, IBM Plex Sans, tabular numbers, Lucide icons, no gradients.
  All colours are tokens at the top of `css/app.css`.
- PDF reports use the built-in Helvetica font, which cannot show Devanagari names.

## 6. Troubleshooting

| Problem | Fix |
|---|---|
| `Communications link failure` / `Connection refused` at start | MySQL is not running: start the MySQL service (`services.msc`) |
| `Access denied for user 'root'` | wrong password in `application.properties` |
| `Port 8080 was already in use` | another copy is running; stop it, or set `server.port=8081` |
| A phone shows an old page after an update | pull down to refresh; the service worker updates static files in the background |
| Start again with fresh demo data | `DROP DATABASE attendance_tracker;` then restart |

---

## 7. Install on phones, tablets and computers

Every device uses the same website, server and database, so attendance marked on a phone shows up on the
computer straight away.

| Device | How |
|---|---|
| **Android** | Chrome: menu ⋮ → **Install app** / **Add to Home screen**. Or install the APK (below). |
| **iPhone / iPad** | Safari: **Share** → **Add to Home Screen**. Opens full screen with the app icon. (There is no App Store app; that would need a Mac and an Apple Developer account.) |
| **Windows / macOS** | Chrome or Edge: the install icon in the address bar (or menu → **Install Attendance Tracker**). Safari on macOS: **File → Add to Dock**. |
| **Any browser** | just open https://attandtrackinger.qd.je |

**Android APK.** `android/` holds a Trusted Web Activity: a small Android app (made with Google's
Bubblewrap) that opens the website full screen. See [android/README.md](android/README.md) for building and
signing it.

**Offline.** Pages and styles are cached so the app opens on a bad connection, but loading and saving
attendance always needs the server. An orange bar says when the device is offline.
