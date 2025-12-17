package com.learning.progress.common;

public enum DifficultyLevel {
    // ==================== GRADE LEVELS (L1-L12) ====================

    L1("Level 1 - Elementary Grade 1",
            "VOCABULARY: 300-500 words (basic nouns: apple, cat, dog, colors, numbers 1-20, family members)\n" +
                    "GRAMMAR: Present simple ('I am', 'This is'), basic pronouns (I, you, he, she)\n" +
                    "QUESTION TYPES:\n" +
                    "  • MULTIPLE_CHOICE: Picture-based, very simple (\"What color is this?\")\n" +
                    "  • TRUE_OR_FALSE: Direct facts from text (\"The cat is red. True/False?\")\n" +
                    "  • DRAG_AND_DROP: Match pictures to words\n" +
                    "CANNOT HANDLE: Fill-in-blanks, dropdown, complex reasoning, inference\n" +
                    "COMPLEXITY: Direct recognition only, no interpretation needed"),

    L2("Level 2 - Elementary Grade 2",
            "VOCABULARY: 500-800 words (school items, basic verbs: eat, play, run, simple adjectives)\n" +
                    "GRAMMAR: Present continuous ('I am eating'), can/can't, simple plurals\n" +
                    "QUESTION TYPES:\n" +
                    "  • MULTIPLE_CHOICE: Simple fact recall (\"Where is Tom? A) At school B) At home\")\n" +
                    "  • TRUE_OR_FALSE: Statements directly from text\n" +
                    "  • FILL_IN_THE_BLANK: Single word, high-frequency words only (\"The ___ is red\" → cat)\n" +
                    "  • DRAG_AND_DROP: Match words to pictures or complete simple sentences\n" +
                    "CANNOT HANDLE: Inference, opinion, multiple-step reasoning\n" +
                    "COMPLEXITY: Explicit information only, one-step tasks"),

    L3("Level 3 - Elementary Grade 3",
            "VOCABULARY: 800-1200 words (daily routines, weather, seasons, basic feelings)\n" +
                    "GRAMMAR: Past simple regular verbs, basic prepositions (in, on, at), there is/are\n" +
                    "QUESTION TYPES:\n" +
                    "  • MULTIPLE_CHOICE: Detail questions (\"What did Sarah do yesterday?\")\n" +
                    "  • TRUE_OR_FALSE: Must scan text for specific information\n" +
                    "  • FILL_IN_THE_BLANK: Common verbs/nouns with clear context\n" +
                    "  • REARRANGE: Simple sentences (4-5 words: \"I / go / to / school\")\n" +
                    "  • DRAG_AND_DROP: Sequence events (First, Next, Then, Finally)\n" +
                    "CAN START: Very simple inference (\"It's raining, so Sarah took her ___\" → umbrella)\n" +
                    "COMPLEXITY: Mostly explicit, beginning logical connections"),

    L4("Level 4 - Elementary Grade 4",
            "VOCABULARY: 1200-1800 words (hobbies, jobs, animals, habitats, basic science terms)\n" +
                    "GRAMMAR: Past simple irregular verbs, comparatives (bigger, better), going to future\n" +
                    "QUESTION TYPES:\n" +
                    "  • MULTIPLE_CHOICE: Inference questions (\"Why did Tom feel sad?\")\n" +
                    "  • TRUE_OR_FALSE: Requires understanding, not just word matching\n" +
                    "  • FILL_IN_THE_BLANK: Verbs in correct tense, adjectives\n" +
                    "  • DROPDOWN: Choose correct word form (\"She ___ to school yesterday\" → went/goes/going)\n" +
                    "  • REARRANGE: 6-7 words including time markers\n" +
                    "  • MULTIPLE_SELECT: 2 correct answers from 4-5 options\n" +
                    "COMPLEXITY: Simple inference, identifying main idea, sequencing multiple events"),

    L5("Level 5 - Elementary Grade 5",
            "VOCABULARY: 1800-2500 words (geography terms, history basics, technology, health)\n" +
                    "GRAMMAR: Present perfect (have/has + past participle), modal verbs (should, must, might)\n" +
                    "QUESTION TYPES:\n" +
                    "  • MULTIPLE_CHOICE: Cause-effect, comparing information\n" +
                    "  • TRUE_OR_FALSE: Requires synthesis of multiple sentences\n" +
                    "  • FILL_IN_THE_BLANK: Academic vocabulary, connecting words (however, because)\n" +
                    "  • DROPDOWN: Grammar-focused (tense, preposition, article choices)\n" +
                    "  • REARRANGE: Complete sentences with dependent clauses\n" +
                    "  • MULTIPLE_SELECT: Identify 2-3 supporting details\n" +
                    "  • REWRITE: Simple transformations (active → passive, statement → question)\n" +
                    "COMPLEXITY: Multi-step reasoning, identifying purpose, comparing ideas"),

