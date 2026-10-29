package com.amine.quizservice.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QuizDto {

    @NotBlank(message = "Category name is required")
    private String categoryName;

    @NotNull(message = "Number of questions is required")
    @Min(value = 1, message = "Number of questions must be at least 1")
    private Integer numQuestions;

    @NotBlank(message = "Quiz title is required")
    private String title;
}
