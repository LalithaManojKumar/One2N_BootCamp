package basics.student_grading;

public record StudentStat(Student student, float finalScore, Grade grade) {

    @Override
    public String toString() {
        return student + " => finalScore=" + finalScore + ", grade=" + grade;
    }
}
