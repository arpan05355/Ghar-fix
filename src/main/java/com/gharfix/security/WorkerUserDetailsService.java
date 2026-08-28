package com.gharfix.security;

import com.gharfix.entity.Worker;
import com.gharfix.repository.WorkerRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service("workerUserDetailsService")
public class WorkerUserDetailsService implements UserDetailsService {

    private final WorkerRepository workerRepository;

    public WorkerUserDetailsService(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Worker worker = workerRepository.findByEmail(email)
                .filter(w -> w.getPasswordHash() != null && w.getEmail() != null)
                .orElseThrow(() -> new UsernameNotFoundException("Worker not found with email: " + email));
        return new WorkerUserDetails(worker);
    }
}
