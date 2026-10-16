package com.quizverse.security;

import com.quizverse.model.User;
import com.quizverse.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Looks users up by username or email; the principal name is always the username.
 * Usernames listed in quizverse.admin-usernames also get ROLE_ADMIN (question reports).
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;
    private final Set<String> admins;

    public AppUserDetailsService(UserRepository users, @Value("${quizverse.admin-usernames:}") String adminUsernames) {
        this.users = users;
        this.admins = Arrays.stream(adminUsernames.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String username) {
        return admins.contains(username);
    }

    @Override
    public UserDetails loadUserByUsername(String login) {
        String key = login.trim().toLowerCase(Locale.ROOT);
        User user = (key.contains("@") ? users.findByEmail(key) : users.findByUsername(key))
                .orElseThrow(() -> new UsernameNotFoundException("No such user"));
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(isAdmin(user.getUsername()) ? new String[] {"USER", "ADMIN"} : new String[] {"USER"})
                .build();
    }
}