    L6("Level 6 - Middle School Grade 6",
            "VOCABULARY: 2500-3500 words (academic terms: environment, culture, basic economics)\n" +
                    "GRAMMAR: Passive voice (basic), relative clauses (who, which, that), conditionals (type 1)\n" +
                    "QUESTION TYPES:\n" +
                    "  • MULTIPLE_CHOICE: Inference, author's purpose, vocabulary in context\n" +
                    "  • TRUE_OR_FALSE: Statements requiring interpretation\n" +
                    "  • FILL_IN_THE_BLANK: Academic vocabulary, idiomatic expressions\n" +
                    "  • DROPDOWN: Advanced grammar (tenses, conditionals, passive/active voice)\n" +
                    "  • REARRANGE: Complex sentences with multiple clauses\n" +
                    "  • MULTIPLE_SELECT: Identify all supporting evidence (3 correct from 6 options)\n" +
                    "  • REWRITE: Sentence combination, tense transformation, clause restructuring\n" +
                    "COMPLEXITY: Identifying implicit meaning, analyzing text structure, drawing conclusions"),

    L7("Level 7 - Middle School Grade 7",
            "VOCABULARY: 3500-4500 words (scientific terms, social issues, technology, literature basics)\n" +
                    "GRAMMAR: All conditionals (type 1, 2, 3), reported speech, complex passive forms\n" +
                    "QUESTION TYPES:\n" +
                    "  • MULTIPLE_CHOICE: Analyzing arguments, distinguishing fact vs opinion\n" +
                    "  • TRUE_OR_FALSE: Requires understanding implied meaning\n" +
                    "  • FILL_IN_THE_BLANK: Collocations, phrasal verbs, transition words\n" +
                    "  • DROPDOWN: Advanced structures (wish clauses, subjunctive, inversion)\n" +
                    "  • REARRANGE: Paragraphs or complex sentences with embedded clauses\n" +
                    "  • MULTIPLE_SELECT: Identify all accurate interpretations\n" +
                    "  • REWRITE: Paraphrasing, formal ↔ informal style changes\n" +
                    "COMPLEXITY: Evaluating arguments, recognizing bias, synthesizing multiple perspectives"),

    L8("Level 8 - Middle School Grade 8",
            "VOCABULARY: 4500-5500 words (abstract concepts: justice, democracy, technology ethics)\n" +
                    "GRAMMAR: Advanced passive (all tenses), causative verbs (have/get something done), cleft sentences\n" +
                    "QUESTION TYPES:\n" +
                    "  • MULTIPLE_CHOICE: Analyzing tone, identifying assumptions, evaluating claims\n" +
                    "  • TRUE_OR_FALSE: Nuanced statements requiring deep comprehension\n" +
                    "  • FILL_IN_THE_BLANK: Academic collocations, discourse markers, fixed expressions\n" +
                    "  • DROPDOWN: Stylistic choices, register appropriateness\n" +
                    "  • REARRANGE: Organizing argument structure (claim, evidence, conclusion)\n" +
                    "  • MULTIPLE_SELECT: Identify all valid inferences (multiple layers of meaning)\n" +
                    "  • REWRITE: Advanced transformations (emphasizing, hedging, nominalizing)\n" +
                    "COMPLEXITY: Critical analysis, understanding rhetorical devices, evaluating evidence quality"),

    L9("Level 9 - High School Grade 9",
            "VOCABULARY: 5500-6500 words (specialized academic vocabulary across subjects)\n" +
                    "GRAMMAR: Mastery of all tenses, advanced modals (could have, should have been), inversion for emphasis\n" +
                    "QUESTION TYPES:\n" +
                    "  • MULTIPLE_CHOICE: Analyzing complex arguments, identifying logical fallacies\n" +
                    "  • TRUE_OR_FALSE: Statements requiring synthesis of entire passage\n" +
                    "  • FILL_IN_THE_BLANK: Discipline-specific terms, precise vocabulary choices\n" +
                    "  • DROPDOWN: Complex grammar in academic context, style consistency\n" +
                    "  • REARRANGE: Organizing full paragraphs or multi-step arguments\n" +
                    "  • MULTIPLE_SELECT: Identify all implications of an argument\n" +
                    "  • REWRITE: Summarizing, synthesizing, maintaining coherence across transformations\n" +
                    "COMPLEXITY: Evaluating multiple perspectives, recognizing unstated assumptions, analyzing text coherence"),

