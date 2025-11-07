package com.learning.progress.common;

public enum DifficultyLevel {
    // Grade levels (L1-L12)
    L1("Level 1 - Elementary Grade 1", "Basic literacy and numeracy for 6-7 year olds"),
    L2("Level 2 - Elementary Grade 2", "Foundational skills for 7-8 year olds"),
    L3("Level 3 - Elementary Grade 3", "Developing literacy for 8-9 year olds"),
    L4("Level 4 - Elementary Grade 4", "Intermediate elementary for 9-10 year olds"),
    L5("Level 5 - Elementary Grade 5", "Advanced elementary for 10-11 year olds"),
    L6("Level 6 - Middle School Grade 6", "Early middle school for 11-12 year olds"),
    L7("Level 7 - Middle School Grade 7", "Middle school for 12-13 year olds"),
    L8("Level 8 - Middle School Grade 8", "Advanced middle school for 13-14 year olds"),
    L9("Level 9 - High School Grade 9", "Freshman year for 14-15 year olds"),
    L10("Level 10 - High School Grade 10", "Sophomore year for 15-16 year olds"),
    L11("Level 11 - High School Grade 11", "Junior year for 16-17 year olds"),
    L12("Level 12 - High School Grade 12", "Senior year for 17-18 year olds"),

    // CEFR levels
    A1("A1 - Beginner", "Can understand and use familiar everyday expressions"),
    A2("A2 - Elementary", "Can communicate in simple routine tasks"),
    B1("B1 - Intermediate", "Can deal with most situations while traveling"),
    B2("B2 - Upper Intermediate", "Can interact with fluency and spontaneity"),
    C1("C1 - Advanced", "Can express ideas fluently and spontaneously"),
    C2("C2 - Proficiency", "Can understand virtually everything with ease"),

    // University level
    UNIVERSITY("University Level", "Academic English for higher education");

    private final String displayName;
    private final String description;

    DifficultyLevel(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
