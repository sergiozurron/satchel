package com.jejo.satchel.service;

import com.jejo.satchel.model.User;

public interface CurrentUserProvider {
	User getCurrentUser();
}