    L10("Level 10 - High School Grade 10",
            "VOCABULARY: 6500-7500 words (advanced academic: philosophical terms, scientific methodology)\n" +
                    "GRAMMAR: Sophisticated sentence structures, embedded clauses, formal register\n" +
                    "QUESTION TYPES:\n" +
                    "  • MULTIPLE_CHOICE: Analyzing methodology, evaluating evidence strength\n" +
                    "  • TRUE_OR_FALSE: Complex conditional statements, counterfactuals\n" +
                    "  • FILL_IN_THE_BLANK: Technical terminology, precise academic language\n" +
                    "  • DROPDOWN: Maintaining coherence across long passages, choosing appropriate hedging\n" +
                    "  • REARRANGE: Organizing research structure (intro, methods, findings, discussion)\n" +
                    "  • MULTIPLE_SELECT: Identify all valid conclusions from data/evidence\n" +
                    "  • REWRITE: Formal academic paraphrasing, citation integration\n" +
                    "COMPLEXITY: Meta-cognitive analysis, evaluating research design, synthesizing complex data"),

    L11("Level 11 - High School Grade 11",
            "VOCABULARY: 7500-9000 words (specialized discourse: linguistics, economics, philosophy)\n" +
                    "GRAMMAR: Near-native control, sophisticated style manipulation\n" +
                    "QUESTION TYPES:\n" +
                    "  • MULTIPLE_CHOICE: Distinguishing between similar arguments, identifying subtle rhetorical moves\n" +
                    "  • TRUE_OR_FALSE: Statements requiring understanding of theoretical frameworks\n" +
                    "  • FILL_IN_THE_BLANK: Discipline-specific jargon, nuanced vocabulary distinctions\n" +
                    "  • DROPDOWN: Advanced register choices, maintaining academic voice\n" +
                    "  • REARRANGE: Complex argumentative structure with multiple supporting threads\n" +
                    "  • MULTIPLE_SELECT: Identify all implications across multiple domains\n" +
                    "  • REWRITE: Advanced synthesis, integrating multiple sources, maintaining authorial voice\n" +
                    "COMPLEXITY: Theoretical analysis, evaluating paradigms, recognizing disciplinary conventions"),

    L12("Level 12 - High School Grade 12",
            "VOCABULARY: 9000+ words (advanced academic: epistemology, quantum physics, literary theory)\n" +
                    "GRAMMAR: Native-level sophistication, stylistic variation for effect\n" +
                    "QUESTION TYPES:\n" +
                    "  • MULTIPLE_CHOICE: Evaluating competing theories, analyzing epistemological assumptions\n" +
                    "  • TRUE_OR_FALSE: Requires deep theoretical understanding and cross-textual synthesis\n" +
                    "  • FILL_IN_THE_BLANK: Highly specialized terminology, precise conceptual distinctions\n" +
                    "  • DROPDOWN: Sophisticated stylistic choices, academic convention mastery\n" +
                    "  • REARRANGE: Complex multi-paragraph arguments with counter-arguments\n" +
                    "  • MULTIPLE_SELECT: Identify all valid interpretations across frameworks\n" +
                    "  • REWRITE: Sophisticated paraphrasing, theoretical reframing, critical synthesis\n" +
                    "COMPLEXITY: Meta-theoretical analysis, evaluating foundational assumptions, advanced synthesis"),

    // ==================== CEFR LEVELS ====================

    A1("A1 - Beginner",
            "VOCABULARY: 500-800 words (survival vocabulary: greetings, numbers, basic needs)\n" +
                    "GRAMMAR: Present simple, basic questions (What/Where/Who), singular/plural\n" +
                    "QUESTION TYPES:\n" +
                    "  • MULTIPLE_CHOICE: Recognition of familiar words/phrases\n" +
                    "  • TRUE_OR_FALSE: Direct matching with text\n" +
                    "  • DRAG_AND_DROP: Picture-word matching, simple categorization\n" +
                    "CANNOT HANDLE: Inference, multi-step tasks, unfamiliar contexts\n" +
                    "COMPLEXITY: Concrete, immediate, highly supported"),

    A2("A2 - Elementary",
            "VOCABULARY: 1000-1500 words (everyday topics: shopping, family, work, local area)\n" +
                    "GRAMMAR: Past simple, basic future (going to), can/should, simple connectors (and, but, because)\n" +
                    "QUESTION TYPES:\n" +
                    "  • MULTIPLE_CHOICE: Simple inference from context\n" +
                    "  • TRUE_OR_FALSE: Requires basic comprehension\n" +
                    "  • FILL_IN_THE_BLANK: High-frequency words in clear context\n" +
                    "  • REARRANGE: Simple sentences (5-6 words)\n" +
                    "COMPLEXITY: Familiar contexts, routine information, basic personal/social topics"),

