package com.college.attendance.service;

import com.college.attendance.dto.ImportConfirmRequest;
import com.college.attendance.dto.ImportPreview;
import com.college.attendance.dto.ImportRow;
import com.college.attendance.exception.BadRequestException;
import com.college.attendance.model.Batch;
import com.college.attendance.model.CollegeClass;
import com.college.attendance.model.Student;
import com.college.attendance.repository.BatchRepository;
import com.college.attendance.repository.StudentRepository;
import com.college.attendance.util.RollNumbers;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static com.college.attendance.util.Text.clean;

/**
 * Bulk upload of students from an Excel (.xlsx / .xls) or CSV file with the columns
 * Roll No, Name, Batch.
 *
 * Step 1 - preview(): read the file and check every row; nothing is saved.
 * Step 2 - save():    the browser sends the previewed rows back; they are checked again
 *                     and saved together (all or nothing).
 */
@Service
@Transactional
public class StudentImportService {

    static final int MAX_ROWS = 500;

    private final ClassService classService;
    private final BatchRepository batchRepository;
    private final StudentRepository studentRepository;

    public StudentImportService(ClassService classService, BatchRepository batchRepository,
                                StudentRepository studentRepository) {
        this.classService = classService;
        this.batchRepository = batchRepository;
        this.studentRepository = studentRepository;
    }

    /** A row as read from the file, before checking. */
    record RawRow(int rowNo, String rollNo, String name, String batch) {
    }

    // ---------------------------------------------------------------- step 1: preview

    @Transactional(readOnly = true)
    public ImportPreview preview(Long classId, MultipartFile file) {
        CollegeClass c = classService.findClass(classId);
        List<String> fileErrors = new ArrayList<>();
        List<RawRow> rows = List.of();
        try {
            rows = readFile(file, fileErrors);
        } catch (IOException | RuntimeException e) {
            fileErrors.add("The file could not be read. Save it as .xlsx or .csv and try again.");
        }
        if (fileErrors.isEmpty() && rows.isEmpty()) {
            fileErrors.add("The file has no student rows below the heading row.");
        }
        if (rows.size() > MAX_ROWS) {
            fileErrors.add("The file has " + rows.size() + " rows; at most " + MAX_ROWS + " can be uploaded at once.");
        }
        return ImportPreview.of(check(c, rows), fileErrors);
    }

    // ---------------------------------------------------------------- step 2: save

    /** Checks the rows again and saves them. Returns the preview; nothing is saved if any row has an error. */
    public ImportPreview save(Long classId, ImportConfirmRequest request) {
        CollegeClass c = classService.findClass(classId);
        List<RawRow> rows = new ArrayList<>();
        int rowNo = 2;   // same numbering as the file: row 1 is the heading
        for (ImportConfirmRequest.Row r : request.rows()) {
            rows.add(new RawRow(rowNo++, clean(r.rollNo()), clean(r.name()), clean(r.batch())));
        }
        List<String> fileErrors = rows.size() > MAX_ROWS
                ? List.of("At most " + MAX_ROWS + " students can be uploaded at once.")
                : List.of();
        ImportPreview result = ImportPreview.of(check(c, rows), fileErrors);
        if (!result.canSave()) {
            return result;
        }

        Map<String, Batch> batches = batchesByName(classId);
        List<Student> students = rows.stream()
                .map(r -> new Student(r.rollNo(), r.name(), c, batches.get(r.batch().toUpperCase())))
                .toList();
        studentRepository.saveAll(students);
        return result;
    }

    // ---------------------------------------------------------------- checking

    /** Applies all the rules to every row and attaches error messages. */
    List<ImportRow> check(CollegeClass c, List<RawRow> rows) {
        Map<String, Batch> batches = batchesByName(c.getId());
        String batchList = batches.values().stream().map(Batch::getName)
                .sorted(RollNumbers.NATURAL).collect(Collectors.joining(", "));

        Map<String, String> existing = new HashMap<>();   // ROLL NO (upper case) -> student name
        for (Student s : studentRepository.findByCollegeClassId(c.getId())) {
            existing.put(s.getRollNo().toUpperCase(), s.getName());
        }

        Map<String, List<Integer>> rowsByRoll = new HashMap<>();
        for (RawRow r : rows) {
            if (!r.rollNo().isEmpty()) {
                rowsByRoll.computeIfAbsent(r.rollNo().toUpperCase(), k -> new ArrayList<>()).add(r.rowNo());
            }
        }

        List<ImportRow> result = new ArrayList<>();
        for (RawRow r : rows) {
            List<String> errors = new ArrayList<>();
            if (r.rollNo().isEmpty()) {
                errors.add("Roll No is missing.");
            } else if (r.rollNo().length() > 20) {
                errors.add("Roll No is longer than 20 characters.");
            } else {
                List<Integer> same = rowsByRoll.get(r.rollNo().toUpperCase());
                if (same.size() > 1) {
                    errors.add("Roll No " + r.rollNo() + " appears more than once in the file (rows "
                            + same.stream().map(String::valueOf).collect(Collectors.joining(", ")) + ").");
                }
                String existingName = existing.get(r.rollNo().toUpperCase());
                if (existingName != null) {
                    errors.add("Roll No " + r.rollNo() + " is already in " + c.getName() + " (" + existingName + ").");
                }
            }
            if (r.name().isEmpty()) {
                errors.add("Name is missing.");
            } else if (r.name().length() > 100) {
                errors.add("Name is longer than 100 characters.");
            }
            if (r.batch().isEmpty()) {
                errors.add("Batch is missing.");
            } else if (!batches.containsKey(r.batch().toUpperCase())) {
                errors.add("Unknown batch " + r.batch() + ". " + c.getName() + " has: "
                        + (batchList.isEmpty() ? "no batches yet" : batchList) + ".");
            }
            result.add(new ImportRow(r.rowNo(), r.rollNo(), r.name(), r.batch(), errors));
        }
        return result;
    }

