package com.speshkitty.playerpronouns;

import lombok.Getter;

public enum Pronoun {
    HE_HIM (0, "He/Him"),
    SHE_HER (10, "She/Her"),
    HE_THEY (20, "He/They"),
    SHE_THEY (30, "She/They"),
    THEY_THEM(40, "They/Them"),
    IT_ITS(50, "It/Its"),
    ANY(100, "Any"),
    ASK(200, "Ask");
    //OTHER(500, "Other");

    @Getter
    private final String text;
    @Getter
    private final int internalValue;
    Pronoun(int internalValue, String text){
        this.text = text;
        this.internalValue = internalValue;
    }

    public String toString() {
        return getText();
    }
}
