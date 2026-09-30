package dio.budgeting.application;

public class InvalidInputException extends IllegalArgumentException {
    public InvalidInputException(String message) {
        super(message);
    }
}
