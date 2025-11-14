package com.learning.progress.common;

public enum DifficultyLevel {
    // Grade levels (L1-L12)
    L1("Level 1 - Elementary Grade 1",
            "Very limited vocabulary (basic objects, colors, numbers). Can recognize simple sight words and read very short sentences with support. Handles extremely simple tasks such as matching pictures to words or identifying key objects."),

    L2("Level 2 - Elementary Grade 2",
            "Basic foundational vocabulary (family, school items, simple actions). Can read short sentences and small paragraphs of 2–3 lines. Completes simple tasks like choosing correct answers from pictures or identifying main ideas."),

    L3("Level 3 - Elementary Grade 3",
            "Growing vocabulary on everyday topics. Can read short paragraphs (~60–80 words) and understand explicit information. Solves simple problem-based tasks using direct information from the text."),

    L4("Level 4 - Elementary Grade 4",
            "Wider vocabulary including hobbies, daily routines, and places. Reads short passages (~100–150 words) with some inference. Can complete tasks requiring locating details, sequencing events, and matching ideas."),

    L5("Level 5 - Elementary Grade 5",
            "Strong basic vocabulary and early academic terms. Reads passages (~150–200 words) with clear structure. Can solve tasks that require comparing information or identifying causes in simple contexts."),

    L6("Level 6 - Middle School Grade 6",
            "Vocabulary covers school life, environment, and simple science topics. Reads 200–250 word texts and understands both general meaning and key details. Can interpret information and solve simple linguistic problems."),

    L7("Level 7 - Middle School Grade 7",
            "Expanding vocabulary across academic subjects. Reads passages (~250–300 words) and recognizes relationships (reasons, results). Can complete tasks requiring inference and synthesizing simple ideas."),

    L8("Level 8 - Middle School Grade 8",
            "Rich vocabulary including abstract topics (technology, health, community). Reads 300–350 word texts and interprets viewpoints. Handles tasks requiring comparison, interpretation, and multi-step reasoning."),

    L9("Level 9 - High School Grade 9",
            "Strong academic vocabulary. Reads 350–450 word passages and analyzes information. Can solve problem-based tasks involving inference, summarizing, and evaluating simple arguments."),

    L10("Level 10 - High School Grade 10",
            "Wider academic and semi-formal vocabulary. Reads 450–550 word texts with complex structures. Can evaluate information, connect ideas, and solve tasks requiring multi-step reasoning."),

    L11("Level 11 - High School Grade 11",
            "Advanced vocabulary for academic subjects. Reads texts of 600+ words with layered arguments. Can analyze viewpoints, identify assumptions, and solve higher-order comprehension problems."),

    L12("Level 12 - High School Grade 12",
            "Pre-university vocabulary and strong reading skills. Reads 700+ word academic passages. Capable of solving complex problem-solving tasks such as evaluating arguments and synthesizing multiple ideas."),

    // CEFR levels
    A1("A1 - Beginner",
            "Very limited vocabulary and basic phrases. Reads extremely short, simple texts. Can complete tasks requiring recognition of familiar words or simple factual matching."),

    A2("A2 - Elementary",
            "Basic everyday vocabulary. Reads simple, short paragraphs and understands routine information. Solves tasks that involve locating direct details and completing simple statements."),

    B1("B1 - Intermediate",
            "Moderate vocabulary across familiar topics. Reads texts with clear structure and main ideas. Solves tasks involving summarizing, identifying key points, and interpreting straightforward information."),

    B2("B2 - Upper Intermediate",
            "Wide vocabulary for both general and semi-academic topics. Reads complex texts and understands nuance. Solves analytical tasks involving inference, comparison, and understanding implied meaning."),

    C1("C1 - Advanced",
            "Very advanced vocabulary suitable for academic use. Reads long, complex texts and identifies subtle arguments. Capable of solving tasks requiring evaluation, critical reasoning, and synthesis."),

    C2("C2 - Proficiency",
            "Near-native vocabulary and comprehension. Reads highly complex and abstract texts with ease. Handles sophisticated problem-solving tasks that require deep analysis and interpretation."),

    // University level
    UNIVERSITY("University Level",
            "Academic and research-level vocabulary. Reads scholarly articles and technical texts. Capable of solving advanced academic problems such as analyzing arguments, interpreting data, and producing structured conclusions.");

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
