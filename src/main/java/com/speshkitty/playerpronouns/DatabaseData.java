package com.speshkitty.playerpronouns;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class DatabaseData {
    @Getter(AccessLevel.PACKAGE)
    private long retrievedAt;

    @Getter(AccessLevel.PACKAGE)
    private String pronoun;
}
