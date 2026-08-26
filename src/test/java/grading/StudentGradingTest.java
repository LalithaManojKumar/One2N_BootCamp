package grading;

import basics.student_grading.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentGradingTest {

    static StudentGrading studentGrading = new StudentGrading();

    @Test
    void createStudentFromRow_buildsStudent_whenRowIsValid() throws InvalidCsvRowException {
        String[] row = {"John", "Doe", "MIT", "80", "75", "90", "85"};

        Student student = studentGrading.createStudentFromRow(row);

        assertEquals("John", student.firstName());
        assertEquals("Doe", student.lastName());
        assertEquals("MIT", student.university());
        assertEquals(80, student.test1Score());
        assertEquals(85, student.test4Score());
    }

    @Test
    void createStudentFromRow_throws_whenColumnCountIsWrong() {
        String[] row = {"John", "Doe", "MIT", "80", "75", "90"}; // missing one column

        assertThrows(InvalidCsvRowException.class, () -> studentGrading.createStudentFromRow(row));
    }

    @Test
    void createStudentFromRow_throws_whenScoreIsNotANumber() {
        String[] row = {"John", "Doe", "MIT", "eighty", "75", "90", "85"};

        assertThrows(InvalidCsvRowException.class, () -> studentGrading.createStudentFromRow(row));
    }

    // ---------- calculateGrade ----------

    @Test
    void calculateGrade_returnsEmptyList_whenNoStudents() {
        List<StudentStat> stats = studentGrading.calculateGrade(List.of());

        assertTrue(stats.isEmpty());
    }

    @Test
    void calculateGrade_computesAverageAndGrade_forOneStudent() {
        Student student = new Student("John", "Doe", "MIT", 80, 75, 90, 85);

        List<StudentStat> stats = studentGrading.calculateGrade(List.of(student));

        assertEquals(1, stats.size());
        assertEquals(82.5f, stats.get(0).finalScore());
        assertEquals(Grade.A, stats.get(0).grade());
    }

    @Test
    void calculateGrade_computesEachGradeBand_forManyStudents() {
        List<Student> students = List.of(
                new Student("A", "A", "MIT", 70, 70, 70, 70),  // avg 70 -> A
                new Student("B", "B", "MIT", 50, 50, 50, 50),  // avg 50 -> B
                new Student("C", "C", "MIT", 35, 35, 35, 35),  // avg 35 -> C
                new Student("D", "D", "MIT", 10, 10, 10, 10)); // avg 10 -> F

        List<StudentStat> stats = studentGrading.calculateGrade(students);

        assertEquals(Grade.A, stats.get(0).grade());
        assertEquals(Grade.B, stats.get(1).grade());
        assertEquals(Grade.C, stats.get(2).grade());
        assertEquals(Grade.F, stats.get(3).grade());
    }

    // ---------- findOverallTopper ----------

    @Test
    void findOverallTopper_returnsEmpty_whenNoStudents() {
        Optional<StudentStat> topper = studentGrading.findOverallTopper(List.of());

        assertTrue(topper.isEmpty());
    }

    @Test
    void findOverallTopper_returnsThatStudent_whenOnlyOneStudent() {
        StudentStat only = statOf("Solo", "Student", "MIT", 88);

        Optional<StudentStat> topper = studentGrading.findOverallTopper(List.of(only));

        assertEquals(only, topper.orElseThrow());
    }

    @Test
    void findOverallTopper_returnsHighestScorer_whenManyStudents() {
        StudentStat low = statOf("Low", "Scorer", "MIT", 40);
        StudentStat high = statOf("High", "Scorer", "MIT", 95);
        StudentStat mid = statOf("Mid", "Scorer", "MIT", 70);

        Optional<StudentStat> topper = studentGrading.findOverallTopper(List.of(low, high, mid));

        assertEquals(high, topper.orElseThrow());
    }

    // ---------- findTopperPerUniversity ----------

    @Test
    void findTopperPerUniversity_returnsEmptyMap_whenNoStudents() {
        Map<String, StudentStat> toppers = studentGrading.findTopperPerUniversity(List.of());

        assertTrue(toppers.isEmpty());
    }

    @Test
    void findTopperPerUniversity_returnsSingleEntry_whenOneStudent() {
        StudentStat only = statOf("Solo", "Student", "MIT", 88);

        Map<String, StudentStat> toppers = studentGrading.findTopperPerUniversity(List.of(only));

        assertEquals(1, toppers.size());
        assertEquals(only, toppers.get("MIT"));
    }

    @Test
    void findTopperPerUniversity_returnsOneTopperPerUniversity_whenManyStudents() {
        StudentStat mitLow = statOf("Mit", "Low", "MIT", 40);
        StudentStat mitHigh = statOf("Mit", "High", "MIT", 95);
        StudentStat stanfordOnly = statOf("Stanford", "Only", "Stanford", 60);

        Map<String, StudentStat> toppers =
                studentGrading.findTopperPerUniversity(List.of(mitLow, mitHigh, stanfordOnly));

        assertEquals(2, toppers.size());
        assertEquals(mitHigh, toppers.get("MIT"));
        assertEquals(stanfordOnly, toppers.get("Stanford"));
    }

    // ---------- parseCsv (file I/O, using JUnit's @TempDir) ----------

    @Test
    void parseCsv_returnsEmptyList_whenFileHasOnlyAHeader(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(tempDir, "first_name,last_name,university,test1,test2,test3,test4\n");

        List<Student> students = studentGrading.parseCsv(csv.toString());

        assertTrue(students.isEmpty());
    }

    @Test
    void parseCsv_returnsOneStudent_whenFileHasOneDataRow(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(tempDir,
                "first_name,last_name,university,test1,test2,test3,test4\n"
                        + "John,Doe,MIT,80,75,90,85\n");

        List<Student> students = studentGrading.parseCsv(csv.toString());

        assertEquals(1, students.size());
        assertEquals("John", students.get(0).firstName());
    }

    @Test
    void parseCsv_returnsAllStudents_whenFileHasManyDataRows(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(tempDir,
                "first_name,last_name,university,test1,test2,test3,test4\n"
                        + "John,Doe,MIT,80,75,90,85\n"
                        + "Jane,Smith,Stanford,60,55,58,62\n"
                        + "Alice,Brown,Harvard,40,38,35,36\n");

        List<Student> students = studentGrading.parseCsv(csv.toString());

        assertEquals(3, students.size());
    }

    @Test
    void parseCsv_throwsIOException_whenFileDoesNotExist() {
        assertThrows(IOException.class, () -> studentGrading.parseCsv("/no/such/file.csv"));
    }

    @Test
    void parseCsv_throwsIOException_whenARowIsMalformed(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(tempDir,
                "first_name,last_name,university,test1,test2,test3,test4\n"
                        + "John,Doe,MIT,not-a-number,75,90,85\n");

        assertThrows(IOException.class, () -> studentGrading.parseCsv(csv.toString()));
    }

    private static StudentStat statOf(String firstName, String lastName, String university, int score) {
        Student student = new Student(firstName, lastName, university, score, score, score, score);
        return new StudentStat(student, score, studentGrading.gradeFor(score));
    }

    private static Path writeCsv(Path tempDir, String content) throws IOException {
        Path file = tempDir.resolve("students.csv");
        Files.writeString(file, content);
        return file;
    }
}
