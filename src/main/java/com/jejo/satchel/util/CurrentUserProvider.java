package com.jejo.satchel.util;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.jejo.satchel.model.User;

@Component
public class CurrentUserProvider {
	
	public User getCurrentUser() {
		return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();		
	}

}
