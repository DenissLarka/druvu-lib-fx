package com.druvu.lib.fx.auth;

/**
 * Verifies credentials and yields an application-defined principal. Implementations are plain
 * functions - a lambda over an HTTP call, a JDBC lookup, an LDAP bind, whatever - so the toolkit
 * hard-codes no auth backend.
 *
 * <p>Threading: {@link LoginPane} invokes this on a background virtual thread (never the FX
 * thread), so blocking I/O here is fine. Return the principal on success; <em>throw</em> to reject.
 * The thrown exception's message is what {@link LoginPane} shows the user, so make it human-facing
 * (e.g. "Invalid username or password").
 *
 * @param <P> the principal type handed back on success (a user record, token, session id, ...)
 */
@FunctionalInterface
public interface Authenticator<P> {

	/**
	 * @param username the entered username (never null)
	 * @param password the entered password (never null); callers may clear it after this returns
	 * @return the authenticated principal (never null)
	 * @throws Exception to reject the credentials or signal a backend failure
	 */
	P authenticate(String username, char[] password) throws Exception;
}
