/**
 * Copyright (c) 2016 Kai Toedter
 * All rights reserved.
 * Licensed under MIT License, see http://toedter.mit-license.org/
 */

package com.toedter.chatty.model;

import java.util.List;

public interface UserRepository {
    List<User> getAll();
    User getUserById(String id);
    User saveUser(User user);
    User updateUser(User user);
    void deleteUserById(String id);
    void deleteAll();
    long getSize();
}
