package com.jejo.satchel.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jejo.satchel.model.RepaymentAccount;

@Repository
public interface RepaymentAccountRepository extends JpaRepository<RepaymentAccount, Long> {

	public Optional<RepaymentAccount> findByUserId(Long id);
}
