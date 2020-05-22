package gov.nist.healthcare.auth.service.impl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import gov.nist.healthcare.auth.domain.PasswordChange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import gov.nist.healthcare.auth.domain.Account;
import gov.nist.healthcare.auth.domain.Privilege;
import gov.nist.healthcare.auth.repository.AccountRepository;
import gov.nist.healthcare.auth.repository.PrivilegeRepository;
import gov.nist.healthcare.auth.service.AccountService;

@Service
public class AccountServiceImpl implements AccountService {

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private PrivilegeRepository privilegeRepository;
	
	@Autowired
	private PasswordEncoder encoder;

	@Override
	public Account getAccountByUsername(String username) {
		return accountRepository.findByUsername(username);
	}

	@Override
	public Account createAdmin(Account account) {
		if (!account.getUsername().isEmpty() && !account.getPassword().isEmpty()) {
			account.setPassword(encoder.encode(account.getPassword()));
			Set<Privilege> roles = new HashSet<Privilege>(privilegeRepository.findAll());
			account.setPrivileges(roles);
			accountRepository.save(account);
			return account;
		}
		return null;
	}

	@Override
	public Account createTester(Account account) {
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		if (!account.getUsername().isEmpty()
				&& !account.getPassword().isEmpty()) {
			account.setPassword(encoder.encode(account.getPassword()));
			Set<Privilege> roles = new HashSet<Privilege>();
			roles.add(privilegeRepository.findByRole("TESTER"));
			account.setPrivileges(roles);
			accountRepository.save(account);
			return account;
		}
		return null;
	}

	@Override
	public Account changePassword(PasswordChange change) throws BadCredentialsException, UsernameNotFoundException {
		Account user = this.getAccountByUsername(change.user);
		if(user != null) {
			if(this.encoder.matches(change.password, user.getPassword())) {
				user.setPassword(this.encoder.encode(change.newPassword));
				return user;
			} else {
				throw new BadCredentialsException("Incorrect password");
			}
		} else {
			throw new UsernameNotFoundException("Username not found "+change.user);
		}
	}

	@Override
	public void deleteAll() {

		accountRepository.deleteAll();
	}

	@Override
	public boolean isAdmin(String user) {
		Account a = this.accountRepository.findByUsername(user);
		if(a == null) {
			return false;
		} else {
			return a.getPrivileges().stream().anyMatch((p) -> p.getRole().equals("ADMIN"));
		}
	}

	@Override
	public List<Account> getAllUsersExceptAdmins() {
		return this.accountRepository.findAll().stream().filter((a) -> {
			return a.getPrivileges().stream().noneMatch((p) -> p.getRole().equals("ADMIN"));
		}).collect(Collectors.toList());
	}

	@Override
	public Account getCurrentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Account)) {
			return null;
		}
		return (Account) authentication.getPrincipal();
	}

	@Override
	public Account createUser(Account account, Privilege p) {
		if(p.getRole().equals("ADMIN")){
			this.createAdmin(account);
		}
		else {
			this.createTester(account);
		}
		return null;
	}

	@Override
	public Account save(Account account) {
		return this.accountRepository.save(account);
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		Account a = this.getAccountByUsername(username);
		if(a != null){
			return a.userDetails();
		}
		else {
			throw new UsernameNotFoundException(username);
		}
	}

	@Override
	public Privilege getPrivilegeByRole(String role) {
		return this.privilegeRepository.findByRole(role);
	}
	
	@Override
	public Privilege createPrivilegeByRole(String role) {
		Privilege p = new Privilege(role);
		if(this.privilegeRepository.findByRole(role) == null){
			this.privilegeRepository.save(p);
			return p;
		}
		return this.privilegeRepository.findByRole(role);
	}

}