    private Map<String, Batch> batchesByName(Long classId) {
        Map<String, Batch> map = new HashMap<>();
        for (Batch b : batchRepository.findByCollegeClassIdOrderByName(classId)) {
            map.put(b.getName().toUpperCase(), b);
        }
        return map;
    }

    // ---------------------------------------------------------------- reading files

    List<RawRow> readFile(MultipartFile file, List<String> fileErrors) throws IOException {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
        List<List<String>> table;
        if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            try (InputStream in = file.getInputStream()) {
                table = readExcel(in);
            }
        } else if (filename.endsWith(".csv")) {
            table = readCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
        } else {
            fileErrors.add("Upload an Excel (.xlsx, .xls) or CSV (.csv) file.");
            return List.of();
        }
        return toRows(table, fileErrors);
    }

    /** Finds the Roll No / Name / Batch columns in the first non-empty row and reads the rows below. */
    List<RawRow> toRows(List<List<String>> table, List<String> fileErrors) {
        int headerIndex = 0;
        while (headerIndex < table.size() && isBlank(table.get(headerIndex))) {
            headerIndex++;
        }
        if (headerIndex == table.size()) {
            fileErrors.add("The file is empty.");
            return List.of();
        }

        List<String> header = table.get(headerIndex);
        int rollCol = -1, nameCol = -1, batchCol = -1;
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i).toLowerCase().replaceAll("[^a-z]", "");
            if (rollCol < 0 && (h.equals("rollno") || h.equals("roll") || h.equals("rollnumber"))) rollCol = i;
            else if (nameCol < 0 && (h.equals("name") || h.equals("studentname"))) nameCol = i;
            else if (batchCol < 0 && h.equals("batch")) batchCol = i;
        }
        if (rollCol < 0 || nameCol < 0 || batchCol < 0) {
            fileErrors.add("The first row must have the column headings Roll No, Name and Batch.");
            return List.of();
        }

        List<RawRow> rows = new ArrayList<>();
        for (int i = headerIndex + 1; i < table.size(); i++) {
            List<String> cells = table.get(i);
            if (isBlank(cells)) {
                continue;
            }
            rows.add(new RawRow(i + 1, clean(cell(cells, rollCol)), clean(cell(cells, nameCol)),
                    clean(cell(cells, batchCol))));
        }
        return rows;
    }

    /** First sheet as a table of strings; numbers are shown as typed (1, not 1.0). */
    private List<List<String>> readExcel(InputStream in) throws IOException {
        List<List<String>> table = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    for (int col = 0; col < row.getLastCellNum(); col++) {
                        Cell cell = row.getCell(col);
                        cells.add(cell == null ? "" : formatter.formatCellValue(cell, evaluator));
                    }
                }
                table.add(cells);
            }
        }
        return table;
    }

    /** Simple CSV reader: comma (or semicolon) separated, double quotes around values allowed. */
    List<List<String>> readCsv(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);   // byte order mark added by Excel
        }
        String firstLine = text.lines().findFirst().orElse("");
        char sep = firstLine.contains(";") && !firstLine.contains(",") ? ';' : ',';

        List<List<String>> table = new ArrayList<>();
        for (String line : text.split("\\r?\\n", -1)) {
            List<String> cells = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean inQuotes = false;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (inQuotes) {
                    if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else if (ch == '"') {
                        inQuotes = false;
                    } else {
                        current.append(ch);
                    }
                } else if (ch == '"') {
                    inQuotes = true;
                } else if (ch == sep) {
                    cells.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(ch);
                }
            }
            cells.add(current.toString());
            table.add(cells);
        }
        return table;
    }

    private static String cell(List<String> cells, int index) {
        return index < cells.size() ? cells.get(index) : "";
    }

    private static boolean isBlank(List<String> cells) {
        return cells.stream().allMatch(s -> s == null || s.isBlank());
    }

    // ---------------------------------------------------------------- template

    /** A ready-to-fill Excel file with the right headings and two example rows. */
    public byte[] template() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Students");
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(bold);

            String[][] data = {{"Roll No", "Name", "Batch"}, {"1", "Example Student", "B1"}, {"2", "Another Student", "B2"}};
            for (int r = 0; r < data.length; r++) {
                Row row = sheet.createRow(r);
                for (int col = 0; col < 3; col++) {
                    Cell cell = row.createCell(col);
                    cell.setCellValue(data[r][col]);
                    if (r == 0) cell.setCellStyle(headerStyle);
                }
            }
            sheet.setColumnWidth(0, 12 * 256);
            sheet.setColumnWidth(1, 32 * 256);
            sheet.setColumnWidth(2, 10 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BadRequestException("Could not create the template.");
        }
    }
}
