package basics.student_grading;

public class InvalidCsvRowException extends Exception {

    public InvalidCsvRowException(String message) {
        super(message);
    }

    public InvalidCsvRowException(String message, Throwable cause) {
        super(message, cause);
    }
}
