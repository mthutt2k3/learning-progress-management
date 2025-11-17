package com.learning.progress.common;

public enum LessonFocus {
    // ============================================
    // GRAMMAR TENSES - Detailed
    // ============================================
    PRESENT_SIMPLE("Present Simple", "I go, He goes, Do you...?, She doesn't..."),
    PRESENT_CONTINUOUS("Present Continuous", "I am going, He is studying, Are you...?"),
    PRESENT_PERFECT("Present Perfect", "I have been, He has done, Have you...?"),
    PRESENT_PERFECT_CONTINUOUS("Present Perfect Continuous", "I have been working, She has been studying"),

    PAST_SIMPLE("Past Simple", "I went, He studied, Did you...?, She didn't..."),
    PAST_CONTINUOUS("Past Continuous", "I was going, He was studying, Were you...?"),
    PAST_PERFECT("Past Perfect", "I had been, He had done, Had you...?"),
    PAST_PERFECT_CONTINUOUS("Past Perfect Continuous", "I had been working, She had been studying"),

    FUTURE_SIMPLE("Future Simple (will)", "I will go, He will study, Will you...?"),
    FUTURE_BE_GOING_TO("Future (be going to)", "I am going to go, He is going to study"),
    FUTURE_CONTINUOUS("Future Continuous", "I will be working, He will be studying"),
    FUTURE_PERFECT("Future Perfect", "I will have finished, She will have done"),

    MIXED_TENSES("Mixed Tenses", "Comparing and choosing between different tenses"),

    // ============================================
    // GRAMMAR STRUCTURES
    // ============================================
    CONDITIONALS_ZERO_FIRST("Conditionals: Zero & First", "If + present simple, If it rains..."),
    CONDITIONALS_SECOND("Conditionals: Second", "If I had/were..., I would..."),
    CONDITIONALS_THIRD("Conditionals: Third", "If I had done..., I would have..."),
    MIXED_CONDITIONALS("Mixed Conditionals", "Combining different conditional types"),

    PASSIVE_VOICE_PRESENT("Passive Voice: Present", "is done, are made, is being done"),
    PASSIVE_VOICE_PAST("Passive Voice: Past", "was done, were made, was being done"),
    PASSIVE_VOICE_PERFECT("Passive Voice: Perfect", "has been done, had been done"),
    PASSIVE_VOICE_MODAL("Passive Voice: Modal", "can be done, should be done, must be done"),

    REPORTED_SPEECH_STATEMENTS("Reported Speech: Statements", "He said that..., She told me..."),
    REPORTED_SPEECH_QUESTIONS("Reported Speech: Questions", "He asked if..., She wanted to know..."),
    REPORTED_SPEECH_COMMANDS("Reported Speech: Commands", "He told me to..., She asked me not to..."),

    RELATIVE_CLAUSES_DEFINING("Relative Clauses: Defining", "The man who..., The book which..."),
    RELATIVE_CLAUSES_NON_DEFINING("Relative Clauses: Non-defining", "My brother, who..., London, which..."),

    QUESTIONS_FORMATION("Question Formation", "Do/Does/Did questions, Wh- questions"),
    QUESTIONS_SUBJECT_OBJECT("Subject vs Object Questions", "Who saw...? vs Who did you see?"),
    QUESTION_TAGS("Question Tags", "isn't it?, don't they?, haven't you?"),

    // ============================================
    // MODAL VERBS
    // ============================================
    MODALS_ABILITY("Modals: Ability", "can, could, be able to"),
    MODALS_PERMISSION("Modals: Permission", "can, may, could"),
    MODALS_OBLIGATION("Modals: Obligation", "must, have to, should, ought to"),
    MODALS_PROHIBITION("Modals: Prohibition", "mustn't, can't, not allowed to"),
    MODALS_ADVICE("Modals: Advice", "should, ought to, had better"),
    MODALS_DEDUCTION("Modals: Deduction", "must be, can't be, might be, could be"),
    MODALS_PAST("Modal Perfects", "must have been, should have done, could have gone"),

    // ============================================
    // WORD FORMS & PATTERNS
    // ============================================
    COMPARATIVES_SUPERLATIVES("Comparatives & Superlatives", "bigger than, the biggest, more/most"),
    ADJECTIVES_ORDER("Adjective Order", "a beautiful big red car"),
    ADJECTIVES_WITH_PREPOSITIONS("Adjectives + Prepositions", "good at, interested in, afraid of"),

    VERB_PATTERNS_GERUND("Verb Patterns: Gerund", "enjoy doing, finish working, avoid making"),
    VERB_PATTERNS_INFINITIVE("Verb Patterns: Infinitive", "want to go, decide to study, hope to see"),
    VERB_PATTERNS_BOTH("Verb Patterns: Both", "like doing/to do, start working/to work"),

