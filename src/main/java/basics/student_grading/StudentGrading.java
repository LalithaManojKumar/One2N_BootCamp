package basics.student_grading;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class StudentGrading {

    private static final int EXPECTED_COLUMN_COUNT = 7;

    public StudentGrading() {
    }

    public List<Student> parseCsv(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            return reader.lines()
                    .skip(1) // header line, discard
                    .filter(line -> !line.isBlank())
                    .map(line -> parseRowOrThrow(line, filePath))
                    .toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private Student parseRowOrThrow(String line, String filePath) {
        try {
            return createStudentFromRow(line.split(",", -1));
        } catch (InvalidCsvRowException e) {
            throw new UncheckedIOException(
                    new IOException("failed to parse " + filePath + ": " + e.getMessage(), e));
        }
    }

    public static Student createStudentFromRow(String[] row) throws InvalidCsvRowException {
        if (row.length != EXPECTED_COLUMN_COUNT) {
            throw new InvalidCsvRowException(
                    "invalid csv row: expected " + EXPECTED_COLUMN_COUNT + " columns, got " + row.length);
        }

        try {
            return new Student(
                    row[0].trim(),
                    row[1].trim(),
                    row[2].trim(),
                    Integer.parseInt(row[3].trim()),
                    Integer.parseInt(row[4].trim()),
                    Integer.parseInt(row[5].trim()),
                    Integer.parseInt(row[6].trim()));
        } catch (NumberFormatException e) {
            throw new InvalidCsvRowException("invalid csv row: " + e.getMessage(), e);
        }
    }

    public List<StudentStat> calculateGrade(List<Student> students) {
        return students.stream()
                .map(StudentGrading::toStudentStat)
                .toList();
    }

    public static StudentStat toStudentStat(Student student) {
        float finalScore = (student.test1Score() + student.test2Score()
                + student.test3Score() + student.test4Score()) / 4f;
        return new StudentStat(student, finalScore, gradeFor(finalScore));
    }

    public static Grade gradeFor(float score) {
        if (score >= 70) return Grade.A;
        if (score >= 50) return Grade.B;
        if (score >= 35) return Grade.C;
        return Grade.F;
    }

    public Optional<StudentStat> findOverallTopper(List<StudentStat> gradedStudents) {
        return gradedStudents.stream()
                .max(Comparator.comparing(StudentStat::finalScore));
    }

    public Map<String, StudentStat> findTopperPerUniversity(List<StudentStat> gradedStudents) {
        return groupByUniversity(gradedStudents).entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), findOverallTopper(entry.getValue())))
                .filter(entry -> entry.getValue().isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));
    }

    /** Groups graded students by their university. */
    public Map<String, List<StudentStat>> groupByUniversity(List<StudentStat> gradedStudents) {
        return gradedStudents.stream()
                .collect(Collectors.groupingBy(stat -> stat.student().university()));
    }
}
