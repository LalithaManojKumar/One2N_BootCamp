package basics.student_grading;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class StudentGradingSystem {

    public static void main(String[] args) {
        String filePath = "C:\\Users\\manob\\Downloads\\One2N BootCamp\\src\\main\\java\\basics\\student_grading\\grades.csv";

        StudentGrading studentGrading = new StudentGrading();

        try {
            List<Student> students = studentGrading.parseCsv(filePath);
            List<StudentStat> gradedStudents = studentGrading.calculateGrade(students);

            System.out.println("All students:");
            gradedStudents.forEach(System.out::println);

            studentGrading.findOverallTopper(gradedStudents)
                    .ifPresentOrElse(
                            topper -> System.out.println("\nOverall topper: " + topper),
                            () -> System.out.println("\nNo students found."));

            System.out.println("\nTopper per university:");
            Map<String, StudentStat> topperPerUniversity = studentGrading.findTopperPerUniversity(gradedStudents);
            topperPerUniversity.forEach((university, topper) -> System.out.println(university + ": " + topper));
        } catch (IOException e) {
            System.err.println("Failed to process student data: " + e.getMessage());
        }
    }
}
