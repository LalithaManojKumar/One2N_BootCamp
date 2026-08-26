package basics.student_grading;

public record Student(
        String firstName,
        String lastName,
        String university,
        int test1Score,
        int test2Score,
        int test3Score,
        int test4Score) {

    @Override
    public String toString() {
        return firstName + " " + lastName + " (" + university + ") - scores: ["
                + test1Score + ", " + test2Score + ", " + test3Score + ", " + test4Score + "]";
    }
}
