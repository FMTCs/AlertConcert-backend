package fcmt.backend.exception.custom;

public class DuplicateUserException extends RuntimeException {

	public DuplicateUserException() {
		super("Duplicate User");
	}

}
