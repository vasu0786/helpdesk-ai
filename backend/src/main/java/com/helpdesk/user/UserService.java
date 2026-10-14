package com.helpdesk.user;

import com.helpdesk.common.BadRequestException;
import com.helpdesk.common.ConflictException;
import com.helpdesk.common.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User create(String email, String rawPassword, String fullName, Role role) {
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("A user with email " + email + " already exists");
        }
        return repository.save(new User(email, passwordEncoder.encode(rawPassword), fullName.strip(), role));
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<User> list(Role role) {
        return role == null ? repository.findAllByOrderByCreatedAtDesc() : repository.findByRoleOrderByFullName(role);
    }

    @Transactional
    public User update(Long id, UpdateUserRequest request, Long actingUserId) {
        User user = get(id);
        if (id.equals(actingUserId) && (request.role() != null || Boolean.FALSE.equals(request.active()))) {
            throw new BadRequestException("You cannot change your own role or deactivate yourself");
        }
        if (request.fullName() != null) user.setFullName(request.fullName().strip());
        if (request.role() != null) user.setRole(request.role());
        if (request.active() != null) user.setActive(request.active());
        return user;
    }
}
