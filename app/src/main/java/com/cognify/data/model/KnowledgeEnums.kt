package com.cognify.data.model

enum class KnowledgeType {
    NOTE,
    CODE,
    SQL,
    LINUX,
    INTERVIEW,
    JAPANESE,
    COLLEGE,
    DOCUMENTATION
}

enum class ProgrammingLanguage(val displayName: String) {
    NONE("None"),
    KOTLIN("Kotlin"),
    JAVA("Java"),
    PYTHON("Python"),
    JAVASCRIPT("JavaScript"),
    TYPESCRIPT("TypeScript"),
    HTML("HTML"),
    CSS("CSS"),
    SQL("SQL"),
    BASH("Bash / Shell"),
    C("C"),
    CPP("C++"),
    CSHARP("C#"),
    GO("Go"),
    RUST("Rust"),
    RUBY("Ruby"),
    SWIFT("Swift"),
    DART("Dart"),
    PHP("PHP"),
    OTHER("Other")
}

enum class Difficulty {
    NONE,
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}
