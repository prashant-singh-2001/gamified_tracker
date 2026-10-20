package com.tracker.gateway.user;

/** The caller's own account, as returned by {@code GET /users/me}. Never includes the password hash or email. */
public record UserProfile(Long id, String firstName, String lastName, Role role) {

    public static UserProfile of(User user) {
        return new UserProfile(user.getId(), user.getFirstName(), user.getLastName(), user.getRole());
    }
}
