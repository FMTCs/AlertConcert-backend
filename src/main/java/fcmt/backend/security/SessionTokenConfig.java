package fcmt.backend.security;

public enum SessionTokenConfig {

	ACCESS(15 * 60 * 1000), // 15분
	REFRESH(14L * 24 * 60 * 60 * 1000); // 14일

	private final long expireMillis;

	SessionTokenConfig(long expireMillis) {
		this.expireMillis = expireMillis;
	}

	public long getExpireMillis() {
		return expireMillis;
	}

}
