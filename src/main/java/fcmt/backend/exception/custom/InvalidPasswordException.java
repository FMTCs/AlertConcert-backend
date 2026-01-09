package fcmt.backend.exception.custom;

public class InvalidPasswordException extends RuntimeException {

	public InvalidPasswordException() {
		super("Invalid password");
	}

}