    PHRASAL_VERBS("Phrasal Verbs", "get up, turn on, look after, give up"),

    PREPOSITIONS_TIME("Prepositions: Time", "at, on, in, for, since, during"),
    PREPOSITIONS_PLACE("Prepositions: Place", "at, in, on, next to, between, behind"),
    PREPOSITIONS_MOVEMENT("Prepositions: Movement", "to, into, out of, through, across"),

    // ============================================
    // ARTICLES & DETERMINERS
    // ============================================
    ARTICLES_A_AN_THE("Articles: a/an/the", "When to use a, an, the, or no article"),
    QUANTIFIERS("Quantifiers", "some, any, much, many, a lot of, few, little"),
    COUNTABLE_UNCOUNTABLE("Countable vs Uncountable", "How to use countable and uncountable nouns"),

    // ============================================
    // VOCABULARY
    // ============================================
    VOCABULARY_COLLOCATIONS("Collocations", "make a decision, take an exam, do homework"),
    VOCABULARY_SYNONYMS_ANTONYMS("Synonyms & Antonyms", "big/large, happy/sad, start/begin"),
    VOCABULARY_WORD_FORMATION("Word Formation", "happy → happiness, create → creative → creation"),
    VOCABULARY_PREFIXES_SUFFIXES("Prefixes & Suffixes", "un-, re-, dis-, -ful, -less, -tion"),
    VOCABULARY_IDIOMS("Idioms & Expressions", "piece of cake, break the ice, cost an arm and a leg"),
    VOCABULARY_CONFUSING_WORDS("Confusing Words", "affect/effect, its/it's, then/than"),

    // By topic
    VOCABULARY_FAMILY("Vocabulary: Family & Relationships", "relatives, siblings, extended family"),
    VOCABULARY_EDUCATION("Vocabulary: Education & Learning", "subjects, exams, degrees, skills"),
    VOCABULARY_WORK("Vocabulary: Work & Jobs", "career, salary, promotion, colleague"),
    VOCABULARY_TRAVEL("Vocabulary: Travel & Transport", "journey, accommodation, luggage, destination"),
    VOCABULARY_HEALTH("Vocabulary: Health & Medicine", "symptoms, treatment, disease, injury"),
    VOCABULARY_ENVIRONMENT("Vocabulary: Environment", "pollution, climate change, renewable energy"),
    VOCABULARY_TECHNOLOGY("Vocabulary: Technology", "devices, software, internet, digital"),
    VOCABULARY_FOOD("Vocabulary: Food & Cooking", "ingredients, recipes, nutrition, taste"),
    VOCABULARY_ENTERTAINMENT("Vocabulary: Entertainment", "movies, music, hobbies, leisure activities"),

    // ============================================
    // READING COMPREHENSION
    // ============================================
    READING_MAIN_IDEA("Reading: Main Idea", "Understanding the main topic and purpose"),
    READING_DETAILS("Reading: Specific Details", "Finding specific information and facts"),
    READING_INFERENCE("Reading: Inference", "Understanding implied meaning"),
    READING_VOCABULARY_CONTEXT("Reading: Vocabulary in Context", "Guessing word meaning from context"),
    READING_TEXT_ORGANIZATION("Reading: Text Organization", "Understanding structure and organization"),
    READING_AUTHOR_OPINION("Reading: Author's Opinion", "Identifying author's attitude and purpose"),

    // ============================================
    // LISTENING COMPREHENSION
    // ============================================
    LISTENING_MAIN_IDEA("Listening: Main Idea", "Understanding main topic and purpose"),
    LISTENING_DETAILS("Listening: Specific Details", "Catching specific information: numbers, names, dates"),
    LISTENING_INFERENCE("Listening: Inference", "Understanding implied meaning"),
    LISTENING_ATTITUDE("Listening: Speaker's Attitude", "Understanding speaker's feelings and opinions"),

    // ============================================
    // EXAM-SPECIFIC
    // ============================================
    ERROR_CORRECTION("Error Correction", "Identifying and correcting grammatical errors"),
    SENTENCE_TRANSFORMATION("Sentence Transformation", "Rewriting sentences with same meaning"),
    WORD_CHOICE("Word Choice", "Choosing the correct word in context"),
    GAP_FILLING("Gap Filling", "Completing sentences with appropriate words"),

    // ============================================
    // CUSTOM
    // ============================================
    CUSTOM("Custom Focus", "User-defined focus");

    private final String displayName;
    private final String description;

    LessonFocus(String displayName, String description) {
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