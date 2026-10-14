package com.helpdesk.user;

import com.helpdesk.config.HelpdeskProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Creates demo accounts on startup (skipped if they already exist). Turn off with SEED_DEMO_DATA=false. */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final HelpdeskProperties props;
    private final UserRepository repository;
    private final UserService userService;

    public DemoDataSeeder(HelpdeskProperties props, UserRepository repository, UserService userService) {
        this.props = props;
        this.repository = repository;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled()) return;
        seed("admin@helpdesk.local", "Admin@123", "Asha Admin", Role.ADMIN);
        seed("agent1@helpdesk.local", "Agent@123", "Arjun Agent", Role.AGENT);
        seed("agent2@helpdesk.local", "Agent@123", "Meera Agent", Role.AGENT);
        seed("customer@helpdesk.local", "Customer@123", "Chris Customer", Role.CUSTOMER);
    }

    private void seed(String email, String password, String name, Role role) {
        if (!repository.existsByEmailIgnoreCase(email)) {
            userService.create(email, password, name, role);
            log.info("Seeded demo user {} ({})", email, role);
        }
    }
}
