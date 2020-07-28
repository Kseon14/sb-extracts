package com.am.sbextracts.service;

import java.util.Collection;

import com.am.sbextracts.vo.Person;

public interface Responder {

    void respond(Collection<Person> persons);
}
