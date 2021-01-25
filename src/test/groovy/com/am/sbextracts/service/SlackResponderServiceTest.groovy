package com.am.sbextracts.service


import spock.lang.Specification
import spock.lang.Unroll

class SlackResponderServiceTest extends Specification {

    @Unroll
    def "test addIfNotNull"() {
        given:
        def fields = []
        expect:
        SlackResponderService.addIfNotNull(fields, "test", value)
        fields.size() == result
        where:
        value  | result
        "test" | 1
        "0"    | 0
        "0.0"  | 0
        "0.00" | 0
        "1"    | 1
        "1.0"  | 1
        ""     | 0
        null   | 0

    }

}
