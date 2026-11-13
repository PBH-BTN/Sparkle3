package com.ghostchu.btn.sparkle.security;

import java.io.Serial;
import java.io.Serializable;

public record PendingGithubRegistration(
        long githubUid,
        String githubLogin,
        String avatarUrl,
        String nickname,
        String email
) implements Serializable {
    public static final String SESSION_ATTRIBUTE =
            PendingGithubRegistration.class.getName() + ".PENDING";

    @Serial
    private static final long serialVersionUID = 1L;
}