    B1("B1 - Intermediate",
            "VOCABULARY: 2500-3500 words (work, school, leisure, travel, current events)\n" +
                    "GRAMMAR: All basic tenses, conditionals (type 1), passive (simple), relative clauses\n" +
                    "QUESTION TYPES:\n" +
                    "  • MULTIPLE_CHOICE: Main idea, supporting details, moderate inference\n" +
                    "  • TRUE_OR_FALSE: Requires understanding relationships\n" +
                    "  • FILL_IN_THE_BLANK: Collocations, phrasal verbs, connectors\n" +
                    "  • DROPDOWN: Tense/form selection based on context\n" +
                    "  • REARRANGE: Complex sentences with clauses\n" +
                    "  • MULTIPLE_SELECT: 2-3 correct from 5-6 options\n" +
                    "  • REWRITE: Basic transformations\n" +
                    "COMPLEXITY: Connected discourse, personal opinions, straightforward arguments"),

    B2("B2 - Upper Intermediate",
            "VOCABULARY: 4000-5500 words (abstract topics, technical in own field, complex social issues)\n" +
                    "GRAMMAR: Advanced structures, subtle modality, complex passives, all conditionals\n" +
                    "QUESTION TYPES:\n" +
                    "  • MULTIPLE_CHOICE: Implicit meaning, attitude, complex inference\n" +
                    "  • TRUE_OR_FALSE: Nuanced understanding required\n" +
                    "  • FILL_IN_THE_BLANK: Academic vocabulary, idiomatic expressions\n" +
                    "  • DROPDOWN: Style/register appropriate choices\n" +
                    "  • REARRANGE: Paragraph organization, argument structure\n" +
                    "  • MULTIPLE_SELECT: Multiple layers of meaning\n" +
                    "  • REWRITE: Maintaining meaning across significant structural changes\n" +
                    "COMPLEXITY: Abstract argumentation, evaluating positions, recognizing viewpoint"),

    C1("C1 - Advanced",
            "VOCABULARY: 6000-8000 words (wide range of demanding topics, specialized discourse)\n" +
                    "GRAMMAR: Full range with flexibility and precision, sophisticated stylistic control\n" +
                    "QUESTION TYPES:\n" +
                    "  • MULTIPLE_CHOICE: Subtle distinctions, implied attitudes, rhetorical purpose\n" +
                    "  • TRUE_OR_FALSE: Requires synthesis and interpretation\n" +
                    "  • FILL_IN_THE_BLANK: Precise vocabulary, collocations, fixed expressions\n" +
                    "  • DROPDOWN: Maintaining sophisticated style and coherence\n" +
                    "  • REARRANGE: Complex multi-paragraph structures\n" +
                    "  • MULTIPLE_SELECT: Identifying all valid implications\n" +
                    "  • REWRITE: Sophisticated paraphrasing, synthesis, style transformation\n" +
                    "COMPLEXITY: Recognizing finer points of meaning, understanding complex structures"),

    C2("C2 - Proficiency",
            "VOCABULARY: 8000-10000+ words (virtually any topic with ease and precision)\n" +
                    "GRAMMAR: Native-like command, can appreciate stylistic differences\n" +
                    "QUESTION TYPES:\n" +
                    "  • ALL TYPES: Can handle maximum complexity in any question format\n" +
                    "  • MULTIPLE_CHOICE: Distinguishing fine shades of meaning, appreciating irony/humor\n" +
                    "  • TRUE_OR_FALSE: Requires complete mastery of text\n" +
                    "  • FILL_IN_THE_BLANK: Context-dependent subtle distinctions\n" +
                    "  • DROPDOWN: Recognizing and producing appropriate style\n" +
                    "  • REARRANGE: Any level of structural complexity\n" +
                    "  • MULTIPLE_SELECT: Meta-level understanding\n" +
                    "  • REWRITE: Can maintain exact meaning through major transformations\n" +
                    "COMPLEXITY: Near-native comprehension, appreciating implicit cultural references"),

    // ==================== UNIVERSITY LEVEL ====================

    UNIVERSITY("University Level",
            "VOCABULARY: 10000+ words (academic register, discipline-specific terminology across fields)\n" +
                    "GRAMMAR: Complete mastery, can manipulate language for precise academic purposes\n" +
                    "QUESTION TYPES:\n" +
                    "  • ALL TYPES at maximum sophistication:\n" +
                    "  • MULTIPLE_CHOICE: Analyzing research methodology, evaluating theoretical frameworks\n" +
                    "  • TRUE_OR_FALSE: Statements requiring deep disciplinary knowledge\n" +
                    "  • FILL_IN_THE_BLANK: Highly specialized terminology, discipline conventions\n" +
                    "  • DROPDOWN: Academic register maintenance, citation integration\n" +
                    "  • REARRANGE: Research paper structure, complex argumentative flow\n" +
                    "  • MULTIPLE_SELECT: Identifying all valid scholarly implications\n" +
                    "  • REWRITE: Academic synthesis, theoretical reframing, critical engagement\n" +
                    "COMPLEXITY: Meta-cognitive analysis, interdisciplinary synthesis, original critical thought");

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